package com.wensaas.core.impl.audit.aspect;

import com.wensaas.core.api.spi.def.DataPermissionTableDef;
import com.wensaas.core.impl.spi.registry.DataPermissionTableRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 把 {@code sys_operation_log} / {@code sys_login_log} 注册到 {@link DataPermissionTableRegistry}，
 * 让 MP 数据权限拦截器自动按角色 data_scope 注入 WHERE。
 *
 * <p>两表都不属于业务模块，用 {@code "__core__"} 占位 moduleCode。
 * 列名 {@code operator_id} / {@code operator_dept_id} 由 {@code DataPermissionHandlerImpl}
 * 通过 {@code def.creatorColumn() / def.deptColumn()} 透明使用，handler 无需改动。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.audit", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AuditDataPermissionRegistrar {

    private final DataPermissionTableRegistry registry;

    public AuditDataPermissionRegistrar(DataPermissionTableRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void register() {
        registry.register("__core__",
                new DataPermissionTableDef("sys.audit.operation", "操作日志",
                        "sys_operation_log", "operator_id", "operator_dept_id"));
        registry.register("__core__",
                new DataPermissionTableDef("sys.audit.login", "登录日志",
                        "sys_login_log", "operator_id", "operator_dept_id"));
    }
}
