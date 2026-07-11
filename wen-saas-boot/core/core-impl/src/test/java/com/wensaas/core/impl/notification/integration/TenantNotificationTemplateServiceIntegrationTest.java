package com.wensaas.core.impl.notification.integration;

import com.wensaas.core.api.notification.template.TenantNotificationTemplateException;
import com.wensaas.core.api.notification.template.TenantNotificationTemplateService;
import com.wensaas.core.api.notification.template.TenantNotificationTemplateService.Priority;
import com.wensaas.core.api.notification.template.TenantNotificationTemplateService.TemplateDetail;
import com.wensaas.core.api.notification.template.TenantNotificationTemplateService.UpdatePrefCommand;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.notification.template.TemplateDataLoader;
import com.wensaas.core.impl.notification.template.TemplateSnapshot;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 租户通知模板偏好集成测试：启停 / 渠道 / 优先级 / 恢复默认 / sys.* 保护 / 停用静默。
 */
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "wen-saas-boot.notification.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantNotificationTemplateServiceIntegrationTest {

    private static final long TENANT_ID = 9751L;
    private static final String CODE = "test.demo_notify";
    private static final String SYS_CODE = "sys.demo_notify";

    @Autowired JdbcTemplate jdbc;
    @Autowired TenantNotificationTemplateService service;
    @Autowired TemplateDataLoader loader;

    @BeforeAll
    void setupClass() {
        cleanup();
    }

    @AfterAll
    void teardownClass() {
        cleanup();
        TenantContext.clear();
    }

    @BeforeEach
    void setUp() {
        cleanup();
        seedDefault(CODE, "active", "NORMAL");
        seedDefault(SYS_CODE, "active", "NORMAL");
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_notification_template_pref WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_notification_template WHERE template_code IN (?, ?)", CODE, SYS_CODE);
    }

    /** 平台默认模板：STATION + EMAIL 两通道有文案，默认通道 [STATION, EMAIL]。 */
    private void seedDefault(String code, String status, String priority) {
        jdbc.update("""
                INSERT INTO sys_notification_template
                  (id, tenant_id, template_code, module_code, name, description,
                   available_vars, default_channels, contents, notification_category, priority, status)
                VALUES (?, 0, ?, 'test', ?, '演示', '[]'::jsonb,
                   '["STATION","EMAIL"]'::jsonb,
                   '{"STATION":{"title":"标题","body":"正文 {name}"},"EMAIL":{"title":"主题","body":"邮件正文"}}'::jsonb,
                   NULL, ?, ?)
                """, System.nanoTime(), code, code, priority, status);
    }

    @Test
    void list_default_noPref() {
        List<TenantNotificationTemplateService.TemplateListItem> items = service.list("test", null);
        assertThat(items).extracting(TenantNotificationTemplateService.TemplateListItem::templateCode)
                .contains(CODE, SYS_CODE);
        var demo = items.stream().filter(i -> i.templateCode().equals(CODE)).findFirst().orElseThrow();
        assertThat(demo.enabled()).isTrue();
        assertThat(demo.customized()).isFalse();
        assertThat(demo.channels()).containsExactly("STATION", "EMAIL");
        assertThat(demo.priority()).isEqualTo(Priority.NORMAL);
        assertThat(demo.defaultActive()).isTrue();
    }

    @Test
    void disable_makesLoaderSuppressed() {
        service.updatePref(CODE, new UpdatePrefCommand(false, null, null));

        TemplateDetail d = service.get(CODE);
        assertThat(d.enabled()).isFalse();
        assertThat(d.customized()).isTrue();

        Optional<TemplateSnapshot> snap = loader.load(TENANT_ID, CODE);
        assertThat(snap).isPresent();
        assertThat(snap.get().suppressed()).isTrue();
        // 其它租户不受影响（无偏好 → 正常发送）
        assertThat(loader.load(9999L, CODE).orElseThrow().suppressed()).isFalse();
    }

    @Test
    void channelOverride_dropsEmail() {
        service.updatePref(CODE, new UpdatePrefCommand(true, List.of("STATION"), null));

        TemplateDetail d = service.get(CODE);
        assertThat(d.customized()).isTrue();
        assertThat(d.channels()).containsExactly("STATION");

        TemplateSnapshot snap = loader.load(TENANT_ID, CODE).orElseThrow();
        assertThat(snap.suppressed()).isFalse();
        assertThat(snap.defaultChannels()).extracting(Enum::name).containsExactly("STATION");
    }

    @Test
    void channelOverride_forcesStation() {
        // 即便只勾 EMAIL，也强制保留 STATION 保底
        service.updatePref(CODE, new UpdatePrefCommand(true, List.of("EMAIL"), null));
        TemplateDetail d = service.get(CODE);
        assertThat(d.channels()).containsExactly("STATION", "EMAIL");
    }

    @Test
    void priorityOverride() {
        service.updatePref(CODE, new UpdatePrefCommand(true, null, Priority.IMPORTANT));
        TemplateDetail d = service.get(CODE);
        assertThat(d.customized()).isTrue();
        assertThat(d.priority()).isEqualTo(Priority.IMPORTANT);
        assertThat(loader.load(TENANT_ID, CODE).orElseThrow().priority().name()).isEqualTo("IMPORTANT");
    }

    @Test
    void invalidChannel_notInDefaultContents() {
        assertThatThrownBy(() -> service.updatePref(CODE, new UpdatePrefCommand(true, List.of("SMS"), null)))
                .isInstanceOf(TenantNotificationTemplateException.class)
                .extracting(e -> ((TenantNotificationTemplateException) e).code())
                .isEqualTo(TenantNotificationTemplateException.Code.INVALID_CHANNEL);
    }

    @Test
    void allDefaultState_clearsPref() {
        service.updatePref(CODE, new UpdatePrefCommand(false, null, null));
        assertThat(service.get(CODE).customized()).isTrue();
        // 回到全默认（启用 + 无渠道覆盖 + 无优先级覆盖）→ 偏好行删除
        TemplateDetail d = service.updatePref(CODE, new UpdatePrefCommand(true, null, null));
        assertThat(d.customized()).isFalse();
        assertThat(loader.load(TENANT_ID, CODE).orElseThrow().suppressed()).isFalse();
    }

    @Test
    void resetPref_clearsCustomization() {
        service.updatePref(CODE, new UpdatePrefCommand(false, List.of("STATION"), Priority.IMPORTANT));
        assertThat(service.get(CODE).customized()).isTrue();
        TemplateDetail d = service.resetPref(CODE);
        assertThat(d.customized()).isFalse();
        assertThat(d.enabled()).isTrue();
        assertThat(d.channels()).containsExactly("STATION", "EMAIL");
        assertThat(d.priority()).isEqualTo(Priority.NORMAL);
    }

    @Test
    void sysTemplate_cannotDisable() {
        assertThatThrownBy(() -> service.updatePref(SYS_CODE, new UpdatePrefCommand(false, null, null)))
                .isInstanceOf(TenantNotificationTemplateException.class)
                .extracting(e -> ((TenantNotificationTemplateException) e).code())
                .isEqualTo(TenantNotificationTemplateException.Code.SYS_TEMPLATE_PROTECTED);
        // 但渠道 / 优先级定制允许
        TemplateDetail d = service.updatePref(SYS_CODE, new UpdatePrefCommand(true, null, Priority.IMPORTANT));
        assertThat(d.priority()).isEqualTo(Priority.IMPORTANT);
    }

    @Test
    void notFound_throws() {
        assertThatThrownBy(() -> service.get("test.nonexistent"))
                .isInstanceOf(TenantNotificationTemplateException.class)
                .extracting(e -> ((TenantNotificationTemplateException) e).code())
                .isEqualTo(TenantNotificationTemplateException.Code.TEMPLATE_NOT_FOUND);
    }
}
