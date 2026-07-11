package com.wensaas.core.impl.openapi.integration;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.file.CredentialCipher;
import com.wensaas.core.impl.openapi.calllog.ApiCallBatchBuffer;
import com.wensaas.core.impl.openapi.calllog.ApiCallEvent;
import com.wensaas.core.impl.openapi.calllog.ApiCallEventPublisher;
import com.wensaas.core.impl.openapi.calllog.ApiCallLogFilter;
import com.wensaas.core.impl.openapi.gateway.AppKeyAuthenticationFilter;
import com.wensaas.core.impl.openapi.gateway.HmacSignatureVerifier;
import com.wensaas.core.impl.request.RequestIdFilter;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 开放 API 框架端到端集成测试（真 PostgreSQL + 真 Redis；不依赖 RocketMQ broker）。
 *
 * <p>MockMvc standaloneSetup 装上 {@link ApiCallLogFilter}（外）+ {@link AppKeyAuthenticationFilter}（内）
 * 走完整 Filter 链；ApiCallEventPublisher 注入 {@code @Primary CapturingApiCallEventPublisher}
 * 捕获 ApiCallEvent，{@code flushAllTo(buffer)} 后手工 {@code buffer.flushNow()} 触发批量 INSERT。
 *
 * <p>覆盖 8 个场景：unsignedKey 成功 / signed valid HMAC 成功 / wrongSignature 401 /
 * expiredTimestamp 401 / disabledKey 401 / ipWhitelist 拒绝 401 / rateLimit 超限 429 / 调用日志落分区表。
 */
@SpringBootTest(classes = {TestApp.class, OpenApiFrameworkIntegrationTest.TestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "wen-saas-boot.openapi.enabled=true",
        "wen-saas-boot.openapi.rate-limit.default-limit=3",
        "wen-saas-boot.openapi.rate-limit.window=60s",
        "wen-saas-boot.openapi.timestamp-skew=300s",
        "wen-saas-boot.event.enabled=false",
        "wen-saas-boot.webhook.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiFrameworkIntegrationTest {

    private static final long TENANT_A = 9701L;

    @Autowired JdbcTemplate jdbc;
    @Autowired RequestIdFilter requestIdFilter;
    @Autowired AppKeyAuthenticationFilter authFilter;
    @Autowired ApiCallLogFilter callLogFilter;
    @Autowired HmacSignatureVerifier verifier;
    @Autowired CredentialCipher cipher;
    @Autowired ApiCallBatchBuffer apiCallBatchBuffer;
    @Autowired CapturingApiCallEventPublisher capturingPublisher;
    @Autowired StringRedisTemplate redis;

    private MockMvc mockMvc;
    private final PingController controller = new PingController();

    @BeforeAll
    void seedAll() {
        // 表结构由单一基线迁移 V1.0.0 保证（测试连已迁移的 wen_saas 库，flyway.enabled=false）。
        cleanup();
        // 4 个不同特性的 ApiKey
        insertKey("ak-unsigned", TENANT_A, "secret-unsigned", false, "active", null);
        insertKey("ak-signed", TENANT_A, "secret-signed", true, "active", null);
        insertKey("ak-disabled", TENANT_A, "secret-disabled", false, "disabled", null);
        insertKey("ak-ipwhitelist", TENANT_A, "secret-ipwl", false, "active", List.of("10.0.0.0/8"));

        // 顺序：requestId（最外）→ callLog → auth（最内），与生产 chain 一致
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(requestIdFilter, callLogFilter, authFilter)
                .build();
    }

    @AfterAll
    void teardown() {
        cleanup();
    }

    @BeforeEach
    void resetEach() {
        // 清限流 + ApiKey 缓存，避免测试间相互污染
        Set<String> rl = redis.keys("wen-saas-boot:openapi:ratelimit:*");
        if (rl != null && !rl.isEmpty()) {
            redis.delete(rl);
        }
        Set<String> ak = redis.keys("wen-saas-boot:openapi:apikey:*");
        if (ak != null && !ak.isEmpty()) {
            redis.delete(ak);
        }
        Set<String> nonce = redis.keys("wen-saas-boot:dedup:openapi-nonce:*");
        if (nonce != null && !nonce.isEmpty()) {
            redis.delete(nonce);
        }
        capturingPublisher.clear();
    }

    // -------------------------------------------------------------------------------
    // 8 个测试用例
    // -------------------------------------------------------------------------------

    @Test
    void unsignedKey_passesAndInjectsContext() throws Exception {
        mockMvc.perform(get("/api/v1/openapi/ping").header("X-Wen-API-Key", "ak-unsigned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_A))
                .andExpect(jsonPath("$.byApiKey").value(true))
                .andExpect(jsonPath("$.apiKeyId").isNumber())
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().exists("X-RateLimit-Reset"));
    }

    @Test
    void signedKey_validHmac_passes() throws Exception {
        long ts = Instant.now().getEpochSecond();
        String body = "{\"hello\":\"world\"}";
        String nonce = java.util.UUID.randomUUID().toString();
        String sig = verifier.sign("secret-signed", "POST", "/api/v1/openapi/echo", body, ts, nonce);
        mockMvc.perform(post("/api/v1/openapi/echo")
                        .header("X-Wen-API-Key", "ak-signed")
                        .header("X-Wen-Timestamp", String.valueOf(ts))
                        .header("X-Wen-Nonce", nonce)
                        .header("X-Wen-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.byApiKey").value(true));
    }

    @Test
    void signedKey_wrongSignature_401() throws Exception {
        long ts = Instant.now().getEpochSecond();
        mockMvc.perform(post("/api/v1/openapi/echo")
                        .header("X-Wen-API-Key", "ak-signed")
                        .header("X-Wen-Timestamp", String.valueOf(ts))
                        .header("X-Wen-Nonce", java.util.UUID.randomUUID().toString())
                        .header("X-Wen-Signature", "BOGUS-SIGNATURE-BASE64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication failed"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void signedKey_missingNonce_401() throws Exception {
        long ts = Instant.now().getEpochSecond();
        String sig = verifier.sign("secret-signed", "POST", "/api/v1/openapi/echo", "{}", ts, "");
        mockMvc.perform(post("/api/v1/openapi/echo")
                        .header("X-Wen-API-Key", "ak-signed")
                        .header("X-Wen-Timestamp", String.valueOf(ts))
                        .header("X-Wen-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication failed"));
    }

    @Test
    void signedKey_replayedNonce_401() throws Exception {
        long ts = Instant.now().getEpochSecond();
        String body = "{\"hello\":\"world\"}";
        String nonce = java.util.UUID.randomUUID().toString();
        String sig = verifier.sign("secret-signed", "POST", "/api/v1/openapi/echo", body, ts, nonce);
        // 首次：合法通过
        mockMvc.perform(post("/api/v1/openapi/echo")
                        .header("X-Wen-API-Key", "ak-signed")
                        .header("X-Wen-Timestamp", String.valueOf(ts))
                        .header("X-Wen-Nonce", nonce)
                        .header("X-Wen-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        // 原样重放（同 nonce + 同签名）：窗口内一次性校验拦下
        mockMvc.perform(post("/api/v1/openapi/echo")
                        .header("X-Wen-API-Key", "ak-signed")
                        .header("X-Wen-Timestamp", String.valueOf(ts))
                        .header("X-Wen-Nonce", nonce)
                        .header("X-Wen-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication failed"));
    }

    @Test
    void expiredTimestamp_401() throws Exception {
        // 偏差 7 分钟，超过 timestampSkew=300s
        long ts = Instant.now().getEpochSecond() - 7 * 60;
        String nonce = java.util.UUID.randomUUID().toString();
        String sig = verifier.sign("secret-signed", "POST", "/api/v1/openapi/echo", "{}", ts, nonce);
        mockMvc.perform(post("/api/v1/openapi/echo")
                        .header("X-Wen-API-Key", "ak-signed")
                        .header("X-Wen-Timestamp", String.valueOf(ts))
                        .header("X-Wen-Nonce", nonce)
                        .header("X-Wen-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication failed"));
    }

    @Test
    void disabledKey_401() throws Exception {
        mockMvc.perform(get("/api/v1/openapi/ping").header("X-Wen-API-Key", "ak-disabled"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication failed"));
    }

    @Test
    void ipWhitelistReject_401() throws Exception {
        // mockMvc 默认 remoteAddr = 127.0.0.1，不在 10.0.0.0/8
        mockMvc.perform(get("/api/v1/openapi/ping").header("X-Wen-API-Key", "ak-ipwhitelist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication failed"));
    }

    @Test
    void rateLimitExceeded_429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/openapi/ping").header("X-Wen-API-Key", "ak-unsigned"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/openapi/ping").header("X-Wen-API-Key", "ak-unsigned"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value("Request rate limit exceeded"));
    }

    @Test
    void apiCallLog_writesToPartition_afterFlush() throws Exception {
        mockMvc.perform(get("/api/v1/openapi/ping")
                        .header("X-Wen-API-Key", "ak-unsigned")
                        .header("X-Forwarded-For", "203.0.113.7")
                        .header("User-Agent", "test-agent/1.0"));
        // CapturingPublisher 捕获 → 转给 buffer.offer → buffer.flushNow 触发批量 INSERT
        capturingPublisher.flushAllTo(apiCallBatchBuffer);
        apiCallBatchBuffer.flushNow();

        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_api_call_log WHERE tenant_id=? AND path=?",
                Integer.class, TENANT_A, "/api/v1/openapi/ping");
        assertThat(cnt).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT ip, status_code, method, user_agent FROM sys_api_call_log "
                        + "WHERE tenant_id=? AND path=? ORDER BY id DESC LIMIT 1",
                TENANT_A, "/api/v1/openapi/ping");
        assertThat(row.get("ip")).isEqualTo("203.0.113.7");
        assertThat(row.get("status_code")).isEqualTo(200);
        assertThat(row.get("method")).isEqualTo("GET");
        assertThat(row.get("user_agent")).isEqualTo("test-agent/1.0");
    }

    // -------------------------------------------------------------------------------
    // 测试基础设施
    // -------------------------------------------------------------------------------

    void cleanup() {
        jdbc.update("DELETE FROM sys_api_call_log WHERE tenant_id=?", TENANT_A);
        jdbc.update("DELETE FROM sys_api_key WHERE tenant_id=?", TENANT_A);
    }

    void insertKey(String apiKey, long tenantId, String plainSecret,
                   boolean requireSignature, String status, List<String> ipWhitelist) {
        String secretEnc = cipher.encrypt(plainSecret);
        String ipJson = ipWhitelist == null ? null : JSON.toJSONString(ipWhitelist);
        jdbc.update("""
                        INSERT INTO sys_api_key
                            (id, tenant_id, name, api_key, secret_encrypted, ip_whitelist,
                             require_signature, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, now(), now())""",
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),
                tenantId, "test-" + apiKey, apiKey, secretEnc, ipJson,
                requireSignature, status);
    }

    // -------------------------------------------------------------------------------
    // 内嵌 Controller + Fixture Configuration
    // -------------------------------------------------------------------------------

    @RestController
    static class PingController {

        @GetMapping("/api/v1/openapi/ping")
        public Map<String, Object> ping() {
            Map<String, Object> m = new LinkedHashMap<>();
            UserPrincipal p = UserContext.get();
            m.put("tenantId", TenantContext.get());
            m.put("userId", p == null ? null : p.userId());
            m.put("byApiKey", p != null && p.byApiKey());
            m.put("apiKeyId", p == null ? null : p.apiKeyId());
            return m;
        }

        @PostMapping("/api/v1/openapi/echo")
        public Map<String, Object> echo(@RequestBody(required = false) Map<String, Object> body) {
            Map<String, Object> m = new LinkedHashMap<>();
            UserPrincipal p = UserContext.get();
            m.put("ok", true);
            m.put("byApiKey", p != null && p.byApiKey());
            m.put("received", body == null ? Map.of() : body);
            return m;
        }
    }

    /** 截获 ApiCallEvent 不真发到 RocketMQ。 */
    static class CapturingApiCallEventPublisher implements ApiCallEventPublisher {
        private final List<ApiCallEvent> buffer = new CopyOnWriteArrayList<>();

        @Override
        public void publish(ApiCallEvent event) {
            buffer.add(event);
        }

        void clear() {
            buffer.clear();
        }

        void flushAllTo(ApiCallBatchBuffer target) {
            List<ApiCallEvent> snapshot = new ArrayList<>(buffer);
            buffer.clear();
            for (ApiCallEvent e : snapshot) {
                target.offer(e);
            }
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        @Primary
        CapturingApiCallEventPublisher capturingApiCallEventPublisher() {
            return new CapturingApiCallEventPublisher();
        }
    }
}
