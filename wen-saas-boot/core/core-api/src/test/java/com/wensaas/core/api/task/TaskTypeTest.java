package com.wensaas.core.api.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskTypeTest {

    @Test
    void enumNames_areStable() {
        assertThat(TaskType.IMPORT.name()).isEqualTo("IMPORT");
        assertThat(TaskType.EXPORT.name()).isEqualTo("EXPORT");
        assertThat(TaskType.BATCH.name()).isEqualTo("BATCH");
        assertThat(TaskType.OTHER.name()).isEqualTo("OTHER");
    }

    @Test
    void allValues_present() {
        assertThat(TaskType.values()).hasSize(4);
    }
}
