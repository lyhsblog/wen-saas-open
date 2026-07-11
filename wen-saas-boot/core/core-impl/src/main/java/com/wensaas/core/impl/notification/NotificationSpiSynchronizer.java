package com.wensaas.core.impl.notification;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.template.TemplateContent;
import com.wensaas.core.api.notification.template.TemplateVariableDef;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.spi.def.NotificationTemplateDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.wensaas.core.impl.notification.template.SysNotificationTemplate;
import com.wensaas.core.impl.notification.template.SysNotificationTemplateMapper;

/**
 * 把启动期发现的业务模块通知模板同步到 {@code sys_notification_template} (tenant_id=0)。
 *
 * <p>策略（参考 {@code DictSpiSynchronizer}）：
 * <ul>
 *   <li>模板不存在 → INSERT（status=active）</li>
 *   <li>模板已存在 → 比对 name / description / defaultChannels / contents / availableVars / priority，
 *       不同则 UPDATE；模块代码迁移亦写回</li>
 *   <li>v1 跳过 contents 为空（含 of(code,name) 简单工厂入口）的模板</li>
 *   <li>模块下线 → v1 不操作 DB（与字典同惯例；UI 阶段做"管理页隐藏"）</li>
 * </ul>
 *
 * <p>整体 {@link Transactional}，任一步失败回滚 + 启动失败。
 */
public class NotificationSpiSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(NotificationSpiSynchronizer.class);
    private static final long PLATFORM_TENANT_ID = 0L;

    private final SysNotificationTemplateMapper mapper;

    public NotificationSpiSynchronizer(SysNotificationTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void sync(List<BusinessModuleDescriptor> enabledModules) {
        // 只读写 tenant_id=0 的默认模板行；启动期无请求上下文，显式以平台租户身份执行，
        // 混合表（sys_notification_template）的租户线条件即命中 tenant_id=0。
        TenantContext.runAs(PLATFORM_TENANT_ID, () -> doSync(enabledModules));
    }

    private void doSync(List<BusinessModuleDescriptor> enabledModules) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (BusinessModuleDescriptor d : enabledModules) {
            String moduleCode = d.metadata().code();
            for (NotificationTemplateDef def : d.notificationTemplates()) {
                if (def.contents().isEmpty()) {
                    // 走 of(code, name) 简单入口的占位模板：v1 不入库（无内容无法发送），打 WARN 提醒补全
                    log.warn("[Notify-SPI] 模板 {} contents 为空，跳过入库（业务方需用完整 record 声明）", def.code());
                    skipped++;
                    continue;
                }
                if (syncOne(moduleCode, def)) {
                    created++;
                } else {
                    updated++;
                }
            }
        }
        log.info("[Notify-SPI] 已同步业务模块通知模板：新建 {}、命中 {}、跳过 {}", created, updated, skipped);
    }

    /** @return true = 新建；false = 已存在（含 UPDATE / NoChange） */
    private boolean syncOne(String moduleCode, NotificationTemplateDef def) {
        SysNotificationTemplate existing = mapper.selectPlatformDefault(def.code());

        if (existing == null) {
            SysNotificationTemplate row = new SysNotificationTemplate();
            row.setId(IdWorker.getId());
            row.setTenantId(PLATFORM_TENANT_ID);
            row.setTemplateCode(def.code());
            row.setModuleCode(moduleCode);
            row.setName(def.name());
            row.setDescription(def.description());
            row.setAvailableVars(toVarList(def.availableVars()));
            row.setDefaultChannels(toChannelList(def.defaultChannels()));
            row.setContents(toContentMap(def.contents()));
            row.setPriority(def.priority().name());
            row.setStatus("active");
            mapper.insert(row);
            log.info("[Notify-SPI] 新增模板 {}（owner={}, channels={}）",
                    def.code(), moduleCode, def.defaultChannels());
            return true;
        }

        boolean changed = false;
        if (!Objects.equals(existing.getName(), def.name())) {
            existing.setName(def.name()); changed = true;
        }
        if (!Objects.equals(existing.getDescription(), def.description())) {
            existing.setDescription(def.description()); changed = true;
        }
        List<Map<String, Object>> newVars = toVarList(def.availableVars());
        if (!Objects.equals(existing.getAvailableVars(), newVars)) {
            existing.setAvailableVars(newVars); changed = true;
        }
        List<String> newChannels = toChannelList(def.defaultChannels());
        if (!Objects.equals(existing.getDefaultChannels(), newChannels)) {
            existing.setDefaultChannels(newChannels); changed = true;
        }
        Map<String, Map<String, Object>> newContents = toContentMap(def.contents());
        if (!Objects.equals(existing.getContents(), newContents)) {
            existing.setContents(newContents); changed = true;
        }
        if (!Objects.equals(existing.getPriority(), def.priority().name())) {
            existing.setPriority(def.priority().name()); changed = true;
        }
        if (!Objects.equals(existing.getModuleCode(), moduleCode)) {
            existing.setModuleCode(moduleCode); changed = true;
        }
        if (changed) {
            mapper.updateById(existing);
            log.info("[Notify-SPI] 更新模板 {}（owner={}）", def.code(), moduleCode);
        }
        return false;
    }

    static List<Map<String, Object>> toVarList(List<TemplateVariableDef> defs) {
        if (defs == null || defs.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(defs.size());
        for (TemplateVariableDef v : defs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", v.name());
            m.put("sampleValue", v.sampleValue());
            m.put("required", v.required());
            out.add(m);
        }
        return out;
    }

    static List<String> toChannelList(List<Channel> channels) {
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(channels.size());
        for (Channel c : channels) {
            out.add(c.name());
        }
        return out;
    }

    static Map<String, Map<String, Object>> toContentMap(Map<Channel, TemplateContent> contents) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<Channel, TemplateContent> e : contents.entrySet()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("title", e.getValue().title());
            v.put("body", e.getValue().body());
            out.put(e.getKey().name(), v);
        }
        return out;
    }
}
