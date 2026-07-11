package com.wensaas.core.impl.notification.template;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.impl.tenant.PgJsonbTypeHandler;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_notification_template_pref} 实体。
 *
 * <p>租户对平台默认模板（{@code sys_notification_template.tenant_id=0}）的轻量定制：
 * 仅启停 / 渠道 / 优先级，<b>不含文案</b>（文案恒用默认版本）。
 *
 * <p>{@code tenant_id} 由 MP TenantLineInnerInterceptor 自动注入 / 过滤；写入走当前租户上下文。
 */
@TableName(value = "sys_notification_template_pref", autoResultMap = true)
@Getter
@Setter
public class SysNotificationTemplatePref {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String templateCode;
    /** false = 该模板对本租户完全不发（含站内信兜底） */
    private Boolean enabled;
    /** 覆盖通道列表；null = 继承默认 */
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private List<String> channels;
    /** 覆盖优先级 NORMAL / IMPORTANT；null = 继承默认 */
    private String priority;

    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
