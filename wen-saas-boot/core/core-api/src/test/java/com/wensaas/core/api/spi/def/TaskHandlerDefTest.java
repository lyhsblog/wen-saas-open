package com.wensaas.core.api.spi.def;

import com.wensaas.core.api.task.TaskContext;
import com.wensaas.core.api.task.TaskHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskHandlerDefTest {

    @Test
    void valid_construction() {
        TaskHandlerDef def = new TaskHandlerDef("crm.task.import.customer", StubHandler.class);
        assertThat(def.handlerCode()).isEqualTo("crm.task.import.customer");
        assertThat(def.handlerClass()).isEqualTo(StubHandler.class);
    }

    @Test
    void blank_handlerCode_throws() {
        assertThatThrownBy(() -> new TaskHandlerDef("", StubHandler.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_handlerClass_throws() {
        assertThatThrownBy(() -> new TaskHandlerDef("h", null))
                .isInstanceOf(NullPointerException.class);
    }

    static class StubHandler implements TaskHandler {
        @Override
        public String handlerCode() { return "stub"; }

        @Override
        public void execute(TaskContext context) { }
    }
}
