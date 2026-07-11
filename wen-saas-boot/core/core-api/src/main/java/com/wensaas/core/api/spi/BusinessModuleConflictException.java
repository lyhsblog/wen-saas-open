package com.wensaas.core.api.spi;

/**
 * 业务模块命名空间冲突异常。启动期校验失败时抛出，阻断 Spring 容器启动。
 *
 * <p>触发条件：模块编码 / 权限点 / 字典 / H5 路由 / 通知模板 /
 * 工作台卡片 / 菜单路径 等命名空间冲突。
 */
public class BusinessModuleConflictException extends RuntimeException {

    public BusinessModuleConflictException(String message) {
        super(message);
    }

    public BusinessModuleConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
