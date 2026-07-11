package com.wensaas.core.impl.job;

import com.wensaas.core.impl.task.SysTask;
import com.wensaas.core.impl.task.SysTaskMapper;
import com.wensaas.core.impl.task.execution.TaskExecutionMessage;
import com.wensaas.core.impl.task.execution.TaskExecutionPublisher;
import com.wensaas.core.impl.task.TaskProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PendingTaskReaperJob} 单测：验证逐条重投、keyset 游标推进、迭代上限、空结果不发。
 */
class PendingTaskReaperJobTest {

    private static SysTask pending(long id, long tenantId) {
        SysTask t = new SysTask();
        t.setId(id);
        t.setTenantId(tenantId);
        t.setStatus("PENDING");
        t.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        return t;
    }

    private static TaskProperties props(int batchSize, int maxIterations) {
        TaskProperties p = new TaskProperties();
        p.setPendingReapBatchSize(batchSize);
        p.setPendingReapMaxIterations(maxIterations);
        return p;
    }

    @Test
    void execute_partialBatch_republishesEachOnceAndStops() {
        SysTaskMapper mapper = mock(SysTaskMapper.class);
        TaskExecutionPublisher publisher = mock(TaskExecutionPublisher.class);
        when(mapper.selectPendingForRepublish(any(), nullable(Long.class), eq(2)))
                .thenReturn(List.of(pending(10L, 100L)));

        new PendingTaskReaperJob(mapper, publisher, props(2, 5)).execute();

        verify(mapper, times(1)).selectPendingForRepublish(any(), nullable(Long.class), eq(2));
        ArgumentCaptor<TaskExecutionMessage> cap = ArgumentCaptor.forClass(TaskExecutionMessage.class);
        verify(publisher, times(1)).publish(cap.capture());
        assertThat(cap.getValue().taskId()).isEqualTo(10L);
        assertThat(cap.getValue().tenantId()).isEqualTo(100L);
        assertThat(cap.getValue().requestId()).isNull();
    }

    @Test
    void execute_fullBatch_advancesKeysetCursorUntilDrained() {
        SysTaskMapper mapper = mock(SysTaskMapper.class);
        TaskExecutionPublisher publisher = mock(TaskExecutionPublisher.class);
        when(mapper.selectPendingForRepublish(any(), nullable(Long.class), eq(2)))
                .thenReturn(List.of(pending(10L, 1L), pending(20L, 1L)))  // full → 继续
                .thenReturn(List.of(pending(30L, 2L)));                   // partial → 收尾

        new PendingTaskReaperJob(mapper, publisher, props(2, 5)).execute();

        ArgumentCaptor<Long> afterId = ArgumentCaptor.forClass(Long.class);
        verify(mapper, times(2)).selectPendingForRepublish(any(), afterId.capture(), eq(2));
        // 首页 afterId=null，第二页推进到上一批末尾 id=20
        assertThat(afterId.getAllValues()).containsExactly(null, 20L);
        verify(publisher, times(3)).publish(any());
    }

    @Test
    void execute_respectsMaxIterations() {
        SysTaskMapper mapper = mock(SysTaskMapper.class);
        TaskExecutionPublisher publisher = mock(TaskExecutionPublisher.class);
        when(mapper.selectPendingForRepublish(any(), nullable(Long.class), eq(2)))
                .thenReturn(List.of(pending(10L, 1L), pending(20L, 1L)))
                .thenReturn(List.of(pending(30L, 1L), pending(40L, 1L)))
                .thenReturn(List.of(pending(50L, 1L), pending(60L, 1L)));  // 不应被取到（上限 2 轮）

        new PendingTaskReaperJob(mapper, publisher, props(2, 2)).execute();

        verify(mapper, times(2)).selectPendingForRepublish(any(), nullable(Long.class), eq(2));
        verify(publisher, times(4)).publish(any());
    }

    @Test
    void execute_emptyResult_noRepublish() {
        SysTaskMapper mapper = mock(SysTaskMapper.class);
        TaskExecutionPublisher publisher = mock(TaskExecutionPublisher.class);
        when(mapper.selectPendingForRepublish(any(), nullable(Long.class), eq(2)))
                .thenReturn(List.of());

        new PendingTaskReaperJob(mapper, publisher, props(2, 5)).execute();

        verify(mapper, times(1)).selectPendingForRepublish(any(), nullable(Long.class), eq(2));
        verify(publisher, never()).publish(any());
    }
}
