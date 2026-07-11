package com.wensaas.core.api.spi.def;

import com.wensaas.core.api.task.TaskHandler;

import java.util.Objects;

/**
 * 任务处理器扩展点定义。
 *
 * <p>业务模块通过 {@code BusinessModuleDescriptor.taskHandlers()} 声明：
 * <pre>
 *   new TaskHandlerDef("crm.task.import.customer", CrmCustomerImportHandler.class)
 * </pre>
 *
 * <p>{@code handlerClass} 必须是已注册为 Spring Bean 的 {@link TaskHandler} 实现；
 * TaskRunner 通过 ApplicationContext.getBean(handlerClass) 拿到单例。
 *
 * @param handlerCode  处理器标识，命名约定：业务模块表前缀 + ".task." + 业务标识
 * @param handlerClass 处理器实现类
 */
public record TaskHandlerDef(String handlerCode, Class<? extends TaskHandler> handlerClass) {

    public TaskHandlerDef {
        Objects.requireNonNull(handlerCode, "handlerCode");
        Objects.requireNonNull(handlerClass, "handlerClass");
        if (handlerCode.isBlank()) {
            throw new IllegalArgumentException("handlerCode 不可为空");
        }
    }
}
