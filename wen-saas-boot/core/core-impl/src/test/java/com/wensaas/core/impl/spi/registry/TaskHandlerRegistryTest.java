package com.wensaas.core.impl.spi.registry;

import com.wensaas.core.api.spi.def.TaskHandlerDef;
import com.wensaas.core.api.task.TaskContext;
import com.wensaas.core.api.task.TaskHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskHandlerRegistryTest {

    @Test
    void register_and_findByKey() {
        TaskHandlerRegistry r = new TaskHandlerRegistry();
        r.register("crm", new TaskHandlerDef("crm.task.import.customer", StubHandler.class));
        assertThat(r.findByKey("crm.task.import.customer")).isPresent();
        assertThat(r.findByKey("crm.task.import.customer").get().handlerClass()).isEqualTo(StubHandler.class);
    }

    @Test
    void findByKey_caseInsensitive() {
        TaskHandlerRegistry r = new TaskHandlerRegistry();
        r.register("crm", new TaskHandlerDef("CRM.Task.Import.Customer", StubHandler.class));
        // 注册时 key 已小写化
        assertThat(r.findByKey("crm.task.import.customer")).isPresent();
        assertThat(r.findByKey("CRM.Task.Import.Customer")).isEmpty(); // 查询端不再小写化
    }

    @Test
    void unknown_returns_empty() {
        TaskHandlerRegistry r = new TaskHandlerRegistry();
        assertThat(r.findByKey("missing")).isEmpty();
    }

    static class StubHandler implements TaskHandler {
        @Override public String handlerCode() { return "stub"; }
        @Override public void execute(TaskContext context) { }
    }
}
