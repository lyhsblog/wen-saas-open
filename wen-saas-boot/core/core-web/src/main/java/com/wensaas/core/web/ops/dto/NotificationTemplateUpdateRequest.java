package com.wensaas.core.web.ops.dto;

import com.wensaas.core.api.notification.template.NotificationTemplateAdminService.ChannelContent;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminService.Priority;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminService.TemplateStatus;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminService.VarDef;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 编辑通知模板请求体。null 字段表示不改；
 * {@code defaultChannels} / {@code contents} / {@code availableVars} 非 null 时全量替换。
 */
public record NotificationTemplateUpdateRequest(
        @Schema(description = "显示名", nullable = true)
        @Size(max = 255)
        String name,

        @Schema(description = "描述", nullable = true)
        String description,

        @Schema(description = "可用变量声明；非 null 时全量替换", nullable = true)
        List<VarDef> availableVars,

        @Schema(description = "默认渠道；非 null 时全量替换（服务端确保含 STATION）", nullable = true)
        List<String> defaultChannels,

        @Schema(description = "各渠道文案；非 null 时全量替换", nullable = true)
        Map<String, ChannelContent> contents,

        @Schema(description = "通知类别（NotificationCategory 枚举名）", nullable = true)
        String notificationCategory,

        @Schema(description = "优先级", nullable = true)
        Priority priority,

        @Schema(description = "状态", nullable = true)
        TemplateStatus status
) {}
