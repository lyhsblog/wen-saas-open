package com.wensaas.core.impl.task.execution;

import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.spi.def.TaskHandlerDef;
import com.wensaas.core.api.task.TaskContext;
import com.wensaas.core.api.task.TaskException;
import com.wensaas.core.api.task.TaskHandler;
import com.wensaas.core.api.task.TaskStatus;
import com.wensaas.core.impl.spi.registry.TaskHandlerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.task.SysTask;
import com.wensaas.core.impl.task.SysTaskMapper;
import com.wensaas.core.impl.task.TaskProperties;
import com.wensaas.core.impl.task.progress.TaskProgressStore;

class TaskRunnerTest {

    private SysTaskMapper mapper;
    private TaskHandlerRegistry registry;
    private TaskProgressStore store;
    private ApplicationContext appCtx;
    private TaskProperties props;
    private MessageService messageService;
    private TaskRunner runner;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(SysTaskMapper.class);
        registry = new TaskHandlerRegistry();
        store = mock(TaskProgressStore.class);
        appCtx = mock(ApplicationContext.class);
        props = new TaskProperties();
        props.setProgressKeyTtl(Duration.ofMinutes(5));
        messageService = mock(MessageService.class);
        ObjectProvider<MessageService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(messageService);
        // 默认抢占成功（CAS PENDING→RUNNING 受影响行数=1）；需要模拟"被他人抢占"的用例单独覆写。
        when(mapper.update(any(), any())).thenReturn(1);
        runner = new TaskRunner(mapper, registry, store, appCtx, props, provider);
    }

    @Test
    void run_pending_to_completed() {
        SysTask t = makeTask(1L, "h.complete", TaskStatus.PENDING);
        when(mapper.selectById(1L)).thenReturn(t);
        registry.register("__test__", new TaskHandlerDef("h.complete", CompletingHandler.class));
        when(appCtx.getBean(CompletingHandler.class)).thenReturn(new CompletingHandler());

        runner.run(1L);

        // 至少 2 次 updateById：RUNNING + COMPLETED
        ArgumentCaptor<SysTask> cap = ArgumentCaptor.forClass(SysTask.class);
        verify(mapper, atLeastOnce()).updateById((SysTask) cap.capture());
        SysTask last = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(last.getStatus()).isEqualTo(TaskStatus.COMPLETED.name());
        assertThat(last.getProgress()).isEqualTo(100);
        assertThat(last.getFinishedAt()).isNotNull();
        verify(store).clearCancelled(1L);
    }

    @Test
    void run_handlerThrows_marksFailed() {
        SysTask t = makeTask(2L, "h.fail", TaskStatus.PENDING);
        when(mapper.selectById(2L)).thenReturn(t);
        registry.register("__test__", new TaskHandlerDef("h.fail", FailingHandler.class));
        when(appCtx.getBean(FailingHandler.class)).thenReturn(new FailingHandler());

        runner.run(2L);

        ArgumentCaptor<SysTask> cap = ArgumentCaptor.forClass(SysTask.class);
        verify(mapper, atLeastOnce()).updateById((SysTask) cap.capture());
        SysTask last = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(last.getStatus()).isEqualTo(TaskStatus.FAILED.name());
        assertThat(last.getErrorMessage()).contains("boom");
    }

    @Test
    void run_handlerExitsAfterCancelled_marksCancelled() {
        SysTask t = makeTask(3L, "h.complete", TaskStatus.PENDING);
        when(mapper.selectById(3L)).thenReturn(t);
        when(store.isCancelled(3L)).thenReturn(true);
        registry.register("__test__", new TaskHandlerDef("h.complete", CompletingHandler.class));
        when(appCtx.getBean(CompletingHandler.class)).thenReturn(new CompletingHandler());

        runner.run(3L);

        ArgumentCaptor<SysTask> cap = ArgumentCaptor.forClass(SysTask.class);
        verify(mapper, atLeastOnce()).updateById((SysTask) cap.capture());
        SysTask last = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(last.getStatus()).isEqualTo(TaskStatus.CANCELLED.name());
    }

    @Test
    void run_handlerNotFound_throws_andMarksFailed() {
        SysTask t = makeTask(4L, "h.unknown", TaskStatus.PENDING);
        when(mapper.selectById(4L)).thenReturn(t);

        assertThatThrownBy(() -> runner.run(4L))
                .isInstanceOf(TaskException.class)
                .matches(e -> ((TaskException) e).code() == TaskException.Code.HANDLER_NOT_FOUND);

        ArgumentCaptor<SysTask> cap = ArgumentCaptor.forClass(SysTask.class);
        verify(mapper, atLeastOnce()).updateById((SysTask) cap.capture());
        SysTask last = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(last.getStatus()).isEqualTo(TaskStatus.FAILED.name());
    }

    @Test
    void run_nonPendingStatus_skipsExecution() {
        SysTask t = makeTask(5L, "h.complete", TaskStatus.RUNNING);
        when(mapper.selectById(5L)).thenReturn(t);

        runner.run(5L);

        verify(mapper, never()).updateById(org.mockito.ArgumentMatchers.any(SysTask.class));
        verify(appCtx, never()).getBean(eq(CompletingHandler.class));
    }

    @Test
    void run_missingTask_logsAndReturns() {
        when(mapper.selectById(6L)).thenReturn(null);
        runner.run(6L);
        verify(mapper, never()).updateById(org.mockito.ArgumentMatchers.any(SysTask.class));
    }

    /** H1：消息被重复投递 / 并发双拉时，抢占 CAS 受影响行数为 0 的那条不得执行 handler。 */
    @Test
    void run_claimLostToAnotherConsumer_skipsExecution() {
        SysTask t = makeTask(11L, "h.complete", TaskStatus.PENDING);
        when(mapper.selectById(11L)).thenReturn(t);
        // 模拟另一消费者已抢先把 PENDING→RUNNING：本次 CAS 影响行数为 0
        when(mapper.update(any(), any())).thenReturn(0);
        registry.register("__test__", new TaskHandlerDef("h.complete", CompletingHandler.class));

        runner.run(11L);

        // 不解析/执行 handler、不落终态、不发通知
        verify(appCtx, never()).getBean(eq(CompletingHandler.class));
        verify(mapper, never()).updateById(org.mockito.ArgumentMatchers.any(SysTask.class));
        verify(messageService, never()).send(any());
    }

    @Test
    void run_completed_notifiesCreator() {
        SysTask t = makeTask(7L, "h.complete", TaskStatus.PENDING);
        t.setCreatorId(99L);
        t.setTaskName("批量导入客户");
        when(mapper.selectById(7L)).thenReturn(t);
        registry.register("__test__", new TaskHandlerDef("h.complete", CompletingHandler.class));
        when(appCtx.getBean(CompletingHandler.class)).thenReturn(new CompletingHandler());

        runner.run(7L);

        ArgumentCaptor<MessageSendRequest> cap = ArgumentCaptor.forClass(MessageSendRequest.class);
        verify(messageService).send((MessageSendRequest) cap.capture());
        assertThat(cap.getValue().templateCode()).isEqualTo("sys.task.completed");
        assertThat(cap.getValue().variables()).containsEntry("task_name", "批量导入客户");
    }

    @Test
    void run_failed_notifiesCreator() {
        SysTask t = makeTask(8L, "h.fail", TaskStatus.PENDING);
        t.setCreatorId(99L);
        when(mapper.selectById(8L)).thenReturn(t);
        registry.register("__test__", new TaskHandlerDef("h.fail", FailingHandler.class));
        when(appCtx.getBean(FailingHandler.class)).thenReturn(new FailingHandler());

        runner.run(8L);

        ArgumentCaptor<MessageSendRequest> cap = ArgumentCaptor.forClass(MessageSendRequest.class);
        verify(messageService).send((MessageSendRequest) cap.capture());
        assertThat(cap.getValue().templateCode()).isEqualTo("sys.task.failed");
    }

    @Test
    void run_completed_noCreator_doesNotNotify() {
        SysTask t = makeTask(9L, "h.complete", TaskStatus.PENDING); // creatorId 为 null
        when(mapper.selectById(9L)).thenReturn(t);
        registry.register("__test__", new TaskHandlerDef("h.complete", CompletingHandler.class));
        when(appCtx.getBean(CompletingHandler.class)).thenReturn(new CompletingHandler());

        runner.run(9L);

        verify(messageService, never()).send(any());
    }

    @Test
    void run_suppressingHandler_doesNotNotify() {
        SysTask t = makeTask(10L, "h.suppress", TaskStatus.PENDING);
        t.setCreatorId(99L);
        when(mapper.selectById(10L)).thenReturn(t);
        registry.register("__test__", new TaskHandlerDef("h.suppress", SuppressingHandler.class));
        when(appCtx.getBean(SuppressingHandler.class)).thenReturn(new SuppressingHandler());

        runner.run(10L);

        verify(messageService, never()).send(any());
    }

    private static SysTask makeTask(long id, String handler, TaskStatus status) {
        SysTask t = new SysTask();
        t.setId(id);
        t.setTenantId(0L);
        t.setHandlerCode(handler);
        t.setStatus(status.name());
        t.setTaskType("OTHER");
        t.setTaskParams(java.util.Map.of());
        return t;
    }

    public static class CompletingHandler implements TaskHandler {
        @Override public String handlerCode() { return "h.complete"; }
        @Override
        public void execute(TaskContext ctx) {
            ctx.updateProgress(100, 1);
            ctx.recordSuccess(1);
            ctx.setResult(java.util.Map.of("ok", true));
        }
    }

    public static class FailingHandler implements TaskHandler {
        @Override public String handlerCode() { return "h.fail"; }
        @Override
        public void execute(TaskContext ctx) {
            throw new RuntimeException("boom");
        }
    }

    public static class SuppressingHandler implements TaskHandler {
        @Override public String handlerCode() { return "h.suppress"; }
        @Override public void execute(TaskContext ctx) { ctx.recordSuccess(1); }
        @Override public boolean suppressTerminalNotification() { return true; }
    }
}
