package com.wensaas.core.impl.notification.template;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.impl.tenant.PgJsonbTypeHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_notification_template} 实体。混合表。
 *
 * <p>{@code tenant_id=0} 为业务方默认模板；{@code tenant_id>0} 为租户定制覆盖。
 *
 * <p>{@code availableVars} / {@code defaultChannels} / {@code contents} 三个 jsonb 列由
 * {@link JacksonTypeHandler} 处理。
 */
@TableName(value = "sys_notification_template", autoResultMap = true)
@Getter
@Setter
public class SysNotificationTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String templateCode;
    private String moduleCode;
    private String name;
    private String description;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private List<Map<String, Object>> availableVars;
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private List<String> defaultChannels;
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Map<String, Map<String, Object>> contents;

    /** NotificationCategory 枚举名（可空），非空时参与用户通知偏好过滤 */
    private String notificationCategory;
    /** NORMAL / IMPORTANT */
    private String priority;
    /** active / inactive */
    private String status;

    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
