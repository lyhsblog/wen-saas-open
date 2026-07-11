package com.wensaas.core.impl.task;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.spi.def.TaskHandlerDef;
import com.wensaas.core.api.task.SubmitRequest;
import com.wensaas.core.api.task.TaskContext;
import com.wensaas.core.api.task.TaskException;
import com.wensaas.core.api.task.TaskHandler;
import com.wensaas.core.api.task.TaskPage;
import com.wensaas.core.api.task.TaskQuery;
import com.wensaas.core.api.task.TaskStatus;
import com.wensaas.core.api.task.TaskType;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.spi.registry.TaskHandlerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.task.execution.TaskExecutionMessage;
import com.wensaas.core.impl.task.execution.TaskExecutionPublisher;
import com.wensaas.core.impl.task.progress.TaskProgressStore;

class TaskServiceImplTest {

    private SysTaskMapper mapper;
    private TaskHandlerRegistry registry;
    private TaskProgressStore store;
    private TaskExecutionPublisher publisher;
    private TaskServiceImpl svc;

    @BeforeEach
    void setUp() {
        mapper = mock(SysTaskMapper.class);
        registry = new TaskHandlerRegistry();
        store = mock(TaskProgressStore.class);
        publisher = mock(TaskExecutionPublisher.class);
        TaskProperties props = new TaskProperties();
        svc = new TaskServiceImpl(mapper, registry, store, publisher, props);
        TenantContext.set(9L);
        UserContext.set(new UserPrincipal(7L, 9L, "alice", "s", false, 11L, false, null));
        RequestContext.set("req-A");
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        UserContext.clear();
        RequestContext.clear();
    }

    @Test
    void submit_happyPath_insertsAndPublishes() {
        registry.register("__test__", new TaskHandlerDef("h.ok", StubHandler.class));
        when(mapper.countRunningByHandler(9L, "h.ok")).thenReturn(0);

        Long id = svc.submit(new SubmitRequest(TaskType.IMPORT, "x", "h.ok", Map.of("k", "v")));

        ArgumentCaptor<SysTask> cap = ArgumentCaptor.forClass(SysTask.class);
        verify(mapper).insert((SysTask) cap.capture());
        SysTask t = cap.getValue();
        assertThat(t.getTenantId()).isEqualTo(9L);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.PENDING.name());
        assertThat(t.getCreatorId()).isEqualTo(7L);
        assertThat(t.getExpiresAt()).isNotNull();
        verify(publisher).publish(any(TaskExecutionMessage.class));
        assertThat(id).isEqualTo(t.getId());
    }

    @Test
    void submit_handlerNotFound_throws() {
        assertThatThrownBy(() -> svc.submit(
                new SubmitRequest(TaskType.OTHER, null, "h.missing", null)))
                .isInstanceOf(TaskException.class)
                .matches(e -> ((TaskException) e).code() == TaskException.Code.HANDLER_NOT_FOUND);
        verify(mapper, never()).insert(any(SysTask.class));
    }

    @Test
    void submit_duplicateRunning_throws() {
        registry.register("__test__", new TaskHandlerDef("h.dup", StubHandler.class));
        when(mapper.countRunningByHandler(9L, "h.dup")).thenReturn(1);

        assertThatThrownBy(() -> svc.submit(new SubmitRequest(TaskType.BATCH, null, "h.dup", null)))
                .isInstanceOf(TaskException.class)
                .matches(e -> ((TaskException) e).code() == TaskException.Code.DUPLICATE_RUNNING_TASK);
        verify(mapper, never()).insert(any(SysTask.class));
        verify(publisher, never()).publish(any());
    }

    @Test
    void cancel_happyPath_setsCancellingAndRedisFlag() {
        SysTask t = new SysTask();
        t.setId(100L);
        t.setStatus(TaskStatus.RUNNING.name());
        when(mapper.selectById(100L)).thenReturn(t);

        svc.cancel(100L);

        ArgumentCaptor<SysTask> cap = ArgumentCaptor.forClass(SysTask.class);
        verify(mapper).updateById((SysTask) cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(TaskStatus.CANCELLING.name());
        verify(store).markCancelled(100L);
    }

    @Test
    void cancel_nonRunning_throws() {
        SysTask t = new SysTask();
        t.setId(101L);
        t.setStatus(TaskStatus.COMPLETED.name());
        when(mapper.selectById(101L)).thenReturn(t);

        assertThatThrownBy(() -> svc.cancel(101L))
                .isInstanceOf(TaskException.class)
                .matches(e -> ((TaskException) e).code() == TaskException.Code.CANCEL_NOT_ALLOWED);
        verify(store, never()).markCancelled(anyLong());
    }

    @Test
    void get_missing_throws() {
        when(mapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> svc.get(404L))
                .isInstanceOf(TaskException.class)
                .matches(e -> ((TaskException) e).code() == TaskException.Code.TASK_NOT_FOUND);
    }

    @Test
    void listByCreator_passesTenantToMapper() {
        when(mapper.selectByCreator(eq(9L), eq(7L), eq(0), eq(10))).thenReturn(List.of());
        svc.listByCreator(7L, 0, 10);
        verify(mapper).selectByCreator(9L, 7L, 0, 10);
    }

    @Test
    void queryMine_zeroTotal_shortCircuitsWithoutSelect() {
        when(mapper.countMine(9L, 7L, null, null)).thenReturn(0L);

        TaskPage page = svc.queryMine(7L, new TaskQuery(null, null, 0, 20));

        assertThat(page.total()).isZero();
        assertThat(page.records()).isEmpty();
        verify(mapper, never()).selectMine(anyLong(), anyLong(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void queryMine_appliesFiltersAndMapsRows() {
        when(mapper.countMine(9L, 7L, "RUNNING", "IMPORT")).thenReturn(2L);
        when(mapper.selectMine(9L, 7L, "RUNNING", "IMPORT", 0, 20))
                .thenReturn(List.of(taskRow(1L), taskRow(2L)));
        when(store.readProgress(anyLong())).thenReturn(Optional.empty());

        TaskPage page = svc.queryMine(7L,
                new TaskQuery(TaskStatus.RUNNING, TaskType.IMPORT, 0, 20));

        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.records()).hasSize(2)
                .allMatch(s -> s.status() == TaskStatus.RUNNING && s.type() == TaskType.IMPORT);
        verify(mapper).selectMine(9L, 7L, "RUNNING", "IMPORT", 0, 20);
    }

    private static SysTask taskRow(long id) {
        SysTask t = new SysTask();
        t.setId(id);
        t.setTenantId(9L);
        t.setTaskType(TaskType.IMPORT.name());
        t.setStatus(TaskStatus.RUNNING.name());
        t.setHandlerCode("h.ok");
        t.setProgress(40);
        t.setCreatorId(7L);
        return t;
    }

    public static class StubHandler implements TaskHandler {
        @Override public String handlerCode() { return "stub"; }
        @Override public void execute(TaskContext context) { }
    }
}
