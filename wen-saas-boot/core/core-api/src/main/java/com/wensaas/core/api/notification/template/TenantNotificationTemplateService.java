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
import com.wensaas.core.api.notification.send.Priority;

/**
 * 租户后台 · 通知模板。
 *
 * <p><b>定位</b>：租户主管理员对平台默认模板（{@code sys_notification_template.tenant_id=0}）做轻量定制。
 * 文案 / 触发场景 / 可用变量全部<b>只读</b>（恒用平台默认版本，不提供文案编辑）；可定制的只有三项：
 * <ul>
 *   <li><b>启停</b>：停用后该模板对本租户完全不发（含站内信兜底）；sys.* 不可停用</li>
 *   <li><b>渠道开关</b>：在模板支持的渠道里勾选走哪些（STATION 保底恒选）</li>
 *   <li><b>优先级</b>：普通 / 重要</li>
 * </ul>
 *
 * <p>定制落 {@code sys_notification_template_pref}（不产生 tenant_id&gt;0 的整模板覆盖行），发送时由
 * {@code TemplateDataLoader} 在平台默认快照上叠加。当三项全为默认（启用 + 渠道 null + 优先级 null）时
 * 不留偏好行（{@code customized=false}）。
 */
public interface TenantNotificationTemplateService {

    /** 列表（按业务模块 + 编码排序；叠加本租户偏好后的生效态）。 */
    List<TemplateListItem> list(String moduleCode, String keyword);

    /**
     * 详情（含只读文案 / 可用变量 + 平台默认设定 + 本租户生效设定）。
     *
     * @throws TenantNotificationTemplateException TEMPLATE_NOT_FOUND
     */
    TemplateDetail get(String templateCode);

    /**
     * 更新本租户偏好（启停 / 渠道 / 优先级）。语义为"目标态"全量覆盖：
     * <ul>
     *   <li>{@code enabled} null → 视作 true</li>
     *   <li>{@code channels} null → 清除渠道覆盖（继承默认）；非空 → 覆盖（须为模板支持渠道子集，自动含 STATION）</li>
     *   <li>{@code priority} null → 清除优先级覆盖（继承默认）</li>
     * </ul>
     * 计算后若与平台默认完全一致（启用 + 无渠道覆盖 + 无优先级覆盖），删除偏好行。
     *
     * @throws TenantNotificationTemplateException TEMPLATE_NOT_FOUND / SYS_TEMPLATE_PROTECTED / INVALID_CHANNEL
     */
    TemplateDetail updatePref(String templateCode, UpdatePrefCommand cmd);

    /**
     * 恢复默认（清除本租户对该模板的全部定制）。等价于 updatePref 三项全默认。
     *
     * @throws TenantNotificationTemplateException TEMPLATE_NOT_FOUND
     */
    TemplateDetail resetPref(String templateCode);

    /** 渠道元数据（供前端渲染渠道开关；{@code fallback=true} 为站内信保底，不可取消）。 */
    List<ChannelMeta> listChannels();

    // ===================== records & enums =====================

    /** 优先级。落库同名（NORMAL / IMPORTANT）。 */
    enum Priority { NORMAL, IMPORTANT }

    /** 渠道元数据。 */
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

    /** 可用变量声明（业务方声明，含示例值，供前端本地预览）。 */
    record VarDef(String name, String sampleValue, boolean required) {}

    /** 单渠道只读文案。 */
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

    /** 列表项：平台默认元信息 + 本租户生效设定。 */
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
        private boolean preset;
        private boolean defaultActive;
        private boolean enabled;
        private List<String> channels;
        private Priority priority;
        private boolean customized;
        private Instant updatedAt;
    }

    /** 详情：只读文案/变量 + 平台默认设定 + 本租户生效设定。 */
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
        private boolean preset;
        private List<VarDef> availableVars;
        private Map<String, ChannelContent> contents;
        private List<String> defaultChannels;
        private Priority defaultPriority;
        private boolean defaultActive;
        private boolean enabled;
        private List<String> channels;
        private Priority priority;
        private boolean customized;
        private Instant updatedAt;
    }

    /** 偏好更新命令。三字段均可空（见 {@link #updatePref} 语义）。 */
    record UpdatePrefCommand(Boolean enabled, List<String> channels, Priority priority) {}
}
