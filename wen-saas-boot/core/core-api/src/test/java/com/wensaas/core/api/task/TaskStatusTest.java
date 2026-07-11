package com.wensaas.core.api.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStatusTest {

    @Test
    void isTerminal_matchesExpected() {
        assertThat(TaskStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(TaskStatus.FAILED.isTerminal()).isTrue();
        assertThat(TaskStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(TaskStatus.PENDING.isTerminal()).isFalse();
        assertThat(TaskStatus.RUNNING.isTerminal()).isFalse();
        assertThat(TaskStatus.CANCELLING.isTerminal()).isFalse();
    }

    @Test
    void isRunning_matchesExpected() {
        assertThat(TaskStatus.RUNNING.isRunning()).isTrue();
        assertThat(TaskStatus.CANCELLING.isRunning()).isTrue();
        assertThat(TaskStatus.PENDING.isRunning()).isFalse();
        assertThat(TaskStatus.COMPLETED.isRunning()).isFalse();
    }

    @Test
    void enumNames_areStable() {
        // 持久化字符串契约，改动需配套迁移
        assertThat(TaskStatus.PENDING.name()).isEqualTo("PENDING");
        assertThat(TaskStatus.RUNNING.name()).isEqualTo("RUNNING");
        assertThat(TaskStatus.COMPLETED.name()).isEqualTo("COMPLETED");
        assertThat(TaskStatus.FAILED.name()).isEqualTo("FAILED");
        assertThat(TaskStatus.CANCELLED.name()).isEqualTo("CANCELLED");
        assertThat(TaskStatus.CANCELLING.name()).isEqualTo("CANCELLING");
    }
}
