package com.wensaas.core.impl.notification.template;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.template.TemplateContent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 从 DB 装载模板快照。
 *
 * <p>文案恒取 <b>平台默认</b>（{@code tenant_id=0}）版本；其上叠加租户偏好
 * （{@link SysNotificationTemplatePref}，仅 启停 / 渠道 / 优先级）：
 * <ul>
 *   <li>平台默认不存在 / 非 active（运营全局停用） → {@link Optional#empty()}</li>
 *   <li>租户偏好 {@code enabled=false} → 返回 {@code suppressed} 快照（发送链路静默跳过，含站内信兜底）</li>
 *   <li>租户偏好 {@code channels} 非空 → 覆盖默认通道（仍含 STATION 保底，由偏好写入侧保证）</li>
 *   <li>租户偏好 {@code priority} 非空 → 覆盖默认优先级</li>
 * </ul>
 *
 * <p>两张表均显式按 tenant_id 维度查询，调用方走 {@link InterceptorIgnoreHelper} 忽略 MP TenantLine
 * （默认模板存在 tenant_id=0，必须能被任意租户读到）。
 */
public class TemplateDataLoader {

    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();
    private static final String STATUS_ACTIVE = "active";

    private final SysNotificationTemplateMapper mapper;
    private final SysNotificationTemplatePrefMapper prefMapper;

    public TemplateDataLoader(SysNotificationTemplateMapper mapper,
                              SysNotificationTemplatePrefMapper prefMapper) {
        this.mapper = mapper;
        this.prefMapper = prefMapper;
    }

    public Optional<TemplateSnapshot> load(long tenantId, String templateCode) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysNotificationTemplate def = mapper.selectPlatformDefault(templateCode);
            if (def == null || !STATUS_ACTIVE.equalsIgnoreCase(def.getStatus())) {
                return Optional.empty();
            }
            SysNotificationTemplatePref pref = prefMapper.selectByTenantAndCode(tenantId, templateCode);
            if (pref != null && Boolean.FALSE.equals(pref.getEnabled())) {
                return Optional.of(suppressedSnapshot(tenantId, def));
            }
            return Optional.of(toSnapshot(tenantId, def, pref));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    /** 平台默认快照 + 租户偏好叠加（pref 可空）。 */
    static TemplateSnapshot toSnapshot(long tenantId, SysNotificationTemplate def, SysNotificationTemplatePref pref) {
        Map<Channel, TemplateContent> contents = parseContents(def);

        List<Channel> channels = new ArrayList<>();
        List<String> channelSource = pref != null && pref.getChannels() != null
                ? pref.getChannels() : def.getDefaultChannels();
        if (channelSource != null) {
            for (String c : channelSource) {
                channels.add(Channel.valueOf(c.toUpperCase(Locale.ROOT)));
            }
        }

        Priority priority = parsePriority(
                pref != null && pref.getPriority() != null ? pref.getPriority() : def.getPriority());

        boolean customized = pref != null;
        return new TemplateSnapshot(
                customized ? tenantId : def.getTenantId(),
                def.getTemplateCode(),
                def.getModuleCode(),
                channels,
                contents,
                priority,
                def.getNotificationCategory(),
                false);
    }

    private static TemplateSnapshot suppressedSnapshot(long tenantId, SysNotificationTemplate def) {
        return new TemplateSnapshot(
                tenantId, def.getTemplateCode(), def.getModuleCode(),
                List.of(), Map.of(), Priority.NORMAL, def.getNotificationCategory(), true);
    }

    private static Map<Channel, TemplateContent> parseContents(SysNotificationTemplate def) {
        Map<Channel, TemplateContent> contents = new LinkedHashMap<>();
        if (def.getContents() != null) {
            for (Map.Entry<String, Map<String, Object>> e : def.getContents().entrySet()) {
                Channel ch = Channel.valueOf(e.getKey().toUpperCase(Locale.ROOT));
                Map<String, Object> v = e.getValue();
                String title = v.get("title") == null ? null : v.get("title").toString();
                Object bodyObj = v.get("body");
                String body = bodyObj == null ? "" : bodyObj.toString();
                if (body.isBlank()) {
                    // 跳过：DB 出脏数据时不构造 TemplateContent（会抛 IllegalArgumentException）；
                    // 上游 MessageService 处理"模板缺失该通道"。
                    continue;
                }
                contents.put(ch, new TemplateContent(title, body));
            }
        }
        return contents;
    }

    private static Priority parsePriority(String raw) {
        if (raw != null && !raw.isBlank()) {
            return Priority.valueOf(raw.toUpperCase(Locale.ROOT));
        }
        return Priority.NORMAL;
    }
}
