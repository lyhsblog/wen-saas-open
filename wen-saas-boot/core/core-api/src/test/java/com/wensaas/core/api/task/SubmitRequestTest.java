package com.wensaas.core.api.task;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmitRequestTest {

    @Test
    void fullConstructor_keepsAllFields() {
        SubmitRequest req = new SubmitRequest(
                TaskType.IMPORT, "导入成员-X", "sys.task.import.member", Map.of("k", "v"));
        assertThat(req.type()).isEqualTo(TaskType.IMPORT);
        assertThat(req.name()).isEqualTo("导入成员-X");
        assertThat(req.handlerCode()).isEqualTo("sys.task.import.member");
        assertThat(req.params()).containsEntry("k", "v");
    }

    @Test
    void of_minimal_setsDefaults() {
        SubmitRequest req = SubmitRequest.of(TaskType.EXPORT, "sys.task.export.x");
        assertThat(req.name()).isNull();
        assertThat(req.params()).isEmpty();
    }

    @Test
    void blank_handlerCode_throws() {
        assertThatThrownBy(() -> new SubmitRequest(TaskType.OTHER, null, "", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_type_throws() {
        assertThatThrownBy(() -> new SubmitRequest(null, null, "h", null))
                .isInstanceOf(NullPointerException.class);
    }
}
