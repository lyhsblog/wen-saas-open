package com.wensaas.core.impl.task.integration;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.spi.def.TaskHandlerDef;
import com.wensaas.core.api.task.SubmitRequest;
import com.wensaas.core.api.task.TaskContext;
import com.wensaas.core.api.task.TaskException;
import com.wensaas.core.api.task.TaskHandler;
import com.wensaas.core.api.task.TaskService;
import com.wensaas.core.api.task.TaskSnapshot;
import com.wensaas.core.api.task.TaskStatus;
import com.wensaas.core.api.task.TaskType;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.spi.registry.TaskHandlerRegistry;
import com.wensaas.core.impl.task.SysTaskMapper;
import com.wensaas.core.impl.task.execution.TaskExecutionPublisher;
import com.wensaas.core.impl.task.progress.TaskProgressStore;
import com.wensaas.core.impl.task.execution.TaskRunner;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 任务中心端到端集成测试（真 PostgreSQL + 真 Redis；跳过 MQ 直接调 {@link TaskRunner#run(long)}）。
 *
 * <p>覆盖：submit → runner → DB 状态机 + Redis 进度 / 取消标志；并发控制；handler 抛错；cancel 路径。
 *
 * <p>v1 跳过 RocketMQ 真集成（dev 环境 broker.conf 遗留）；
 * Publisher 注入 mock，TaskRunner 由测试直调，与生产消费者链路一致。
 */
@SpringBootTest(classes = {TestApp.class, TaskCenterIntegrationTest.TestHandlerConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "wen-saas-boot.task.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskCenterIntegrationTest {

    private static final long TENANT_A = 9701L;
    private static final long TENANT_B = 9702L;
    private static final long CREATOR_ID = 7L;
    private static final String HANDLER_OK = "test.task.ok";
    private static final String HANDLER_FAIL = "test.task.fail";
    private static final String HANDLER_CANCELLABLE = "test.task.cancellable";

    @Autowired JdbcTemplate jdbc;
    @Autowired TaskService taskService;
    @Autowired TaskRunner taskRunner;
    @Autowired TaskHandlerRegistry handlerRegistry;
    @Autowired TaskProgressStore progressStore;
    @Autowired SysTaskMapper mapper;

    @BeforeAll
    void seed() {
        // 表结构由单一基线迁移 V1.0.0 保证（测试连已迁移的 wen_saas 库，flyway.enabled=false）。
        handlerRegistry.register("__test__", new TaskHandlerDef(HANDLER_OK, OkHandler.class));
        handlerRegistry.register("__test__", new TaskHandlerDef(HANDLER_FAIL, FailHandler.class));
        handlerRegistry.register("__test__", new TaskHandlerDef(HANDLER_CANCELLABLE, CancellableHandler.class));
        cleanup();
    }

    @AfterAll
    void teardown() {
        cleanup();
        TenantContext.clear();
        UserContext.clear();
        RequestContext.clear();
    }

    @BeforeEach
    void setupContext() {
        TenantContext.set(TENANT_A);
        UserContext.set(new UserPrincipal(CREATOR_ID, TENANT_A, "alice", "s", false, 11L, false, null));
        RequestContext.set("req-test");
        cleanup();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_task WHERE creator_id = ?", CREATOR_ID);
    }

    @Test
    void submit_thenRun_completesTask() {
        Long id = taskService.submit(SubmitRequest.of(TaskType.OTHER, HANDLER_OK));
        assertThat(id).isNotNull();

        taskRunner.run(id);

        TaskSnapshot snap = taskService.get(id);
        assertThat(snap.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(snap.progress()).isEqualTo(100);
        assertThat(snap.finishedAt()).isNotNull();
    }

    @Test
    void submit_duplicateRunning_throws() {
        // 直接造一条 RUNNING 状态
        Long firstId = taskService.submit(SubmitRequest.of(TaskType.OTHER, HANDLER_OK));
        jdbc.update("UPDATE sys_task SET status = 'RUNNING' WHERE id = ?", firstId);

        assertThatThrownBy(() -> taskService.submit(SubmitRequest.of(TaskType.OTHER, HANDLER_OK)))
                .isInstanceOf(TaskException.class)
                .matches(e -> ((TaskException) e).code() == TaskException.Code.DUPLICATE_RUNNING_TASK);
    }

    @Test
    void cancel_running_marksCancelling_thenRunnerFinishesCancelled() {
        Long id = taskService.submit(SubmitRequest.of(TaskType.BATCH, HANDLER_CANCELLABLE));
        // 先标记取消（CANCELLABLE handler 在循环里检查 isCancelled）
        // taskService.cancel 要求当前 status=RUNNING，先把 PENDING 改 RUNNING 模拟"已开始执行"
        jdbc.update("UPDATE sys_task SET status = 'RUNNING' WHERE id = ?", id);
        taskService.cancel(id);

        // 直接复位到 PENDING + Redis cancel 标志仍在 → runner 看到 cancel 并退出
        jdbc.update("UPDATE sys_task SET status = 'PENDING' WHERE id = ?", id);
        taskRunner.run(id);

        TaskSnapshot snap = taskService.get(id);
        assertThat(snap.status()).isEqualTo(TaskStatus.CANCELLED);
        // cancel 标志在 runner finally 内被清
        assertThat(progressStore.isCancelled(id)).isFalse();
    }

    @Test
    void handlerThrows_marksFailed() {
        Long id = taskService.submit(SubmitRequest.of(TaskType.OTHER, HANDLER_FAIL));
        taskRunner.run(id);

        TaskSnapshot snap = taskService.get(id);
        assertThat(snap.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(snap.errorMessage()).contains("intentional fail");
        assertThat(snap.finishedAt()).isNotNull();
    }

    @Test
    void crossTenant_isolation() {
        Long idA = taskService.submit(SubmitRequest.of(TaskType.OTHER, HANDLER_OK));

        TenantContext.set(TENANT_B);
        UserContext.set(new UserPrincipal(CREATOR_ID, TENANT_B, "bob", "s", false, null, false, null));
        assertThatThrownBy(() -> taskService.get(idA))
                .isInstanceOf(TaskException.class)
                .matches(e -> ((TaskException) e).code() == TaskException.Code.TASK_NOT_FOUND);
    }

    @Test
    void listByCreator_returnsOnlyCurrentTenant() {
        taskService.submit(SubmitRequest.of(TaskType.OTHER, HANDLER_OK));
        taskService.submit(new SubmitRequest(TaskType.BATCH, "second", HANDLER_OK,
                Map.of()));
        List<TaskSnapshot> rows = taskService.listByCreator(CREATOR_ID, 0, 10);
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(r -> r.tenantId() == TENANT_A);
    }

    @Test
    void runner_nonPendingStatus_skips() {
        Long id = taskService.submit(SubmitRequest.of(TaskType.OTHER, HANDLER_OK));
        jdbc.update("UPDATE sys_task SET status = 'COMPLETED' WHERE id = ?", id);

        taskRunner.run(id);

        TaskSnapshot snap = taskService.get(id);
        assertThat(snap.status()).isEqualTo(TaskStatus.COMPLETED);
        // started_at / finished_at 未被 runner 改写
        assertThat(snap.startedAt()).isNull();
    }

    /** Handler 实现 + Publisher mock 装配。 */
    @Configuration
    static class TestHandlerConfig {
        @Bean OkHandler okHandler() { return new OkHandler(); }
        @Bean FailHandler failHandler() { return new FailHandler(); }
        @Bean CancellableHandler cancellableHandler() { return new CancellableHandler(); }

        /** 替换 Publisher，避免真投递 MQ（broker.conf 遗留）。@Primary 让 TaskService 选用本实现。 */
        @Bean @Primary TaskExecutionPublisher noopPublisher() {
            return msg -> { /* swallow */ };
        }
    }

    public static class OkHandler implements TaskHandler {
        @Override public String handlerCode() { return HANDLER_OK; }
        @Override
        public void execute(TaskContext ctx) {
            ctx.updateProgress(100, 1);
            ctx.recordSuccess(1);
            ctx.setResult(Map.of("done", true));
        }
    }

    public static class FailHandler implements TaskHandler {
        @Override public String handlerCode() { return HANDLER_FAIL; }
        @Override
        public void execute(TaskContext ctx) {
            throw new RuntimeException("intentional fail");
        }
    }

    public static class CancellableHandler implements TaskHandler {
        @Override public String handlerCode() { return HANDLER_CANCELLABLE; }
        @Override
        public void execute(TaskContext ctx) {
            AtomicBoolean seen = new AtomicBoolean(false);
            for (int i = 0; i < 100; i++) {
                if (ctx.isCancelled()) {
                    seen.set(true);
                    return;
                }
                ctx.recordSuccess(1);
            }
        }
    }
}
