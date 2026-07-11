package com.wensaas.core.impl.notification;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.template.TemplateContent;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.ModuleMetadata;
import com.wensaas.core.api.spi.def.NotificationTemplateDef;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.notification.template.SysNotificationTemplate;
import com.wensaas.core.impl.notification.template.SysNotificationTemplateMapper;

class NotificationSpiSynchronizerTest {

    private final SysNotificationTemplateMapper mapper = mock(SysNotificationTemplateMapper.class);
    private final NotificationSpiSynchronizer sync = new NotificationSpiSynchronizer(mapper);

    @Test
    void newTemplate_inserts() {
        when(mapper.selectPlatformDefault("crm.welcome")).thenReturn(null);
        sync.sync(List.of(moduleWith("crm",
                fullTemplate("crm.welcome", "欢迎"))));
        ArgumentCaptor<SysNotificationTemplate> cap = ArgumentCaptor.forClass(SysNotificationTemplate.class);
        verify(mapper).insert(cap.capture());
        SysNotificationTemplate row = cap.getValue();
        assertThat(row.getTenantId()).isEqualTo(0L);
        assertThat(row.getTemplateCode()).isEqualTo("crm.welcome");
        assertThat(row.getModuleCode()).isEqualTo("crm");
        assertThat(row.getStatus()).isEqualTo("active");
        assertThat(row.getDefaultChannels()).containsExactly("STATION");
        assertThat(row.getContents().get("STATION")).containsEntry("title", "欢迎");
    }

    @Test
    void existingTemplate_unchanged_no_update() {
        SysNotificationTemplate existing = makeExisting();
        when(mapper.selectPlatformDefault("crm.welcome")).thenReturn(existing);
        sync.sync(List.of(moduleWith("crm", fullTemplate("crm.welcome", "欢迎"))));
        verify(mapper, never()).updateById(any(SysNotificationTemplate.class));
        verify(mapper, never()).insert(any(SysNotificationTemplate.class));
    }

    @Test
    void existingTemplate_nameChanged_updates() {
        SysNotificationTemplate existing = makeExisting();
        when(mapper.selectPlatformDefault("crm.welcome")).thenReturn(existing);
        sync.sync(List.of(moduleWith("crm", fullTemplate("crm.welcome", "新名字"))));
        ArgumentCaptor<SysNotificationTemplate> cap = ArgumentCaptor.forClass(SysNotificationTemplate.class);
        verify(mapper).updateById(cap.capture());
        assertThat(cap.getValue().getName()).isEqualTo("新名字");
    }

    @Test
    void emptyContents_template_skipped_warn() {
        // 走 of(code,name) 的占位模板：contents 为空 → 跳过入库（log.warn 提示业务方补全）
        when(mapper.selectPlatformDefault("crm.placeholder")).thenReturn(null);
        sync.sync(List.of(moduleWith("crm",
                NotificationTemplateDef.of("crm.placeholder", "占位"))));
        verify(mapper, never()).insert(any(SysNotificationTemplate.class));
    }

    @Test
    void existingTemplate_moduleCodeMigration_updates() {
        SysNotificationTemplate existing = makeExisting();
        existing.setModuleCode("old_module");
        when(mapper.selectPlatformDefault("crm.welcome")).thenReturn(existing);
        sync.sync(List.of(moduleWith("crm", fullTemplate("crm.welcome", "欢迎"))));
        ArgumentCaptor<SysNotificationTemplate> cap = ArgumentCaptor.forClass(SysNotificationTemplate.class);
        verify(mapper).updateById(cap.capture());
        assertThat(cap.getValue().getModuleCode()).isEqualTo("crm");
    }

    private static NotificationTemplateDef fullTemplate(String code, String name) {
        return new NotificationTemplateDef(
                code, name, null,
                List.of(Channel.STATION),
                Map.of(Channel.STATION, TemplateContent.of("欢迎", "body")),
                List.of(), Priority.NORMAL);
    }

    private static SysNotificationTemplate makeExisting() {
        SysNotificationTemplate row = new SysNotificationTemplate();
        row.setId(1L);
        row.setTenantId(0L);
        row.setTemplateCode("crm.welcome");
        row.setModuleCode("crm");
        row.setName("欢迎");
        row.setDefaultChannels(List.of("STATION"));
        row.setContents(Map.of("STATION", Map.of("title", "欢迎", "body", "body")));
        row.setAvailableVars(List.of());
        row.setPriority("NORMAL");
        row.setStatus("active");
        return row;
    }

    private static BusinessModuleDescriptor moduleWith(String code, NotificationTemplateDef... templates) {
        return new BusinessModuleDescriptor() {
            @Override
            public ModuleMetadata metadata() {
                return ModuleMetadata.of(code, code + " name", "1.0.0");
            }
            @Override
            public List<NotificationTemplateDef> notificationTemplates() {
                return List.of(templates);
            }
        };
    }
}
