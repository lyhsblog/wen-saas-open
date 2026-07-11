package com.wensaas.core.impl.spi.registry;

import com.wensaas.core.api.spi.def.TaskHandlerDef;
import org.springframework.stereotype.Component;
import com.wensaas.core.impl.spi.module.BusinessModuleRegistrar;

/**
 * 任务处理器扩展点注册表。key = handlerCode（小写化）。
 *
 * <p>由 {@link BusinessModuleRegistrar} 在启动期注册；运行时由 {@code TaskRunner} 查询。
 */
@Component
public class TaskHandlerRegistry extends AbstractRegistry<TaskHandlerDef> {

    public TaskHandlerRegistry() {
        super(def -> def.handlerCode().toLowerCase());
    }
}
