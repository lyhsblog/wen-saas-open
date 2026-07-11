package com.wensaas.core.impl.notification.template;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.template.TenantNotificationTemplateException;
import com.wensaas.core.api.notification.template.TenantNotificationTemplateService;
import com.wensaas.core.api.tenant.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@link TenantNotificationTemplateService} 默认实现。
 *
 * <p>平台默认模板（{@code tenant_id=0}）读取走 {@link InterceptorIgnoreHelper} 绕 MP TenantLine；
 * 偏好读写显式带当前租户 id（{@link TenantContext#require()}）。
 */
public class TenantNotificationTemplateServiceImpl implements TenantNotificationTemplateService {

    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();
    private static final String STATUS_ACTIVE = "active";
    private static final String SYS_PREFIX = "sys.";

    private final SysNotificationTemplateMapper templateMapper;
    private final SysNotificationTemplatePrefMapper prefMapper;
    private final ObjectProvider<TemplateResolver> templateResolverProvider;

    public TenantNotificationTemplateServiceImpl(SysNotificationTemplateMapper templateMapper,
                                                 SysNotificationTemplatePrefMapper prefMapper,
                                                 ObjectProvider<TemplateResolver> templateResolverProvider) {
        this.templateMapper = templateMapper;
        this.prefMapper = prefMapper;
        this.templateResolverProvider = templateResolverProvider;
    }

    @Override
    public List<TemplateListItem> list(String moduleCode, String keyword) {
        long tenantId = TenantContext.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            Map<String, SysNotificationTemplatePref> prefs = prefIndex(tenantId);
            String mod = moduleCode == null ? null : moduleCode.trim();
            String kw = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
            List<TemplateListItem> out = new ArrayList<>();
            for (SysNotificationTemplate def : templateMapper.selectPlatformAll()) {
                if (mod != null && !mod.isEmpty() && !mod.equals(def.getModuleCode())) {
                    continue;
                }
                if (kw != null && !kw.isEmpty() && !matchesKeyword(def, kw)) {
                    continue;
                }
                out.add(toListItem(def, prefs.get(def.getTemplateCode())));
            }
            return out;
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public TemplateDetail get(String templateCode) {
        long tenantId = TenantContext.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysNotificationTemplate def = requireDefault(templateCode);
            return toDetail(def, prefMapper.selectByTenantAndCode(tenantId, templateCode));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public TemplateDetail updatePref(String templateCode, UpdatePrefCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        long tenantId = TenantContext.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysNotificationTemplate def = requireDefault(templateCode);
            boolean preset = isPreset(templateCode);

            boolean enabled = cmd.enabled() == null || cmd.enabled();
            if (preset && !enabled) {
                throw new TenantNotificationTemplateException(
                        TenantNotificationTemplateException.Code.SYS_TEMPLATE_PROTECTED,
                        "底座预置模板（sys.*）不可停用: " + templateCode);
            }

            List<String> channelsOverride = normalizeChannelsOverride(def, cmd.channels());
            String priorityOverride = cmd.priority() == null ? null : cmd.priority().name();
            if (priorityOverride != null && priorityOverride.equalsIgnoreCase(defaultPriorityName(def))) {
                priorityOverride = null;
            }

            SysNotificationTemplatePref existing = prefMapper.selectByTenantAndCode(tenantId, templateCode);
            boolean isDefaultState = enabled && channelsOverride == null && priorityOverride == null;

            if (isDefaultState) {
                if (existing != null) {
                    prefMapper.deleteById(existing.getId());
                }
            } else if (existing != null) {
                existing.setEnabled(enabled);
                existing.setChannels(channelsOverride);
                existing.setPriority(priorityOverride);
                existing.setUpdatedBy(currentUserId());
                prefMapper.updateById(existing);
            } else {
                SysNotificationTemplatePref row = new SysNotificationTemplatePref();
                row.setTenantId(tenantId);
                row.setTemplateCode(templateCode);
                row.setEnabled(enabled);
                row.setChannels(channelsOverride);
                row.setPriority(priorityOverride);
                row.setCreatedBy(currentUserId());
                row.setUpdatedBy(currentUserId());
                prefMapper.insert(row);
            }

            evict(tenantId, templateCode);
            return toDetail(def, prefMapper.selectByTenantAndCode(tenantId, templateCode));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public TemplateDetail resetPref(String templateCode) {
        long tenantId = TenantContext.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysNotificationTemplate def = requireDefault(templateCode);
            SysNotificationTemplatePref existing = prefMapper.selectByTenantAndCode(tenantId, templateCode);
            if (existing != null) {
                prefMapper.deleteById(existing.getId());
                evict(tenantId, templateCode);
            }
            return toDetail(def, null);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public List<ChannelMeta> listChannels() {
        return List.of(
                new ChannelMeta(Channel.STATION.name(), "站内信", true),
                new ChannelMeta(Channel.EMAIL.name(), "邮件", false),
                new ChannelMeta(Channel.SMS.name(), "短信", false),
                new ChannelMeta(Channel.WECHAT.name(), "微信", false),
                new ChannelMeta(Channel.WECOM.name(), "企业微信", false),
                new ChannelMeta(Channel.DINGTALK.name(), "钉钉", false));
    }

    // ===================== helpers =====================

    /** 调用方需已 handle(IGNORE_TENANT_LINE)。 */
    private SysNotificationTemplate requireDefault(String templateCode) {
        SysNotificationTemplate def = templateMapper.selectPlatformDefault(templateCode);
        if (def == null) {
            throw new TenantNotificationTemplateException(
                    TenantNotificationTemplateException.Code.TEMPLATE_NOT_FOUND, "模板不存在: " + templateCode);
        }
        return def;
    }

    private Map<String, SysNotificationTemplatePref> prefIndex(long tenantId) {
        Map<String, SysNotificationTemplatePref> map = new LinkedHashMap<>();
        for (SysNotificationTemplatePref p : prefMapper.selectByTenant(tenantId)) {
            map.put(p.getTemplateCode(), p);
        }
        return map;
    }

    /**
     * 归一渠道覆盖：
     * <ul>
     *   <li>入参 null → 返回 null（继承默认）</li>
     *   <li>校验每个渠道 ∈ Channel 枚举 且 ∈ 模板默认文案渠道；强制含 STATION；去重保序</li>
     *   <li>若归一结果与默认渠道集合一致 → 返回 null（视作无覆盖）</li>
     * </ul>
     */
    private static List<String> normalizeChannelsOverride(SysNotificationTemplate def, List<String> raw) {
        if (raw == null) {
            return null;
        }
        Set<String> supported = new LinkedHashSet<>();
        if (def.getContents() != null) {
            for (String k : def.getContents().keySet()) {
                supported.add(parseChannel(k).name());
            }
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(Channel.STATION.name());
        for (String c : raw) {
            String channel = parseChannel(c).name();
            if (!supported.contains(channel)) {
                throw new TenantNotificationTemplateException(
                        TenantNotificationTemplateException.Code.INVALID_CHANNEL,
                        "该模板不支持渠道（默认文案缺失）: " + channel);
            }
            out.add(channel);
        }
        Set<String> defaultSet = new LinkedHashSet<>(
                def.getDefaultChannels() == null ? List.of() : def.getDefaultChannels());
        if (out.equals(defaultSet)) {
            return null;
        }
        return new ArrayList<>(out);
    }

    private static Channel parseChannel(String c) {
        try {
            return Channel.valueOf(c);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new TenantNotificationTemplateException(
                    TenantNotificationTemplateException.Code.INVALID_CHANNEL, "未知渠道: " + c);
        }
    }

    private void evict(long tenantId, String templateCode) {
        TemplateResolver resolver = templateResolverProvider.getIfAvailable();
        if (resolver != null) {
            resolver.evict(tenantId, templateCode);
        }
    }

    private static boolean matchesKeyword(SysNotificationTemplate def, String kw) {
        return contains(def.getTemplateCode(), kw) || contains(def.getName(), kw);
    }

    private static boolean contains(String s, String kw) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(kw);
    }

    private static boolean isPreset(String templateCode) {
        return templateCode != null && templateCode.startsWith(SYS_PREFIX);
    }

    private TemplateListItem toListItem(SysNotificationTemplate def, SysNotificationTemplatePref pref) {
        boolean enabled = pref == null || !Boolean.FALSE.equals(pref.getEnabled());
        List<String> channels = effectiveChannels(def, pref);
        Priority priority = effectivePriority(def, pref);
        return new TemplateListItem(
                def.getTemplateCode(), def.getModuleCode(), def.getName(), def.getDescription(),
                isPreset(def.getTemplateCode()), isDefaultActive(def),
                enabled, channels, priority, pref != null,
                toInstant(pref != null ? pref.getUpdatedAt() : def.getUpdatedAt()));
    }

    private TemplateDetail toDetail(SysNotificationTemplate def, SysNotificationTemplatePref pref) {
        boolean enabled = pref == null || !Boolean.FALSE.equals(pref.getEnabled());
        return new TemplateDetail(
                def.getTemplateCode(), def.getModuleCode(), def.getName(), def.getDescription(),
                isPreset(def.getTemplateCode()),
                toVarDefList(def.getAvailableVars()),
                toContents(def.getContents()),
                def.getDefaultChannels() == null ? List.of() : def.getDefaultChannels(),
                toPriority(defaultPriorityName(def)),
                isDefaultActive(def),
                enabled, effectiveChannels(def, pref), effectivePriority(def, pref), pref != null,
                toInstant(pref != null ? pref.getUpdatedAt() : def.getUpdatedAt()));
    }

    private static boolean isDefaultActive(SysNotificationTemplate def) {
        return STATUS_ACTIVE.equalsIgnoreCase(def.getStatus());
    }

    private static List<String> effectiveChannels(SysNotificationTemplate def, SysNotificationTemplatePref pref) {
        if (pref != null && pref.getChannels() != null) {
            return pref.getChannels();
        }
        return def.getDefaultChannels() == null ? List.of() : def.getDefaultChannels();
    }

    private static Priority effectivePriority(SysNotificationTemplate def, SysNotificationTemplatePref pref) {
        String raw = pref != null && pref.getPriority() != null ? pref.getPriority() : defaultPriorityName(def);
        return toPriority(raw);
    }

    private static String defaultPriorityName(SysNotificationTemplate def) {
        return def.getPriority() == null || def.getPriority().isBlank()
                ? Priority.NORMAL.name() : def.getPriority();
    }

    private static Priority toPriority(String s) {
        return Priority.IMPORTANT.name().equalsIgnoreCase(s) ? Priority.IMPORTANT : Priority.NORMAL;
    }

    private static List<VarDef> toVarDefList(List<Map<String, Object>> raw) {
        if (raw == null) {
            return List.of();
        }
        List<VarDef> out = new ArrayList<>(raw.size());
        for (Map<String, Object> m : raw) {
            out.add(new VarDef(asString(m.get("name")), asString(m.get("sampleValue")),
                    Boolean.TRUE.equals(m.get("required"))));
        }
        return out;
    }

    private static Map<String, ChannelContent> toContents(Map<String, Map<String, Object>> raw) {
        Map<String, ChannelContent> out = new LinkedHashMap<>();
        if (raw != null) {
            for (Map.Entry<String, Map<String, Object>> en : raw.entrySet()) {
                Map<String, Object> v = en.getValue();
                out.put(en.getKey(), new ChannelContent(
                        v == null ? null : asString(v.get("title")),
                        v == null ? null : asString(v.get("body"))));
            }
        }
        return out;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long currentUserId() {
        UserPrincipal p = UserContext.get();
        return p != null ? p.userId() : null;
    }

    private static Instant toInstant(LocalDateTime dt) {
        return dt != null ? dt.toInstant(ZoneOffset.of("+08:00")) : null;
    }
}
