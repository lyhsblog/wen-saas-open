package com.wensaas.core.api.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskExceptionTest {

    @Test
    void code_isPreserved() {
        TaskException e = new TaskException(TaskException.Code.HANDLER_NOT_FOUND, "missing");
        assertThat(e.code()).isEqualTo(TaskException.Code.HANDLER_NOT_FOUND);
        assertThat(e.getMessage()).isEqualTo("missing");
    }

    @Test
    void allCodes_present() {
        // 防止误删枚举值导致下游 ExceptionAdvice 漏案例
        assertThat(TaskException.Code.values()).containsExactlyInAnyOrder(
                TaskException.Code.HANDLER_NOT_FOUND,
                TaskException.Code.DUPLICATE_RUNNING_TASK,
                TaskException.Code.TASK_NOT_FOUND,
                TaskException.Code.CANCEL_NOT_ALLOWED,
                TaskException.Code.INVALID_STATE);
    }
}
