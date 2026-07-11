package com.wensaas.core.impl.task;

import com.wensaas.core.api.spi.def.DataPermissionTableDef;
import com.wensaas.core.impl.spi.registry.DataPermissionTableRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 把 {@code sys_task} 注册到 {@link DataPermissionTableRegistry}，让 MP 数据权限拦截器自动按
 * 角色 data_scope 注入 WHERE。
 *
 * <p>{@code sys_task} 不带 {@code creator_dept_id}，dept_column = {@code null}；
 * {@code DataPermissionHandlerImpl} 已支持 dept_column 缺失（DEPT 范围降级为 1=0 行为）。
 *
 * <p>非业务模块表，用 {@code "__core__"} 占位 moduleCode（与 audit 同惯例）。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.task", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class TaskDataPermissionRegistrar {

    private final DataPermissionTableRegistry registry;

    public TaskDataPermissionRegistrar(DataPermissionTableRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void register() {
        registry.register("__core__",
                new DataPermissionTableDef("sys.task", "任务",
                        "sys_task", "creator_id", null));
    }
}
