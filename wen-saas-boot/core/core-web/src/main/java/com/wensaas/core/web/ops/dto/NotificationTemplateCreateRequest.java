package com.wensaas.core.web.ops.dto;

import com.wensaas.core.api.notification.template.NotificationTemplateAdminService.ChannelContent;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminService.Priority;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminService.TemplateStatus;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminService.VarDef;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** 新建通知模板请求体（运营平台默认模板）。 */
public record NotificationTemplateCreateRequest(
        @Schema(description = "模板编码 {module}.{name}，全局唯一，创建后不可改", example = "crm.follow_reminder")
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")
        String templateCode,

        @Schema(description = "归属业务模块", nullable = true, example = "crm")
        @Size(max = 80)
        String moduleCode,

        @Schema(description = "显示名", example = "客户跟进提醒")
        @NotBlank
        @Size(max = 255)
        String name,

        @Schema(description = "描述", nullable = true)
        String description,

        @Schema(description = "可用变量声明（含示例值，供预览）", nullable = true)
        List<VarDef> availableVars,

        @Schema(description = "默认渠道（Channel 枚举名；服务端确保含 STATION 保底）", example = "[\"STATION\",\"EMAIL\"]")
        List<String> defaultChannels,

        @Schema(description = "各渠道文案 {STATION:{title,body},...}")
        Map<String, ChannelContent> contents,

        @Schema(description = "通知类别（NotificationCategory 枚举名）；空表示不参与偏好过滤", nullable = true)
        String notificationCategory,

        @Schema(description = "优先级；null 视作 NORMAL", nullable = true)
        Priority priority,

        @Schema(description = "初始状态；null 视作 ACTIVE", nullable = true)
        TemplateStatus status
) {}
