package com.wensaas.core.impl.task.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.task.progress.TaskProgressStore;

class TaskContextImplTest {

    private TaskProgressStore store;
    private TaskContextImpl ctx;

    @BeforeEach
    void setUp() {
        store = mock(TaskProgressStore.class);
        ctx = new TaskContextImpl(100L, 9L, Map.of("k", "v"), store, 200L);
    }

    @Test
    void updateProgress_clampsRange_andWritesRedisOnFirstCall() {
        ctx.updateProgress(50, 10);
        ArgumentCaptor<TaskProgressStore.TaskProgress> p = ArgumentCaptor.forClass(TaskProgressStore.TaskProgress.class);
        verify(store, atLeastOnce()).writeProgress(eq(100L), p.capture());
        assertThat(p.getValue().progress()).isEqualTo(50);
        assertThat(p.getValue().processed()).isEqualTo(10);

        ctx.updateProgress(-5, 0);
        assertThat(ctx.progressValue()).isEqualTo(0);

        ctx.updateProgress(200, 999);
        assertThat(ctx.progressValue()).isEqualTo(100);
    }

    @Test
    void updateProgress_throttle_secondCallSkipsRedis() {
        ctx.updateProgress(10, 1);
        ctx.updateProgress(20, 2);   // 距离首次 < throttle，被节流
        // 仅触发一次（首次）
        verify(store, times(1)).writeProgress(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordSuccess_recordFailure_accumulate() {
        ctx.recordSuccess(3);
        ctx.recordSuccess(2);
        ctx.recordFailure(1);
        assertThat(ctx.successValue()).isEqualTo(5);
        assertThat(ctx.failedValue()).isEqualTo(1);
    }

    @Test
    void isCancelled_delegatesToStore() {
        when(store.isCancelled(100L)).thenReturn(true);
        assertThat(ctx.isCancelled()).isTrue();
    }

    @Test
    void setResult_map_returnsAsIs() {
        ctx.setResult(Map.of("a", 1));
        assertThat(ctx.resultValueAsMap()).containsEntry("a", 1);
    }

    @Test
    void setResult_pojo_jsonifiedToMap() {
        ctx.setResult(new Pojo("alice", 9));
        assertThat(ctx.resultValueAsMap()).containsEntry("name", "alice").containsEntry("age", 9);
    }

    @Test
    void tenantId_andParams_keptIntact() {
        assertThat(ctx.tenantId()).isEqualTo(9L);
        assertThat(ctx.params()).containsEntry("k", "v");
    }

    static org.mockito.ArgumentMatcher<Long> eqLong(long v) { return l -> l == v; }
    static Long eq(long v) { return org.mockito.ArgumentMatchers.eq(v); }

    record Pojo(String name, int age) {}
}
