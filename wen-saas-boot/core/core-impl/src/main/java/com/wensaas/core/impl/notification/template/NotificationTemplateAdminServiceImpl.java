package com.wensaas.core.impl.notification.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminException;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminService;
import com.wensaas.core.api.tenant.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * {@link NotificationTemplateAdminService} 默认实现。
 *
 * <p>仅操作 {@code tenant_id=0} 平台默认模板；全程显式按 {@code tenant_id=0} 过滤并绕过
 * MP TenantLineInnerInterceptor（参考 {@code OpsTeamMemberAdminServiceImpl}）。
 */
public class NotificationTemplateAdminServiceImpl implements NotificationTemplateAdminService {

    private static final long OPS_TENANT = TenantContext.PLATFORM_TENANT_ID;
    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_INACTIVE = "inactive";
    private static final String SYS_PREFIX = "sys.";

    /** {module}.{name}（允许多段，如 sys.task.export.completed）。 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$");

    private final SysNotificationTemplateMapper mapper;
    private final ObjectProvider<TemplateResolver> templateResolverProvider;

    public NotificationTemplateAdminServiceImpl(SysNotificationTemplateMapper mapper,
                                                ObjectProvider<TemplateResolver> templateResolverProvider) {
        this.mapper = mapper;
        this.templateResolverProvider = templateResolverProvider;
    }

    @Override
    public List<TemplateListItem> listTemplates(StatusFilter status, String moduleCode, String keyword) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            LambdaQueryWrapper<SysNotificationTemplate> q = new LambdaQueryWrapper<SysNotificationTemplate>()
                    .eq(SysNotificationTemplate::getTenantId, OPS_TENANT);
            if (status != null && status != StatusFilter.ALL) {
                q.eq(SysNotificationTemplate::getStatus,
                        status == StatusFilter.ACTIVE ? STATUS_ACTIVE : STATUS_INACTIVE);
            }
            if (moduleCode != null && !moduleCode.isBlank()) {
                q.eq(SysNotificationTemplate::getModuleCode, moduleCode.trim());
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.trim() + "%";
                q.and(w -> w.like(SysNotificationTemplate::getTemplateCode, kw)
                        .or().like(SysNotificationTemplate::getName, kw));
            }
            q.orderByAsc(SysNotificationTemplate::getModuleCode)
                    .orderByAsc(SysNotificationTemplate::getTemplateCode);
            return mapper.selectList(q).stream().map(NotificationTemplateAdminServiceImpl::toListItem).toList();
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public TemplateDetail getTemplate(String templateCode) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            return toDetail(requireTemplate(templateCode));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public TemplateDetail createTemplate(CreateTemplateCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        validateCode(cmd.templateCode());
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("模板名不能为空");
        }
        List<String> channels = normalizeChannels(cmd.defaultChannels());
        Map<String, ChannelContent> contents = cmd.contents() == null ? Map.of() : cmd.contents();
        validateChannelKeys(contents.keySet());
        validateContents(channels, contents);

        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            if (getByCode(cmd.templateCode()) != null) {
                throw new NotificationTemplateAdminException(
                        NotificationTemplateAdminException.Code.CODE_CONFLICT,
                        "模板编码已存在: " + cmd.templateCode());
            }
            Long operatorId = currentUserId();
            SysNotificationTemplate e = new SysNotificationTemplate();
            e.setTenantId(OPS_TENANT);
            e.setTemplateCode(cmd.templateCode());
            e.setModuleCode(cmd.moduleCode());
            e.setName(cmd.name());
            e.setDescription(cmd.description());
            e.setAvailableVars(toVarMapList(cmd.availableVars()));
            e.setDefaultChannels(channels);
            e.setContents(toContentsEntity(contents));
            e.setNotificationCategory(cmd.notificationCategory());
            e.setPriority(cmd.priority() == null ? Priority.NORMAL.name() : cmd.priority().name());
            e.setStatus(cmd.status() == TemplateStatus.INACTIVE ? STATUS_INACTIVE : STATUS_ACTIVE);
            e.setCreatedBy(operatorId);
            e.setUpdatedBy(operatorId);
            mapper.insert(e);
            evict(cmd.templateCode());
            return toDetail(getByCode(cmd.templateCode()));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public TemplateDetail updateTemplate(String templateCode, UpdateTemplateCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        requireNotPreset(templateCode, "编辑");
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysNotificationTemplate e = requireTemplate(templateCode);
            // ② 审计对象名：加载后即登记「旧名/新名」（业务前，失败也留意图）
            AuditObjects.add(null, templateCode, e.getName(), cmd.name());

            if (cmd.name() != null && !cmd.name().isBlank()) e.setName(cmd.name());
            if (cmd.description() != null) e.setDescription(cmd.description());
            if (cmd.availableVars() != null) e.setAvailableVars(toVarMapList(cmd.availableVars()));
            if (cmd.notificationCategory() != null) e.setNotificationCategory(cmd.notificationCategory());
            if (cmd.priority() != null) e.setPriority(cmd.priority().name());
            if (cmd.status() != null) {
                e.setStatus(cmd.status() == TemplateStatus.INACTIVE ? STATUS_INACTIVE : STATUS_ACTIVE);
            }

            List<String> channels = cmd.defaultChannels() != null
                    ? normalizeChannels(cmd.defaultChannels()) : e.getDefaultChannels();
            if (cmd.contents() != null) {
                validateChannelKeys(cmd.contents().keySet());
                validateContents(channels, cmd.contents());
                e.setContents(toContentsEntity(cmd.contents()));
            } else if (cmd.defaultChannels() != null) {
                // 仅改渠道而不改文案：用既有文案校验
                validateContents(channels, toContentsApi(e.getContents()));
            }
            if (cmd.defaultChannels() != null) e.setDefaultChannels(channels);

            e.setUpdatedBy(currentUserId());
            mapper.updateById(e);
            evict(templateCode);
            return toDetail(getByCode(templateCode));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public void enable(String templateCode) {
        setStatus(templateCode, STATUS_ACTIVE);
    }

    @Override
    @Transactional
    public void disable(String templateCode) {
        requireNotPreset(templateCode, "停用");
        setStatus(templateCode, STATUS_INACTIVE);
    }

    @Override
    @Transactional
    public void deleteTemplate(String templateCode) {
        requireNotPreset(templateCode, "删除");
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysNotificationTemplate e = requireTemplate(templateCode);
            // ② 审计对象名：加载后即登记当前名
            AuditObjects.add(null, templateCode, e.getName());
            mapper.deleteById(e.getId());
            evict(templateCode);
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

    private void setStatus(String templateCode, String status) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysNotificationTemplate e = requireTemplate(templateCode);
            // ② 审计对象名：加载后即登记当前名（覆盖 enable/disable）
            AuditObjects.add(null, templateCode, e.getName());
            if (status.equals(e.getStatus())) return;
            e.setStatus(status);
            e.setUpdatedBy(currentUserId());
            mapper.updateById(e);
            evict(templateCode);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    /** 调用方需已 handle(IGNORE_TENANT_LINE)。 */
    private SysNotificationTemplate getByCode(String templateCode) {
        return mapper.selectOne(new LambdaQueryWrapper<SysNotificationTemplate>()
                .eq(SysNotificationTemplate::getTenantId, OPS_TENANT)
                .eq(SysNotificationTemplate::getTemplateCode, templateCode));
    }

    /** 调用方需已 handle(IGNORE_TENANT_LINE)。 */
    private SysNotificationTemplate requireTemplate(String templateCode) {
        SysNotificationTemplate e = getByCode(templateCode);
        if (e == null) {
            throw new NotificationTemplateAdminException(
                    NotificationTemplateAdminException.Code.TEMPLATE_NOT_FOUND, "模板不存在: " + templateCode);
        }
        return e;
    }

    private static void requireNotPreset(String templateCode, String action) {
        if (templateCode != null && templateCode.startsWith(SYS_PREFIX)) {
            throw new NotificationTemplateAdminException(
                    NotificationTemplateAdminException.Code.SYS_TEMPLATE_PROTECTED,
                    "底座预置模板（sys.*）不可" + action + ": " + templateCode);
        }
    }

    private void evict(String templateCode) {
        TemplateResolver resolver = templateResolverProvider.getIfAvailable();
        if (resolver != null) {
            // 默认模板文案内嵌于每个租户的快照，改默认须连带刷新各租户缓存（含 tenant_id=0）
            resolver.evictCodeAllTenants(templateCode);
        }
    }

    private static void validateCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "模板编码不合法（要求 {module}.{name}，小写字母/数字/下划线，如 crm.follow_reminder）: " + code);
        }
    }

    /** 校验渠道名合法 + 强制加入 STATION 保底（去重保序）。 */
    private static List<String> normalizeChannels(List<String> raw) {
        List<String> out = new ArrayList<>();
        out.add(Channel.STATION.name());
        if (raw != null) {
            for (String c : raw) {
                String channel = parseChannel(c).name();
                if (!out.contains(channel)) out.add(channel);
            }
        }
        return out;
    }

    private static void validateChannelKeys(Iterable<String> channels) {
        for (String c : channels) parseChannel(c);
    }

    private static Channel parseChannel(String c) {
        try {
            return Channel.valueOf(c);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new NotificationTemplateAdminException(
                    NotificationTemplateAdminException.Code.INVALID_CHANNEL, "未知渠道: " + c);
        }
    }

    /** 启用的每个渠道文案不能全空；STATION / EMAIL 要求标题非空。 */
    private static void validateContents(List<String> channels, Map<String, ChannelContent> contents) {
        for (String ch : channels) {
            ChannelContent cc = contents == null ? null : contents.get(ch);
            boolean titleBlank = cc == null || cc.title() == null || cc.title().isBlank();
            boolean bodyBlank = cc == null || cc.body() == null || cc.body().isBlank();
            if (bodyBlank) {
                throw new NotificationTemplateAdminException(
                        NotificationTemplateAdminException.Code.EMPTY_CONTENT, "渠道文案正文不能为空: " + ch);
            }
            boolean needTitle = Channel.STATION.name().equals(ch) || Channel.EMAIL.name().equals(ch);
            if (needTitle && titleBlank) {
                throw new NotificationTemplateAdminException(
                        NotificationTemplateAdminException.Code.EMPTY_CONTENT, "渠道文案标题不能为空: " + ch);
            }
        }
    }

    // ----- entity <-> api 形态转换 -----

    private static List<Map<String, Object>> toVarMapList(List<VarDef> vars) {
        if (vars == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(vars.size());
        for (VarDef v : vars) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", v.name());
            m.put("sampleValue", v.sampleValue());
            m.put("required", v.required());
            out.add(m);
        }
        return out;
    }

    private static List<VarDef> toVarDefList(List<Map<String, Object>> raw) {
        if (raw == null) return List.of();
        List<VarDef> out = new ArrayList<>(raw.size());
        for (Map<String, Object> m : raw) {
            out.add(new VarDef(
                    asString(m.get("name")),
                    asString(m.get("sampleValue")),
                    Boolean.TRUE.equals(m.get("required"))));
        }
        return out;
    }

    private static Map<String, Map<String, Object>> toContentsEntity(Map<String, ChannelContent> contents) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (contents != null) {
            for (Map.Entry<String, ChannelContent> en : contents.entrySet()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", en.getValue() == null ? null : en.getValue().title());
                m.put("body", en.getValue() == null ? null : en.getValue().body());
                out.put(en.getKey(), m);
            }
        }
        return out;
    }

    private static Map<String, ChannelContent> toContentsApi(Map<String, Map<String, Object>> raw) {
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

    private static TemplateListItem toListItem(SysNotificationTemplate e) {
        return new TemplateListItem(
                e.getTemplateCode(), e.getModuleCode(), e.getName(), e.getDescription(),
                toStatus(e.getStatus()), toPriority(e.getPriority()), e.getNotificationCategory(),
                e.getDefaultChannels() == null ? List.of() : e.getDefaultChannels(),
                e.getTemplateCode() != null && e.getTemplateCode().startsWith(SYS_PREFIX),
                toInstant(e.getCreatedAt()), toInstant(e.getUpdatedAt()));
    }

    private static TemplateDetail toDetail(SysNotificationTemplate e) {
        return new TemplateDetail(
                e.getTemplateCode(), e.getModuleCode(), e.getName(), e.getDescription(),
                toVarDefList(e.getAvailableVars()),
                e.getDefaultChannels() == null ? List.of() : e.getDefaultChannels(),
                toContentsApi(e.getContents()),
                e.getNotificationCategory(), toPriority(e.getPriority()), toStatus(e.getStatus()),
                e.getTemplateCode() != null && e.getTemplateCode().startsWith(SYS_PREFIX),
                toInstant(e.getCreatedAt()), toInstant(e.getUpdatedAt()));
    }

    private static TemplateStatus toStatus(String s) {
        return STATUS_INACTIVE.equals(s) ? TemplateStatus.INACTIVE : TemplateStatus.ACTIVE;
    }

    private static Priority toPriority(String s) {
        return Priority.IMPORTANT.name().equalsIgnoreCase(s) ? Priority.IMPORTANT : Priority.NORMAL;
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
