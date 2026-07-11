package com.wensaas.core.web.compliance.integration;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.auth.filter.AuthenticationFilter;
import com.wensaas.core.impl.auth.password.BCryptPasswordHasher;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.ops.OpsAuthWebFilter;
import com.wensaas.core.impl.permission.role.SysUserRole;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.tenant.web.TenantContextWebFilter;
import com.wensaas.core.web.auth.integration.WebTestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据隐私与合规 · 敏感字段加密集成测试。
 *
 * <p>覆盖：① sys_user.mobile/email 经 TypeHandler 落库为密文（raw 列 ≠ 明文、带 keyId 前缀），
 * 实体读出透明解密回明文；② 确定性加密——同明文恒得同密文（唯一性等值校验赖以成立）；
 * ③ {@code GET /api/v1/ops/compliance/encryption}（运营端自查）返回底座状态 + 字段登记表 + 掩码规则。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(EncryptionIntegrationTest.TestFixtures.class)
class EncryptionIntegrationTest {

    private static final long ROLE_SUPER_ADMIN = 12100001L;
    private static final String PASSWORD = "Admin@2026!";
    private static final String OWNER_USER = "encryption-it-owner";
    private static final String PEER_USER = "encryption-it-peer";
    private static final String OPS_USER = "encryption-it-ops";
    private static final String TENANT_PREFIX = "TENENCRYPT_IT_";
    private static final String PLAIN_MOBILE = "13800009999";
    private static final String PLAIN_EMAIL = "enc-it@example.com";

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationFilter authenticationFilter;
    @Autowired private TenantContextWebFilter tenantContextWebFilter;
    @Autowired private OpsAuthWebFilter opsAuthWebFilter;
    @Autowired private TestFixtures fixtures;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(authenticationFilter, tenantContextWebFilter, opsAuthWebFilter)
                .build();
        fixtures.cleanup();
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    @Test
    void mobileEmail_encryptedAtRest_transparentRoundTrip_deterministic() {
        String tenantCode = TENANT_PREFIX + "A";
        long tenant = fixtures.seedTenant(tenantCode);
        long ownerId = fixtures.seedTenantUser(OWNER_USER, tenant, true, PLAIN_MOBILE, PLAIN_EMAIL);
        // 同手机号的第二个成员：验证确定性（同明文→同密文）
        long peerId = fixtures.seedTenantUser(PEER_USER, tenant, false, PLAIN_MOBILE, null);

        // ① raw 列为密文：≠ 明文、带 activeKeyId 前缀
        String rawMobile = fixtures.rawColumn(ownerId, "mobile");
        String rawEmail = fixtures.rawColumn(ownerId, "email");
        assertThat(rawMobile).isNotEqualTo(PLAIN_MOBILE).startsWith("default:");
        assertThat(rawEmail).isNotEqualTo(PLAIN_EMAIL).startsWith("default:");

        // ② 实体读出透明解密回明文
        SysUser loaded = fixtures.findById(ownerId);
        assertThat(loaded.getMobile()).isEqualTo(PLAIN_MOBILE);
        assertThat(loaded.getEmail()).isEqualTo(PLAIN_EMAIL);

        // ③ 确定性：同明文手机号在两条记录里密文一致
        assertThat(fixtures.rawColumn(peerId, "mobile")).isEqualTo(rawMobile);
    }

    @Test
    void encryptionOverview_returnsRegistryAndMaskRules_onOpsEndpoint() throws Exception {
        // 加密底座自查已移至运营端（平台运维信息不暴露给租户）
        fixtures.seedOpsUser(OPS_USER, ROLE_SUPER_ADMIN);

        String token = login(OPS_USER);
        mvc.perform(get("/api/v1/ops/compliance/encryption")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("AES-256-GCM"))
                .andExpect(jsonPath("$.keyConfigured").value(true))
                .andExpect(jsonPath("$.activeKeyId").value("default"))
                .andExpect(jsonPath("$.fields").isArray())
                .andExpect(jsonPath("$.fields[?(@.field=='成员手机号')].encryptedAtRest").value(true))
                .andExpect(jsonPath("$.fields[?(@.field=='API Secret')].encryptedAtRest").value(true))
                .andExpect(jsonPath("$.maskRules[?(@.fieldType=='手机号')].masked").value("138****5678"));
    }

    private String login(String username) throws Exception {
        return login(username, null);
    }

    private String login(String username, String tenantCode) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", PASSWORD);
        if (tenantCode != null) {
            body.put("tenantCode", tenantCode);
        }
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
    }

    @Component
    @CrossTenantQuery("EncryptionIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysTenantMapper tenantMapper;
        final SysUserRoleMapper userRoleMapper;
        final JdbcTemplate jdbc;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysTenantMapper tenantMapper,
                     SysUserRoleMapper userRoleMapper,
                     JdbcTemplate jdbc) {
            this.userMapper = userMapper;
            this.tenantMapper = tenantMapper;
            this.userRoleMapper = userRoleMapper;
            this.jdbc = jdbc;
        }

        void seedOpsUser(String username, long roleId) {
            SysUser u = new SysUser();
            u.setTenantId(0L);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(PASSWORD));
            u.setRealName(username);
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);

            SysUserRole ur = new SysUserRole();
            ur.setTenantId(0L);
            ur.setUserId(u.getId());
            ur.setRoleId(roleId);
            ur.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(ur);
        }

        long seedTenant(String code) {
            SysTenant t = new SysTenant();
            t.setCode(code);
            t.setName(code);
            t.setStatus(1);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            tenantMapper.insert(t);
            return t.getId();
        }

        long seedTenantUser(String username, long tenantId, boolean asOwner, String mobile, String email) {
            SysUser u = new SysUser();
            u.setTenantId(tenantId);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(PASSWORD));
            u.setRealName(username);
            u.setMobile(mobile);
            u.setEmail(email);
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);
            if (asOwner) {
                SysTenant patch = new SysTenant();
                patch.setId(tenantId);
                patch.setMainAdminUserId(u.getId());
                tenantMapper.updateById(patch);
            }
            return u.getId();
        }

        /** 直查 raw 列（绕过 TypeHandler），观察落库密文。 */
        String rawColumn(long userId, String column) {
            return jdbc.queryForObject(
                    "SELECT " + column + " FROM sys_user WHERE id = ?", String.class, userId);
        }

        SysUser findById(long userId) {
            return userMapper.selectById(userId);
        }

        void cleanup() {
            SysUser ops = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getUsername, OPS_USER));
            if (ops != null) {
                userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, ops.getId()));
            }
            userMapper.delete(Wrappers.<SysUser>lambdaQuery()
                    .in(SysUser::getUsername, OWNER_USER, PEER_USER, OPS_USER));
            tenantMapper.delete(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
        }
    }
}
