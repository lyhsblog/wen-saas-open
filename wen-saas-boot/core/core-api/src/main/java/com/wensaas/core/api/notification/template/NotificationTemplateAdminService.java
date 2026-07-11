package com.wensaas.core.api.notification.template;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.NotificationCategory;
import com.wensaas.core.api.notification.send.Priority;

/**
 * 运营后台 · 通知模板管理。
 *
 * <p>管理对象为 <b>平台默认模板</b>（{@code sys_notification_template.tenant_id=0}）——业务模块声明 /
 * 运营维护的默认版本。租户侧主管理员的定制覆盖（{@code tenant_id>0}）不在本接口范围。
 *
 * <p>所有读写显式按 {@code tenant_id=0} 过滤并整段绕过 MP TenantLineInnerInterceptor
 * （运营账号无环境租户上下文，参考 {@code OpsTeamMemberAdminServiceImpl}）。
 *
 * <p><b>sys.* 预置模板保护</b>：编码以 {@code sys.} 开头的底座预置模板
 * 只读——不可编辑（{@link #updateTemplate}）、不可停用（{@link #disable}）、不可删除（{@link #deleteTemplate}）。
 */
public interface NotificationTemplateAdminService {

    /** 列表（按状态筛选 + 业务模块筛选 + 关键字过滤；按 module_code + template_code 排序）。 */
    List<TemplateListItem> listTemplates(StatusFilter status, String moduleCode, String keyword);

    /**
     * 详情。
     *
     * @throws NotificationTemplateAdminException TEMPLATE_NOT_FOUND
     */
    TemplateDetail getTemplate(String templateCode);

    /**
     * 创建平台默认模板（恒定 {@code tenant_id=0}）。
     *
     * @throws NotificationTemplateAdminException CODE_CONFLICT / INVALID_CHANNEL / EMPTY_CONTENT
     */
    TemplateDetail createTemplate(CreateTemplateCommand cmd);

    /**
     * 编辑模板。null 字段表示不改；{@code defaultChannels}/{@code contents}/{@code availableVars} 非 null 时全量替换。
     *
     * @throws NotificationTemplateAdminException TEMPLATE_NOT_FOUND / SYS_TEMPLATE_PROTECTED / INVALID_CHANNEL / EMPTY_CONTENT
     */
    TemplateDetail updateTemplate(String templateCode, UpdateTemplateCommand cmd);

    /**
     * 启用（status → active）。
     *
     * @throws NotificationTemplateAdminException TEMPLATE_NOT_FOUND
     */
    void enable(String templateCode);

    /**
     * 停用（status → inactive）。停用后该模板不再发送。
     *
     * @throws NotificationTemplateAdminException TEMPLATE_NOT_FOUND / SYS_TEMPLATE_PROTECTED
     */
    void disable(String templateCode);

    /**
     * 删除（软删）。
     *
     * @throws NotificationTemplateAdminException TEMPLATE_NOT_FOUND / SYS_TEMPLATE_PROTECTED
     */
    void deleteTemplate(String templateCode);

    /** 渠道元数据（供前端渲染渠道开关 / 文案 Tab）。 */
    List<ChannelMeta> listChannels();

    // ===================== records & enums =====================

    /** 列表筛选枚举。{@code ALL} = 不筛选（默认）。 */
    enum StatusFilter { ACTIVE, INACTIVE, ALL }

    /** 模板状态（对外大写；落库为小写 active / inactive）。 */
    enum TemplateStatus { ACTIVE, INACTIVE }

    /** 优先级。落库同名（NORMAL / IMPORTANT）。 */
    enum Priority { NORMAL, IMPORTANT }

    /** 单渠道文案。{@code title} 对邮件即主题；短信 / IM 可仅有 {@code body}。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class ChannelContent {
        private String title;
        private String body;
    }

    /** 可用变量声明（业务方声明，含示例值，供预览）。 */
    record VarDef(String name, String sampleValue, boolean required) {}

    /** 渠道元数据。{@code fallback=true} 表示保底通道（站内信，不可禁用）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class ChannelMeta {
        private String channel;
        private String label;
        private boolean fallback;
    }

    /** 列表项（精简视图）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TemplateListItem {
        private String templateCode;
        private String moduleCode;
        private String name;
        private String description;
        private TemplateStatus status;
        private Priority priority;
        private String notificationCategory;
        private List<String> defaultChannels;
        private boolean preset;
        private Instant createdAt;
        private Instant updatedAt;
    }

    /** 详情。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TemplateDetail {
        private String templateCode;
        private String moduleCode;
        private String name;
        private String description;
        private List<VarDef> availableVars;
        private List<String> defaultChannels;
        private Map<String, ChannelContent> contents;
        private String notificationCategory;
        private Priority priority;
        private TemplateStatus status;
        private boolean preset;
        private Instant createdAt;
        private Instant updatedAt;
    }

    /**
     * 创建命令。
     *
     * @param templateCode {@code {module}.{name}}，全局唯一；非空
     * @param moduleCode   归属业务模块；可空
     * @param name         显示名；非空
     * @param description  描述；可空
     * @param availableVars 可用变量声明；可空（空集合）
     * @param defaultChannels 默认渠道（Channel 枚举名）；服务端确保含 STATION 保底
     * @param contents     各渠道文案
     * @param notificationCategory NotificationCategory 枚举名；可空（不参与偏好过滤）
     * @param priority     优先级；null 视作 NORMAL
     * @param status       初始状态；null 视作 ACTIVE
     */
    record CreateTemplateCommand(
            String templateCode,
            String moduleCode,
            String name,
            String description,
            List<VarDef> availableVars,
            List<String> defaultChannels,
            Map<String, ChannelContent> contents,
            String notificationCategory,
            Priority priority,
            TemplateStatus status
    ) {}

    /** 编辑命令。null 字段表示不改。 */
    record UpdateTemplateCommand(
            String name,
            String description,
            List<VarDef> availableVars,
            List<String> defaultChannels,
            Map<String, ChannelContent> contents,
            String notificationCategory,
            Priority priority,
            TemplateStatus status
    ) {}
}
