package com.wensaas.core.impl.notification.access;

import com.wensaas.core.api.spi.def.DataPermissionTableDef;
import com.wensaas.core.impl.spi.registry.DataPermissionTableRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 把 {@code sys_station_message} 注册到 {@link DataPermissionTableRegistry}：
 * 收件人即"创建人"（owner），列名 {@code recipient_id}；无 dept_column（站内信不按部门过滤）。
 *
 * <p>{@code sys_notification_template} / {@code sys_message_task} 不进数据权限：
 * <ul>
 *   <li>模板是租户级共享配置（与字典同决策）</li>
 *   <li>消息任务由业务方主动查询自有 taskId，无 UI 列表暴露</li>
 * </ul>
 *
 * <p>与 audit / task 同惯例：占位 moduleCode={@code "__core__"}。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.notification", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class NotificationDataPermissionRegistrar {

    private final DataPermissionTableRegistry registry;

    public NotificationDataPermissionRegistrar(DataPermissionTableRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void register() {
        registry.register("__core__",
                new DataPermissionTableDef("sys.notification.message", "站内信",
                        "sys_station_message", "recipient_id", null));
    }
}
