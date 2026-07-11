package com.wensaas.core.impl.task.execution;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.spi.def.TaskHandlerDef;
import com.wensaas.core.api.task.TaskException;
import com.wensaas.core.api.task.TaskHandler;
import com.wensaas.core.api.task.TaskStatus;
import com.wensaas.core.api.tenant.TenantContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.impl.spi.registry.TaskHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.wensaas.core.impl.task.SysTask;
import com.wensaas.core.impl.task.SysTaskMapper;
import com.wensaas.core.impl.task.TaskProperties;
import com.wensaas.core.impl.task.progress.TaskProgressStore;

/**
 * 任务执行器：从 sys_task 加载 → 状态机推进 → 调 handler → 落终态。
 *
 * <p>调用入口：
 * <ul>
 *   <li>{@link TaskExecutionConsumer} 走 RocketMQ 消费链路</li>
 *   <li>集成测试直接调 {@link #run(long)} 绕过 MQ</li>
 * </ul>
 */
public class TaskRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);
    private static final int MAX_ERROR_MESSAGE_LEN = 4000;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SysTaskMapper mapper;
    private final TaskHandlerRegistry registry;
    private final TaskProgressStore progressStore;
    private final ApplicationContext applicationContext;
    private final TaskProperties props;
    private final ObjectProvider<MessageService> messageServiceProvider;

    public TaskRunner(SysTaskMapper mapper,
                      TaskHandlerRegistry registry,
                      TaskProgressStore progressStore,
                      ApplicationContext applicationContext,
                      TaskProperties props,
                      ObjectProvider<MessageService> messageServiceProvider) {
        this.mapper = mapper;
        this.registry = registry;
        this.progressStore = progressStore;
        this.applicationContext = applicationContext;
        this.props = props;
        this.messageServiceProvider = messageServiceProvider;
    }

    public void run(long taskId) {
        SysTask task = mapper.selectById(taskId);
        if (task == null) {
            log.warn("task.run.missing taskId={}", taskId);
            return;
        }
        if (!TaskStatus.PENDING.name().equals(task.getStatus())) {
            log.warn("task.run.skip-non-pending taskId={} status={}", taskId, task.getStatus());
            return;
        }

        // 原子抢占 PENDING→RUNNING：RocketMQ at-least-once + CONCURRENTLY 下，broker 重投 / 多消费者
        // 并发拉取同一消息会让两条线程都通过上面的 PENDING 判断；用条件 UPDATE 的受影响行数当 CAS，
        // 只有抢到的那条（affected==1）继续执行，其余安静退出，避免同一任务被执行多次（详见架构审查 H1）。
        LocalDateTime startedAt = LocalDateTime.now();
        // 用字符串列而非 lambda 方法引用：lambda 方式会即时查 MyBatis entity 缓存，纯单元测试（无 MyBatis 初始化）
        // 会抛 "can not find lambda cache"。字符串列无此依赖，TenantLine 拦截器仍会注入 tenant_id。
        int claimed = mapper.update(null, Wrappers.<SysTask>update()
                .set("status", TaskStatus.RUNNING.name())
                .set("started_at", startedAt)
                .eq("id", taskId)
                .eq("status", TaskStatus.PENDING.name()));
        if (claimed != 1) {
            log.warn("task.run.claim-lost taskId={}（已被其他消费者抢占或状态已变，跳过）", taskId);
            return;
        }
        task.setStatus(TaskStatus.RUNNING.name());
        task.setStartedAt(startedAt);

        // handler 解析放到抢占之后：未注册时由抢到的这条把任务落 FAILED（RUNNING→FAILED），
        // MQ 重投时 selectById 已非 PENDING → 跳过，不会无限重试。
        Optional<TaskHandlerDef> defOpt = registry.findByKey(task.getHandlerCode().toLowerCase());
        if (defOpt.isEmpty()) {
            markFailed(task, "未注册的 handlerCode: " + task.getHandlerCode());
            throw new TaskException(TaskException.Code.HANDLER_NOT_FOUND,
                    "handlerCode 未注册: " + task.getHandlerCode());
        }

        TaskHandler handler = applicationContext.getBean(defOpt.get().handlerClass());
        TaskContextImpl ctx = new TaskContextImpl(task.getId(), task.getTenantId(),
                task.getTaskParams(), progressStore, props.getProgressUpdateThrottleMs());

        try {
            handler.execute(ctx);
            if (progressStore.isCancelled(task.getId())) {
                markCancelled(task, ctx);
            } else {
                markCompleted(task, ctx);
                notifyTerminal(task, handler, true);
            }
        } catch (RuntimeException e) {
            log.error("task.run.handler.fail taskId={} reason={}", taskId, e.getMessage(), e);
            markFailedWithContext(task, ctx, truncate(e.getMessage()));
            notifyTerminal(task, handler, false);
        } finally {
            // 终态进度落 Redis（含计数器汇总）；保留 ttl 让客户端读到最终态
            progressStore.writeTerminalProgress(task.getId(), ctx.snapshot());
            progressStore.clearCancelled(task.getId());
        }
    }

    private void markCompleted(SysTask task, TaskContextImpl ctx) {
        task.setStatus(TaskStatus.COMPLETED.name());
        task.setProgress(100);
        task.setFinishedAt(LocalDateTime.now());
        task.setTotalCount(ctx.totalValue());
        task.setProcessedCount(ctx.processedValue());
        task.setSuccessCount(ctx.successValue());
        task.setFailedCount(ctx.failedValue());
        task.setResultData(ctx.resultValueAsMap());
        mapper.updateById(task);
    }

    private void markCancelled(SysTask task, TaskContextImpl ctx) {
        task.setStatus(TaskStatus.CANCELLED.name());
        task.setProgress(ctx.progressValue());
        task.setFinishedAt(LocalDateTime.now());
        task.setProcessedCount(ctx.processedValue());
        task.setSuccessCount(ctx.successValue());
        task.setFailedCount(ctx.failedValue());
        mapper.updateById(task);
    }

    private void markFailedWithContext(SysTask task, TaskContextImpl ctx, String errorMessage) {
        task.setStatus(TaskStatus.FAILED.name());
        task.setFinishedAt(LocalDateTime.now());
        task.setErrorMessage(errorMessage);
        task.setProgress(ctx.progressValue());
        task.setProcessedCount(ctx.processedValue());
        task.setSuccessCount(ctx.successValue());
        task.setFailedCount(ctx.failedValue());
        mapper.updateById(task);
    }

    private void markFailed(SysTask task, String errorMessage) {
        task.setStatus(TaskStatus.FAILED.name());
        task.setStartedAt(LocalDateTime.now());
        task.setFinishedAt(LocalDateTime.now());
        task.setErrorMessage(errorMessage);
        mapper.updateById(task);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > MAX_ERROR_MESSAGE_LEN ? s.substring(0, MAX_ERROR_MESSAGE_LEN) : s;
    }

    /**
     * 终态给任务发起人投通用站内信（{@code sys.task.completed} / {@code sys.task.failed}）。
     *
     * <p>多重兜底，任何缺失都安静跳过、绝不影响已落库的任务终态：
     * handler 自管通知（{@link TaskHandler#suppressTerminalNotification()}）/ 无发起人 /
     * 通知模块关闭（provider 空）/ 发送异常（仅 warn）。在任务租户上下文内发送（模板按租户偏好解析）。
     */
    private void notifyTerminal(SysTask task, TaskHandler handler, boolean success) {
        if (handler.suppressTerminalNotification()) {
            return;
        }
        Long creatorId = task.getCreatorId();
        if (creatorId == null) {
            return;
        }
        MessageService messageService = messageServiceProvider.getIfAvailable();
        if (messageService == null) {
            return;
        }
        try {
            TenantContext.callAs(task.getTenantId(), () -> {
                String taskName = task.getTaskName() != null && !task.getTaskName().isBlank()
                        ? task.getTaskName() : task.getHandlerCode();
                MessageSendRequest request;
                if (success) {
                    request = MessageSendRequest.builder("sys.task.completed")
                            .variables(Map.of(
                                    "task_name", taskName,
                                    "finish_time", task.getFinishedAt() == null ? "" : task.getFinishedAt().format(TIME_FMT)))
                            .recipients(List.of(RecipientSpec.member(creatorId)))
                            .channels(List.of(Channel.STATION))
                            .category(MessageCategory.TODO)
                            .build();
                } else {
                    String reason = task.getErrorMessage() != null && !task.getErrorMessage().isBlank()
                            ? task.getErrorMessage() : "未知原因";
                    request = MessageSendRequest.builder("sys.task.failed")
                            .variables(Map.of("task_name", taskName, "error_reason", reason))
                            .recipients(List.of(RecipientSpec.member(creatorId)))
                            .channels(List.of(Channel.STATION))
                            .category(MessageCategory.TODO)
                            .priority(Priority.IMPORTANT)
                            .build();
                }
                messageService.send(request);
                return null;
            });
        } catch (Exception e) {
            log.warn("task.run.notify.fail taskId={} success={} reason={}", task.getId(), success, e.getMessage());
        }
    }
}
