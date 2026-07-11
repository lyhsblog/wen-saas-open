package com.wensaas.core.impl.notification.template;

import com.wensaas.core.api.notification.template.NotificationTemplateAdminException;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminService.ChannelContent;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminService.CreateTemplateCommand;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminService.TemplateDetail;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminService.UpdateTemplateCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationTemplateAdminServiceImplTest {

    private SysNotificationTemplateMapper mapper;
    private TemplateResolver resolver;
    private NotificationTemplateAdminServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(SysNotificationTemplateMapper.class);
        resolver = mock(TemplateResolver.class);
        ObjectProvider<TemplateResolver> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(resolver);
        service = new NotificationTemplateAdminServiceImpl(mapper, provider);
    }

    private static SysNotificationTemplate entity(String code) {
        SysNotificationTemplate e = new SysNotificationTemplate();
        e.setId(1L);
        e.setTenantId(0L);
        e.setTemplateCode(code);
        e.setModuleCode("crm");
        e.setName("客户跟进提醒");
        e.setDefaultChannels(List.of("STATION"));
        e.setContents(Map.of("STATION", Map.of("title", "标题", "body", "正文")));
        e.setPriority("NORMAL");
        e.setStatus("active");
        return e;
    }

    @Test
    void create_forces_station_and_evicts_cache() {
        when(mapper.selectOne(any())).thenReturn(null, entity("crm.follow_reminder"));
        CreateTemplateCommand cmd = new CreateTemplateCommand(
                "crm.follow_reminder", "crm", "客户跟进提醒", "desc",
                List.of(), List.of("EMAIL"),
                Map.of("STATION", new ChannelContent("站内标题", "站内正文"),
                        "EMAIL", new ChannelContent("邮件主题", "邮件正文")),
                null, null, null);

        TemplateDetail detail = service.createTemplate(cmd);
        assertThat(detail.templateCode()).isEqualTo("crm.follow_reminder");

        ArgumentCaptor<SysNotificationTemplate> captor = ArgumentCaptor.forClass(SysNotificationTemplate.class);
        verify(mapper).insert(captor.capture());
        SysNotificationTemplate saved = captor.getValue();
        assertThat(saved.getTenantId()).isZero();
        assertThat(saved.getDefaultChannels()).containsExactly("STATION", "EMAIL"); // STATION 保底置首
        assertThat(saved.getStatus()).isEqualTo("active");
        verify(resolver).evictCodeAllTenants("crm.follow_reminder");
    }

    @Test
    void create_empty_body_rejected() {
        when(mapper.selectOne(any())).thenReturn(null);
        CreateTemplateCommand cmd = new CreateTemplateCommand(
                "crm.x", "crm", "n", null, List.of(), List.of(),
                Map.of("STATION", new ChannelContent("有标题", "  ")),
                null, null, null);
        assertThatThrownBy(() -> service.createTemplate(cmd))
                .isInstanceOf(NotificationTemplateAdminException.class)
                .extracting(e -> ((NotificationTemplateAdminException) e).code())
                .isEqualTo(NotificationTemplateAdminException.Code.EMPTY_CONTENT);
        verify(mapper, never()).insert(any(SysNotificationTemplate.class));
    }

    @Test
    void create_conflict_rejected() {
        when(mapper.selectOne(any())).thenReturn(entity("crm.dup"));
        CreateTemplateCommand cmd = new CreateTemplateCommand(
                "crm.dup", "crm", "n", null, List.of(), List.of(),
                Map.of("STATION", new ChannelContent("t", "b")), null, null, null);
        assertThatThrownBy(() -> service.createTemplate(cmd))
                .isInstanceOf(NotificationTemplateAdminException.class)
                .extracting(e -> ((NotificationTemplateAdminException) e).code())
                .isEqualTo(NotificationTemplateAdminException.Code.CODE_CONFLICT);
    }

    @Test
    void create_invalid_channel_rejected() {
        when(mapper.selectOne(any())).thenReturn(null);
        CreateTemplateCommand cmd = new CreateTemplateCommand(
                "crm.x", "crm", "n", null, List.of(), List.of("FAX"),
                Map.of("STATION", new ChannelContent("t", "b")), null, null, null);
        assertThatThrownBy(() -> service.createTemplate(cmd))
                .isInstanceOf(NotificationTemplateAdminException.class)
                .extracting(e -> ((NotificationTemplateAdminException) e).code())
                .isEqualTo(NotificationTemplateAdminException.Code.INVALID_CHANNEL);
    }

    @Test
    void sys_template_protected_on_update_disable_delete() {
        UpdateTemplateCommand upd = new UpdateTemplateCommand(
                "x", null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateTemplate("sys.task.completed", upd))
                .isInstanceOf(NotificationTemplateAdminException.class)
                .extracting(e -> ((NotificationTemplateAdminException) e).code())
                .isEqualTo(NotificationTemplateAdminException.Code.SYS_TEMPLATE_PROTECTED);
        assertThatThrownBy(() -> service.disable("sys.task.completed"))
                .isInstanceOf(NotificationTemplateAdminException.class);
        assertThatThrownBy(() -> service.deleteTemplate("sys.task.completed"))
                .isInstanceOf(NotificationTemplateAdminException.class);
        verify(mapper, never()).updateById(any(SysNotificationTemplate.class));
        verify(mapper, never()).deleteById(any(Long.class));
    }

    @Test
    void update_not_found_throws() {
        when(mapper.selectOne(any())).thenReturn(null);
        UpdateTemplateCommand upd = new UpdateTemplateCommand(
                "新名", null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateTemplate("crm.ghost", upd))
                .isInstanceOf(NotificationTemplateAdminException.class)
                .extracting(e -> ((NotificationTemplateAdminException) e).code())
                .isEqualTo(NotificationTemplateAdminException.Code.TEMPLATE_NOT_FOUND);
    }
}
