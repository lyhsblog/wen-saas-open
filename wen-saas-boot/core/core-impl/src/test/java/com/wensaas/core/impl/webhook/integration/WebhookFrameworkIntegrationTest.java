package com.wensaas.core.impl.webhook.integration;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.event.DomainEventPublisher;
import com.wensaas.core.api.event.EventEnvelope;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.file.CredentialCipher;
import com.wensaas.core.impl.webhook.dispatch.WebhookDispatcherConsumer;
import com.wensaas.core.impl.webhook.dispatch.WebhookHttpClient;
import com.wensaas.core.impl.webhook.WebhookProperties;
import com.wensaas.core.impl.webhook.dispatch.WebhookSendEnvelope;
import com.wensaas.core.impl.webhook.dispatch.WebhookSenderConsumer;
import com.wensaas.core.impl.webhook.dispatch.WebhookSignatureSigner;
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
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import com.wensaas.core.impl.webhook.dispatch.RocketMqWebhookSendPublisher;

/**
 * Webhook 推送框架端到端集成测试（真 PostgreSQL + 真 Redis；不依赖 RocketMQ broker）。
 *
 * <p>三个 @Primary fixture 替换生产 publisher / HTTP client：
 * <ul>
 *   <li>{@link CapturingDomainEventPublisher} 替换 {@code RocketMqDomainEventPublisher}</li>
 *   <li>{@link CapturingWebhookSendPublisher} 替换 {@code RocketMqWebhookSendPublisher}（dispatcher 扇出收口）</li>
 *   <li>{@link RecordingWebhookHttpClient} 替换 {@code WebhookHttpClient}（HTTP POST 受控）</li>
 * </ul>
 *
 * <p>测试驱动模式：手工 {@code dispatcher.handle(event)} 或 {@code capturing.flushAllTo(sender)} 同步触发，
 * 完全绕过 RocketMQ broker。
 *
 * <p>覆盖 7 个场景：dispatcher 扇出匹配 / dispatcher 跨租户隔离 / sender 2xx 成功 + 签名正确 /
 * sender 5xx 重试至成功 / sender 重试穷尽 abandoned / sender 跳过 inactive webhook /
 * 端到端 DomainEvent → dispatcher → sender。
 */
@SpringBootTest(classes = {TestApp.class, WebhookFrameworkIntegrationTest.TestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "wen-saas-boot.event.enabled=true",
        "wen-saas-boot.webhook.enabled=true",
        "wen-saas-boot.webhook.retry-times=3",
        "wen-saas-boot.webhook.retry-backoff=PT0S,PT0S,PT0S",
        "wen-saas-boot.openapi.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebhookFrameworkIntegrationTest {

    private static final long TENANT_A = 9601L;
    private static final long TENANT_B = 9602L;
    private static final String URL_A1 = "http://example.test/hook/a1";
    private static final String URL_A2 = "http://example.test/hook/a2";
    private static final String URL_A_OTHER = "http://example.test/hook/a-other";
    private static final String URL_B = "http://example.test/hook/b";
    private static final String URL_DISABLED = "http://example.test/hook/disabled";
    private static final String URL_RETRY = "http://example.test/hook/retry";
    private static final String URL_ABANDON = "http://example.test/hook/abandon";
    private static final String URL_E2E = "http://example.test/hook/e2e";

    @Autowired JdbcTemplate jdbc;
    @Autowired CredentialCipher cipher;
    @Autowired WebhookSignatureSigner signer;
    @Autowired WebhookDispatcherConsumer dispatcher;
    @Autowired WebhookSenderConsumer sender;
    @Autowired WebhookHttpClient httpClient;
    @Autowired CapturingWebhookSendPublisher sendPublisher;
    @Autowired CapturingDomainEventPublisher eventPublisher;
    @Autowired DomainEventPublisher domainEventPublisherBeanCheck;
    @Autowired WebhookProperties props;

    private RecordingWebhookHttpClient recording;

    @BeforeAll
    void seedAll() {
        // 表结构由单一基线迁移 V1.0.0 保证（测试连已迁移的 wen_saas 库，flyway.enabled=false）。
        cleanup();
        recording = (RecordingWebhookHttpClient) httpClient;
        // 验证 @Primary 替换确实生效（防止后续行为差异难排查）
        assertThat(domainEventPublisherBeanCheck).isInstanceOf(CapturingDomainEventPublisher.class);

        // TenantActivityGuard 需要 sys_tenant 存在且 status=1
        for (long tid : new long[]{TENANT_A, TENANT_B}) {
            jdbc.update("""
                    INSERT INTO sys_tenant (id, code, name, status, created_at, updated_at)
                    VALUES (?, ?, ?, 1, now(), now())
                    ON CONFLICT (id) DO UPDATE SET status = 1""",
                    tid, "test-webhook-" + tid, "Webhook Test " + tid);
        }

        // 平台 + 租户 webhooks
        // tenantA：2 个订阅 crm.customer_created + 1 个订阅别的
        long idA1 = insertWebhook(TENANT_A, URL_A1, "secret-a1",
                List.of("crm.customer_created", "crm.customer_updated"), "active");
        long idA2 = insertWebhook(TENANT_A, URL_A2, "secret-a2",
                List.of("crm.customer_created"), "active");
        long idAOther = insertWebhook(TENANT_A, URL_A_OTHER, "secret-other",
                List.of("crm.customer_deleted"), "active");
        // tenantB：1 个订阅 crm.customer_created
        long idB = insertWebhook(TENANT_B, URL_B, "secret-b",
                List.of("crm.customer_created"), "active");
        // tenantA：1 个 disabled
        long idDisabled = insertWebhook(TENANT_A, URL_DISABLED, "secret-dis",
                List.of("crm.customer_created"), "disabled");
        // tenantA：retry 测试专用
        long idRetry = insertWebhook(TENANT_A, URL_RETRY, "secret-retry",
                List.of("crm.retry_test"), "active");
        long idAbandon = insertWebhook(TENANT_A, URL_ABANDON, "secret-abandon",
                List.of("crm.abandon_test"), "active");
        long idE2E = insertWebhook(TENANT_A, URL_E2E, "secret-e2e",
                List.of("crm.e2e_test"), "active");
    }

    @AfterAll
    void teardown() {
        cleanup();
    }

    @BeforeEach
    void resetEach() {
        recording.clear();
        sendPublisher.clear();
        eventPublisher.clear();
        jdbc.update("DELETE FROM sys_webhook_log WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
    }

    // -------------------------------------------------------------------------------
    // 7 个测试用例
    // -------------------------------------------------------------------------------

    @Test
    void dispatcher_fanOutToMatchingWebhooks() {
        EventEnvelope event = new EventEnvelope(
                TENANT_A, "crm.customer_created", "evt-1",
                Instant.now(), Map.of("customerId", 1001), "req-1");
        dispatcher.handle(event);

        // tenantA 有 2 个 active 订阅 crm.customer_created
        assertThat(sendPublisher.size()).isEqualTo(2);
        assertThat(sendPublisher.snapshot()).allSatisfy(env -> {
            assertThat(env.tenantId()).isEqualTo(TENANT_A);
            assertThat(env.attempt()).isEqualTo(1);
            assertThat(env.event().eventId()).isEqualTo("evt-1");
        });
    }

    @Test
    void dispatcher_crossTenantIsolation() {
        // 发 tenantA 的事件，tenantB 的 webhook 不能被触发
        EventEnvelope event = new EventEnvelope(
                TENANT_A, "crm.customer_created", "evt-cross",
                Instant.now(), Map.of(), "req-cross");
        dispatcher.handle(event);
        assertThat(sendPublisher.snapshot()).noneSatisfy(env -> {
            // 没有任何 envelope 指向 tenantB 的 webhook
            assertThat(env.tenantId()).isEqualTo(TENANT_B);
        });
        // 反向：tenantB 的事件不触发 tenantA
        sendPublisher.clear();
        EventEnvelope eventB = new EventEnvelope(
                TENANT_B, "crm.customer_created", "evt-cross-2",
                Instant.now(), Map.of(), "req-cross-2");
        dispatcher.handle(eventB);
        assertThat(sendPublisher.size()).isEqualTo(1);
        assertThat(sendPublisher.snapshot().get(0).tenantId()).isEqualTo(TENANT_B);
    }

    @Test
    void sender_http2xx_writesSuccessLog_withCorrectSignature() {
        long webhookId = lookupId(TENANT_A, URL_A1);
        recording.onUrl(URL_A1, 200, "{\"received\":true}");
        EventEnvelope event = new EventEnvelope(
                TENANT_A, "crm.customer_created", "evt-success",
                Instant.now(), Map.of("k", "v"), "req-success");
        WebhookSendEnvelope envelope = new WebhookSendEnvelope(webhookId, TENANT_A, "req-success", 1, event);

        sender.handle(envelope);

        // RecordingHttpClient 收到了一次调用
        assertThat(recording.callCount(URL_A1)).isEqualTo(1);
        RecordingWebhookHttpClient.Call call = recording.getCalls().get(0);
        // 签名头存在且能用 plainSecret 重算通过
        String sig = call.headers().get(props.getSignatureHeader());
        String ts = call.headers().get(props.getTimestampHeader());
        String evt = call.headers().get(props.getEventHeader());
        assertThat(sig).isNotBlank();
        assertThat(ts).isNotBlank();
        assertThat(evt).isEqualTo("crm.customer_created");
        String expected = signer.sign("secret-a1", "crm.customer_created", Long.parseLong(ts), call.body());
        assertThat(sig).isEqualTo(expected);

        // sys_webhook_log 1 行成功
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT http_status, final_status, retry_count, duration_ms, event_id, event_type "
                        + "FROM sys_webhook_log WHERE tenant_id=? AND webhook_id=? ORDER BY id DESC LIMIT 1",
                TENANT_A, webhookId);
        assertThat(row.get("http_status")).isEqualTo(200);
        assertThat(row.get("final_status")).isEqualTo("success");
        assertThat(row.get("retry_count")).isEqualTo(0);
        assertThat((Integer) row.get("duration_ms")).isGreaterThanOrEqualTo(0);
        assertThat(row.get("event_id")).isEqualTo("evt-success");
        assertThat(row.get("event_type")).isEqualTo("crm.customer_created");
    }

    @Test
    void sender_http5xx_retriesUntilSuccess() {
        long webhookId = lookupId(TENANT_A, URL_RETRY);
        // 前 2 次 502，第 3 次 200
        recording.onUrlSequence(URL_RETRY, n -> n <= 2
                ? new WebhookHttpClient.Response(502, "Bad Gateway", new HttpHeaders())
                : new WebhookHttpClient.Response(200, "{\"ok\":true}", new HttpHeaders()));
        EventEnvelope event = new EventEnvelope(
                TENANT_A, "crm.retry_test", "evt-retry",
                Instant.now(), Map.of(), "req-retry");
        WebhookSendEnvelope envelope = new WebhookSendEnvelope(webhookId, TENANT_A, "req-retry", 1, event);

        sendPublisher.publish(envelope);
        sendPublisher.flushAllTo(sender);

        assertThat(recording.callCount(URL_RETRY)).isEqualTo(3);
        // 应有 3 行 log：2 行 failed + 1 行 success
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT http_status, final_status, retry_count "
                        + "FROM sys_webhook_log WHERE tenant_id=? AND webhook_id=? ORDER BY retry_count ASC",
                TENANT_A, webhookId);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("retry_count", 0).containsEntry("final_status", "failed");
        assertThat(rows.get(1)).containsEntry("retry_count", 1).containsEntry("final_status", "failed");
        assertThat(rows.get(2)).containsEntry("retry_count", 2).containsEntry("final_status", "success")
                .containsEntry("http_status", 200);
    }

    @Test
    void sender_exhaustedRetries_writesAbandoned() {
        long webhookId = lookupId(TENANT_A, URL_ABANDON);
        // 全部 500，从 attempt=1 一路重试到 retry-times=3 后 abandoned
        recording.onUrl(URL_ABANDON, 500, "boom");
        EventEnvelope event = new EventEnvelope(
                TENANT_A, "crm.abandon_test", "evt-abandon",
                Instant.now(), Map.of(), "req-abandon");
        WebhookSendEnvelope envelope = new WebhookSendEnvelope(webhookId, TENANT_A, "req-abandon", 1, event);

        sendPublisher.publish(envelope);
        sendPublisher.flushAllTo(sender);

        // 3 次都是 500：attempt=1,2 写 failed + retry；attempt=3 写 abandoned，无 retry
        assertThat(recording.callCount(URL_ABANDON)).isEqualTo(3);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT final_status, retry_count, error_message FROM sys_webhook_log "
                        + "WHERE tenant_id=? AND webhook_id=? ORDER BY retry_count ASC",
                TENANT_A, webhookId);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("final_status", "failed");
        assertThat(rows.get(1)).containsEntry("final_status", "failed");
        assertThat(rows.get(2)).containsEntry("final_status", "abandoned").containsEntry("retry_count", 2);
        assertThat((String) rows.get(2).get("error_message")).contains("HTTP 500");
    }

    @Test
    void sender_skipsInactiveWebhook() {
        long webhookId = lookupId(TENANT_A, URL_DISABLED);
        recording.onUrl(URL_DISABLED, 200, "");
        EventEnvelope event = new EventEnvelope(
                TENANT_A, "crm.customer_created", "evt-disabled",
                Instant.now(), Map.of(), "req-disabled");
        WebhookSendEnvelope envelope = new WebhookSendEnvelope(webhookId, TENANT_A, "req-disabled", 1, event);

        sender.handle(envelope);

        // 不发 HTTP，不写日志
        assertThat(recording.callCount(URL_DISABLED)).isZero();
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_webhook_log WHERE tenant_id=? AND webhook_id=?",
                Integer.class, TENANT_A, webhookId);
        assertThat(cnt).isZero();
    }

    @Test
    void sender_duplicateDelivery_isIdempotent_singleHttpAndLog() {
        // M1 幂等去重：同一条消息（webhookId+eventId+attempt）被 MQ 重复投递，只处理一次。
        long webhookId = lookupId(TENANT_A, URL_A2);
        recording.onUrl(URL_A2, 200, "{\"ok\":true}");
        // 用唯一 eventId 避免与其它用例/历史 Redis 去重键冲突
        String eventId = "evt-dup-" + IdWorker.getId();
        EventEnvelope event = new EventEnvelope(
                TENANT_A, "crm.customer_created", eventId,
                Instant.now(), Map.of("k", "v"), "req-dup");
        WebhookSendEnvelope envelope = new WebhookSendEnvelope(webhookId, TENANT_A, "req-dup", 1, event);

        sender.handle(envelope);   // 首次：处理
        sender.handle(envelope);   // 重投：判重跳过

        // 只发了一次 HTTP
        assertThat(recording.callCount(URL_A2)).isEqualTo(1);
        // 只写了一行 log
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_webhook_log WHERE tenant_id=? AND webhook_id=? AND event_id=?",
                Integer.class, TENANT_A, webhookId, eventId);
        assertThat(cnt).isEqualTo(1);
    }

    @Test
    void endToEnd_publishEvent_drivesDispatcherAndSender() {
        long webhookId = lookupId(TENANT_A, URL_E2E);
        recording.onUrl(URL_E2E, 200, "{\"ok\":true}");
        // 业务方走最高层 API 发事件
        TenantContext.runAs(TENANT_A, () -> eventPublisher.publish(
                "crm.e2e_test",
                Map.of("orderId", 8801L)));
        assertThat(eventPublisher.size()).isEqualTo(1);

        // 模拟 MQ：CapturingDomainEventPublisher → dispatcher
        eventPublisher.flushAllTo(dispatcher);
        // dispatcher fanOut 出 1 个 envelope（只有 URL_E2E 订阅 crm.e2e_test）→ sender
        assertThat(sendPublisher.size()).isEqualTo(1);
        sendPublisher.flushAllTo(sender);

        // sys_webhook_log 1 行 success
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT http_status, final_status, event_type "
                        + "FROM sys_webhook_log WHERE tenant_id=? AND webhook_id=? ORDER BY id DESC LIMIT 1",
                TENANT_A, webhookId);
        assertThat(row.get("http_status")).isEqualTo(200);
        assertThat(row.get("final_status")).isEqualTo("success");
        assertThat(row.get("event_type")).isEqualTo("crm.e2e_test");
    }

    // -------------------------------------------------------------------------------
    // 测试基础设施
    // -------------------------------------------------------------------------------

    void cleanup() {
        jdbc.update("DELETE FROM sys_webhook_log WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbc.update("DELETE FROM sys_webhook WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbc.update("DELETE FROM sys_tenant WHERE id IN (?, ?)", TENANT_A, TENANT_B);
    }

    long insertWebhook(long tenantId, String url, String plainSecret,
                        List<String> events, String status) {
        long id = IdWorker.getId();
        String secretEnc = cipher.encrypt(plainSecret);
        jdbc.update("""
                        INSERT INTO sys_webhook
                            (id, tenant_id, name, target_url, signing_secret_encrypted,
                             subscribed_events, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, now(), now())""",
                id, tenantId, "test-" + tenantId, url, secretEnc,
                JSON.toJSONString(events), status);
        return id;
    }

    long lookupId(long tenantId, String url) {
        return jdbc.queryForObject(
                "SELECT id FROM sys_webhook WHERE tenant_id=? AND target_url=?",
                Long.class, tenantId, url);
    }

    // -------------------------------------------------------------------------------
    // Fixture Configuration（三个 @Primary 替换生产 bean）
    // -------------------------------------------------------------------------------

    @Configuration
    static class TestConfig {

        @Bean
        @Primary
        WebhookHttpClient recordingWebhookHttpClient(WebhookProperties props) {
            return new RecordingWebhookHttpClient(props);
        }

        @Bean
        @Primary
        CapturingWebhookSendPublisher capturingWebhookSendPublisher() {
            return new CapturingWebhookSendPublisher();
        }

        @Bean
        @Primary
        CapturingDomainEventPublisher capturingDomainEventPublisher() {
            return new CapturingDomainEventPublisher();
        }
    }
}
