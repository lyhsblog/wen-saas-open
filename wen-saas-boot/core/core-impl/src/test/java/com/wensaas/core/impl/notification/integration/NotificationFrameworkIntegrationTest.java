package com.wensaas.core.impl.notification.integration;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.MessageTaskId;
import com.wensaas.core.api.notification.send.MessageTaskStatus;
import com.wensaas.core.api.notification.send.NotificationException;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.notification.delivery.MessageDeliveryRunner;
import com.wensaas.core.impl.notification.delivery.MessageEnvelope;
import com.wensaas.core.impl.notification.delivery.NotificationPublisher;
import com.wensaas.core.impl.notification.template.TemplateResolver;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.wensaas.core.impl.notification.adapter.StationChannelAdapter;

/**
 * 通知模块端到端集成测试（真 PostgreSQL + 真 Redis；跳过 MQ 链路）。
 *
 * <p>Publisher 注入 noop @Primary，捕获 envelope 后直接交给 {@link MessageDeliveryRunner}
 * 同步处理；与生产消费者链路一致（仅去掉 RocketMQ 来回）。
 *
 * <p>覆盖：模板解析（含 tenant_id=0 默认 + tenant 定制覆盖）/ 收件人解析 / 通道决议 / 站内信兜底 /
 * StationChannelAdapter 写表 / 模板未找到 / 收件人解析空 / 缓存命中。
 */
@SpringBootTest(classes = {TestApp.class, NotificationFrameworkIntegrationTest.TestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "wen-saas-boot.notification.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationFrameworkIntegrationTest {

    private static final long TENANT_A = 9801L;
    private static final long USER_A1 = 980_101L;
    private static final long USER_A2 = 980_102L;

    @Autowired JdbcTemplate jdbc;
    @Autowired MessageService messageService;
    @Autowired MessageDeliveryRunner runner;
    @Autowired TemplateResolver templateResolver;
    @Autowired CapturingPublisher capturing;

    @BeforeAll
    void seed() {
        // 表结构由单一基线迁移 V1.0.0 保证（测试连已迁移的 wen_saas 库，flyway.enabled=false）。
        cleanup();
        // TenantActivityGuard 需要 sys_tenant 存在且 status=1
        for (long tid : new long[]{TENANT_A, 9802L, 9803L}) {
            jdbc.update("""
                    INSERT INTO sys_tenant (id, code, name, status, created_at, updated_at)
                    VALUES (?, ?, ?, 1, now(), now())
                    ON CONFLICT (id) DO UPDATE SET status = 1""",
                    tid, "test-notify-" + tid, "Notify Test " + tid);
        }
        // 业务方默认模板（tenant_id=0）
        insertTemplate(0L, "crm.welcome", "crm", "客户欢迎",
                List.of("STATION", "EMAIL"),
                Map.of(
                        "STATION", Map.of("title", "欢迎 {name}", "body", "{name}，欢迎使用本系统"),
                        "EMAIL", Map.of("title", "欢迎 {name}", "body", "{name}，邮件正文")),
                "NORMAL");
        // 业务方独占模板（只 station）
        insertTemplate(0L, "sys.test_station", "sys", "测试站内信",
                List.of("STATION"),
                Map.of("STATION", Map.of("title", "title-{x}", "body", "body-{x}")),
                "NORMAL");
        // sys_user：A 租户 2 个用户 + 1 个 disabled
        insertUser(USER_A1, TENANT_A, "u1@example.com", "13000000001", 1);
        insertUser(USER_A2, TENANT_A, "u2@example.com", "13000000002", 1);
        insertUser(USER_A2 + 1, TENANT_A, "u3@example.com", null, 0); // disabled
    }

    @AfterAll
    void teardown() {
        cleanup();
    }

    @BeforeEach
    void resetCapture() {
        capturing.clear();
        // 清掉 Redis 缓存避免上一个 case 残留
        templateResolver.evictAll();
        // 清掉上一个 case 的消息数据（不动 sys_user / sys_notification_template seed）
        jdbc.update("DELETE FROM sys_station_message WHERE tenant_id IN (?, ?, ?)", TENANT_A, 9802L, 9803L);
        jdbc.update("DELETE FROM sys_message_task WHERE tenant_id IN (?, ?, ?)", TENANT_A, 9802L, 9803L);
    }

    @Test
    void send_station_only_writesStationMessage() {
        MessageTaskId id = TenantContext.callAs(TENANT_A, () -> messageService.send(
                MessageSendRequest.builder("sys.test_station")
                        .recipients(List.of(RecipientSpec.member(USER_A1)))
                        .variables(Map.of("x", "v1"))
                        .build()));
        assertThat(id.value()).startsWith("nt-");
        // flushAllTo 不在 runAs 内 —— runner 自管 TenantContext
        capturing.flushAllTo(runner);

        TenantContext.runAs(TENANT_A, () -> {
            Optional<MessageTaskStatus> status = messageService.status(id);
            assertThat(status).isPresent();
            assertThat(status.get().sent()).isEqualTo(1);
            assertThat(status.get().pending()).isZero();
        });

        // jdbc 直查 sys_station_message
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_station_message WHERE tenant_id=? AND recipient_id=?",
                Integer.class, TENANT_A, USER_A1);
        assertThat(cnt).isEqualTo(1);
        String title = jdbc.queryForObject(
                "SELECT title FROM sys_station_message WHERE tenant_id=? AND recipient_id=? ORDER BY id DESC LIMIT 1",
                String.class, TENANT_A, USER_A1);
        assertThat(title).isEqualTo("title-v1");
    }

    @Test
    void send_tenantPref_overridesChannels() {
        // 租户 A 偏好：crm.welcome 仅走 STATION（关掉 EMAIL）；文案仍取平台默认
        insertPref(TENANT_A, "crm.welcome", true, List.of("STATION"), null);
        templateResolver.evict(TENANT_A, "crm.welcome");
        try {
            MessageTaskId id = TenantContext.callAs(TENANT_A, () -> messageService.send(
                    MessageSendRequest.builder("crm.welcome")
                            .recipients(List.of(RecipientSpec.member(USER_A1)))
                            .variables(Map.of("name", "Alice"))
                            .build()));
            capturing.flushAllTo(runner);

            // 偏好仅 STATION → 应只 1 条 task
            TenantContext.runAs(TENANT_A, () -> {
                MessageTaskStatus s = messageService.status(id).orElseThrow();
                assertThat(s.total()).isEqualTo(1);
                assertThat(s.entries()).extracting(MessageTaskStatus.Entry::channel)
                        .containsExactly(Channel.STATION);
            });

            // 文案来自平台默认（非定制文案）
            String title = jdbc.queryForObject(
                    "SELECT title FROM sys_station_message WHERE tenant_id=? AND recipient_id=? AND template_code='crm.welcome' ORDER BY id DESC LIMIT 1",
                    String.class, TENANT_A, USER_A1);
            assertThat(title).isEqualTo("欢迎 Alice");
        } finally {
            jdbc.update("DELETE FROM sys_notification_template_pref WHERE tenant_id=? AND template_code='crm.welcome'",
                    TENANT_A);
            templateResolver.evict(TENANT_A, "crm.welcome");
        }
    }

    @Test
    void send_tenantPref_disabled_suppressesAll() {
        // 租户 A 停用 crm.welcome → 完全不发（含站内信兜底）
        insertPref(TENANT_A, "crm.welcome", false, null, null);
        templateResolver.evict(TENANT_A, "crm.welcome");
        try {
            MessageTaskId id = TenantContext.callAs(TENANT_A, () -> messageService.send(
                    MessageSendRequest.builder("crm.welcome")
                            .recipients(List.of(RecipientSpec.member(USER_A1)))
                            .variables(Map.of("name", "Alice"))
                            .build()));
            capturing.flushAllTo(runner);

            TenantContext.runAs(TENANT_A, () ->
                    assertThat(messageService.status(id)).isEmpty());
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_station_message WHERE tenant_id=? AND recipient_id=? AND template_code='crm.welcome'",
                    Integer.class, TENANT_A, USER_A1);
            assertThat(cnt).isZero();
        } finally {
            jdbc.update("DELETE FROM sys_notification_template_pref WHERE tenant_id=? AND template_code='crm.welcome'",
                    TENANT_A);
            templateResolver.evict(TENANT_A, "crm.welcome");
        }
    }

    @Test
    void send_platformDefault_when_noTenantOverride() {
        long otherTenant = 9802L;
        insertUser(980_201L, otherTenant, "x@example.com", null, 1);
        try {
            MessageTaskId id = TenantContext.callAs(otherTenant, () -> messageService.send(
                    MessageSendRequest.builder("crm.welcome")
                            .recipients(List.of(RecipientSpec.member(980_201L)))
                            .variables(Map.of("name", "Bob"))
                            .build()));
            capturing.flushAllTo(runner);

            // 平台默认有 STATION + EMAIL，并由 noStationFallback=false 自动加 STATION 但已存在 → 2 条
            TenantContext.runAs(otherTenant, () -> {
                MessageTaskStatus s = messageService.status(id).orElseThrow();
                assertThat(s.total()).isEqualTo(2);
                assertThat(s.entries()).extracting(MessageTaskStatus.Entry::channel)
                        .containsExactlyInAnyOrder(Channel.STATION, Channel.EMAIL);
            });

            String title = jdbc.queryForObject(
                    "SELECT title FROM sys_station_message WHERE tenant_id=? AND recipient_id=? AND template_code='crm.welcome' ORDER BY id DESC LIMIT 1",
                    String.class, otherTenant, 980_201L);
            assertThat(title).isEqualTo("欢迎 Bob");
        } finally {
            jdbc.update("DELETE FROM sys_user WHERE id=?", 980_201L);
        }
    }

    @Test
    void send_template_not_found_throws() {
        TenantContext.runAs(TENANT_A, () -> {
            MessageSendRequest req = MessageSendRequest.builder("missing.template")
                    .recipients(List.of(RecipientSpec.member(USER_A1)))
                    .build();
            assertThatThrownBy(() -> messageService.send(req))
                    .isInstanceOf(NotificationException.class)
                    .extracting("code").isEqualTo(NotificationException.Code.TEMPLATE_NOT_FOUND);
        });
    }

    @Test
    void send_disabled_user_filteredOut_no_recipient_throws() {
        TenantContext.runAs(TENANT_A, () -> {
            MessageSendRequest req = MessageSendRequest.builder("sys.test_station")
                    .recipients(List.of(RecipientSpec.member(USER_A2 + 1)))  // disabled user
                    .build();
            assertThatThrownBy(() -> messageService.send(req))
                    .isInstanceOf(NotificationException.class)
                    .extracting("code").isEqualTo(NotificationException.Code.NO_RECIPIENT_RESOLVED);
        });
    }

    @Test
    void cache_hit_does_not_query_db_again() {
        TenantContext.runAs(TENANT_A, () -> {
            // 先 send 一次让缓存 warm（cache key 由调用方 tenantId 编出，与 DB row 的 tenant_id 不同）
            messageService.send(MessageSendRequest.builder("sys.test_station")
                    .recipients(List.of(RecipientSpec.member(USER_A1)))
                    .variables(Map.of("x", "1"))
                    .build());
            // 直接改 DB 模板正文（不 evict）→ 第二次 send 应仍用旧值
            jdbc.update("UPDATE sys_notification_template SET contents = ?::jsonb WHERE tenant_id=0 AND template_code='sys.test_station'",
                    "{\"STATION\":{\"title\":\"NEW-{x}\",\"body\":\"new-body-{x}\"}}");
            capturing.clear();
            messageService.send(MessageSendRequest.builder("sys.test_station")
                    .recipients(List.of(RecipientSpec.member(USER_A1)))
                    .variables(Map.of("x", "2"))
                    .build());
            assertThat(capturing.last().title()).isEqualTo("title-2"); // 仍是旧文案

            // 手动 evict 后才看到新版本：cache key 是 (TENANT_A, code)，不是 (0, code)
            templateResolver.evict(TENANT_A, "sys.test_station");
            capturing.clear();
            messageService.send(MessageSendRequest.builder("sys.test_station")
                    .recipients(List.of(RecipientSpec.member(USER_A1)))
                    .variables(Map.of("x", "3"))
                    .build());
            assertThat(capturing.last().title()).isEqualTo("NEW-3");
        });
        // 还原模板（在 runAs 之外，避免下个 case 还原失败）
        jdbc.update("UPDATE sys_notification_template SET contents = ?::jsonb WHERE tenant_id=0 AND template_code='sys.test_station'",
                "{\"STATION\":{\"title\":\"title-{x}\",\"body\":\"body-{x}\"}}");
        templateResolver.evict(TENANT_A, "sys.test_station");
    }

    @Test
    void cross_tenant_recipient_filtered_out() {
        long otherTenant = 9803L;
        long otherUser = 980_301L;
        insertUser(otherUser, otherTenant, "z@x", null, 1);
        TenantContext.runAs(TENANT_A, () -> {
            MessageSendRequest req = MessageSendRequest.builder("sys.test_station")
                    .recipients(List.of(RecipientSpec.member(otherUser)))
                    .build();
            // 跨租户 user 在 TENANT_A 视角下不可见（MP TenantLine 过滤），抛 NO_RECIPIENT_RESOLVED
            assertThatThrownBy(() -> messageService.send(req))
                    .isInstanceOf(NotificationException.class)
                    .extracting("code").isEqualTo(NotificationException.Code.NO_RECIPIENT_RESOLVED);
        });
        jdbc.update("DELETE FROM sys_user WHERE id=?", otherUser);
    }

    // -------------------------------------------------------------------------------
    // 测试基础设施
    // -------------------------------------------------------------------------------

    void cleanup() {
        jdbc.update("DELETE FROM sys_station_message WHERE tenant_id IN (?, ?, ?)", TENANT_A, 9802L, 9803L);
        jdbc.update("DELETE FROM sys_message_task WHERE tenant_id IN (?, ?, ?)", TENANT_A, 9802L, 9803L);
        jdbc.update("DELETE FROM sys_notification_template_pref WHERE tenant_id IN (?, ?, ?)", TENANT_A, 9802L, 9803L);
        jdbc.update("DELETE FROM sys_notification_template WHERE template_code IN ('crm.welcome','sys.test_station')");
        jdbc.update("DELETE FROM sys_user WHERE id IN (?,?,?,?,?)",
                USER_A1, USER_A2, USER_A2 + 1, 980_201L, 980_301L);
        jdbc.update("DELETE FROM sys_tenant WHERE id IN (?, ?, ?)", TENANT_A, 9802L, 9803L);
    }

    void insertTemplate(long tenantId, String code, String moduleCode, String name,
                         List<String> channels, Map<String, Map<String, String>> contents,
                         String priority) {
        jdbc.update("""
                INSERT INTO sys_notification_template
                    (id, tenant_id, template_code, module_code, name, available_vars,
                     default_channels, contents, priority, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, 'active', now(), now())""",
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),
                tenantId, code, moduleCode, name,
                "[]",
                JSON.toJSONString(channels),
                JSON.toJSONString(contents),
                priority);
    }

    void insertPref(long tenantId, String code, boolean enabled, List<String> channels, String priority) {
        jdbc.update("""
                INSERT INTO sys_notification_template_pref
                    (id, tenant_id, template_code, enabled, channels, priority, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, now(), now())""",
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),
                tenantId, code, enabled,
                channels == null ? null : JSON.toJSONString(channels),
                priority);
    }

    void insertUser(long id, long tenantId, String email, String mobile, int status) {
        jdbc.update("""
                INSERT INTO sys_user (id, tenant_id, username, password_hash, real_name,
                                       mobile, email, status, created_at, updated_at)
                VALUES (?, ?, ?, '$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxx', ?, ?, ?, ?, now(), now())""",
                id, tenantId, "user_" + id, "name_" + id, mobile, email, status);
    }

    /** 截获 envelope 不真发到 RocketMQ；测试驱动 runner 同步处理。 */
    static class CapturingPublisher implements NotificationPublisher {
        private final List<MessageEnvelope> buffer = new CopyOnWriteArrayList<>();
        @Override public void publish(MessageEnvelope envelope) { buffer.add(envelope); }
        @Override public void republish(MessageEnvelope envelope, long delayMillis) { buffer.add(envelope); }
        void clear() { buffer.clear(); }
        MessageEnvelope last() { return buffer.get(buffer.size() - 1); }
        void flushAllTo(MessageDeliveryRunner runner) {
            List<MessageEnvelope> snapshot = new ArrayList<>(buffer);
            buffer.clear();
            for (MessageEnvelope e : snapshot) {
                runner.handle(JSON.toJSONString(e));
            }
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        @Primary
        CapturingPublisher capturingPublisher() {
            return new CapturingPublisher();
        }
    }
}
