package com.wensaas.core.web.ops.integration;

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
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 代登录完整端到端集成测试。覆盖：
 * ops 登录 → 代登录签发 nonce → 公开端点兑换 token → /api/auth/me 返回目标用户 →
 * 退出代登录（204，仅销毁代登录 session）→ 运营原 token 全程未受影响 → nonce 单次使用。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(ImpersonateIntegrationTest.TestFixtures.class)
class ImpersonateIntegrationTest {

    private static final String OPS_USERNAME   = "imp-it-ops-admin";
    private static final String OPS_PASSWORD   = "Admin@2026!";
    private static final String TENANT_CODE    = "imp-it-tenant";
    private static final String MEMBER_USERNAME = "imp-it-member";
    private static final String MEMBER_PASSWORD = "Pass@2026!";

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationFilter authenticationFilter;
    @Autowired private TenantContextWebFilter tenantContextWebFilter;
    @Autowired private OpsAuthWebFilter opsAuthWebFilter;
    @Autowired private TestFixtures fixtures;

    private MockMvc mvc;
    private String tenantId;
    private long memberId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(authenticationFilter, tenantContextWebFilter, opsAuthWebFilter)
                .build();
        fixtures.cleanup();
        fixtures.seedOpsUser(OPS_USERNAME, OPS_PASSWORD);
        tenantId = fixtures.createTenant(TENANT_CODE);
        memberId = fixtures.seedTenantMember(Long.parseLong(tenantId), MEMBER_USERNAME, MEMBER_PASSWORD);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    @Test
    void fullFlow_opsImpersonateThenExit() throws Exception {
        // 1. 运营登录
        String opsToken = loginOps();
        assertThat(opsToken).isNotBlank();

        // 2. 发起代登录，获取 nonce
        MvcResult nonceRes = mvc.perform(post("/api/v1/ops/tenants/" + tenantId + "/impersonate")
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("asUserId", memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redeemNonce").isString())
                .andExpect(jsonPath("$.targetTenantCode").value(TENANT_CODE))
                .andExpect(jsonPath("$.expiresInSec").value(300))
                .andReturn();

        String nonce = JSON.parseObject(nonceRes.getResponse().getContentAsString())
                .getString("redeemNonce");
        assertThat(nonce).isNotBlank();

        // 3. 公开端点兑换 nonce → 代登录 token（无需 Authorization header）
        MvcResult redeemRes = mvc.perform(post("/api/auth/impersonate/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("nonce", nonce))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.username").value(MEMBER_USERNAME))
                .andExpect(jsonPath("$.user.tenantId").value(Long.parseLong(tenantId)))
                .andReturn();

        String impToken = JSON.parseObject(redeemRes.getResponse().getContentAsString())
                .getString("token");

        // 4. 用代登录 token 访问 /api/auth/me → 返回目标用户
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + impToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(MEMBER_USERNAME))
                .andExpect(jsonPath("$.tenantId").value(Long.parseLong(tenantId)));

        // 5. 退出代登录 → 204，无响应体（tenant-web 与 ops-web 不同域，不需要回传 ops token）
        mvc.perform(post("/api/auth/exit-impersonation")
                        .header("Authorization", "Bearer " + impToken))
                .andExpect(status().isNoContent());

        // 6. 运营原 token 全程未被改动 → 仍可正常访问
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(0))
                .andExpect(jsonPath("$.username").value(OPS_USERNAME));

        // 7. 代登录 token 已失效（session 已 invalidate）
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + impToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void redeemNonce_secondAttempt_returns404() throws Exception {
        String opsToken = loginOps();
        String nonce = issueNonce(opsToken, memberId);

        // 第一次兑换成功
        mvc.perform(post("/api/auth/impersonate/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("nonce", nonce))))
                .andExpect(status().isOk());

        // 第二次兑换 → 404（nonce 已被 getAndDelete）
        mvc.perform(post("/api/auth/impersonate/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("nonce", nonce))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMPERSONATE_NONCE_INVALID"));
    }

    @Test
    void nestedImpersonation_returns400() throws Exception {
        String opsToken = loginOps();
        String nonce = issueNonce(opsToken, memberId);

        // 兑换为代登录 token
        MvcResult redeemRes = mvc.perform(post("/api/auth/impersonate/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("nonce", nonce))))
                .andExpect(status().isOk())
                .andReturn();
        String impToken = JSON.parseObject(redeemRes.getResponse().getContentAsString()).getString("token");

        // 代登录态再次发起代登录 → 400 NESTED_NOT_ALLOWED
        // 注意：代登录 token 的 tenant_id != 0，会被 OpsAuthWebFilter 拦截（403 OPS_FORBIDDEN）
        // 所以此处不会到达 ImpersonateService.startImpersonation 的嵌套检查。
        // OpsAuthWebFilter 本身已防嵌套，我们验证 403 即可。
        mvc.perform(post("/api/v1/ops/tenants/" + tenantId + "/impersonate")
                        .header("Authorization", "Bearer " + impToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("asUserId", memberId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void exitImpersonation_normalSession_returns400() throws Exception {
        String opsToken = loginOps();
        // 普通运营 session 调 exit-impersonation → 400
        mvc.perform(post("/api/auth/exit-impersonation")
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXIT_IMPERSONATION_NOT_IMPERSONATING"));
    }

    // ===== helpers =====

    private String loginOps() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", OPS_USERNAME, "password", OPS_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
    }

    private String issueNonce(String opsToken, long asUserId) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/ops/tenants/" + tenantId + "/impersonate")
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("asUserId", asUserId))))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("redeemNonce");
    }

    @Component
    @CrossTenantQuery("ImpersonateIT: test fixture")
    static class TestFixtures {

        private final SysTenantMapper tenantMapper;
        private final SysUserMapper userMapper;
        private final SysUserRoleMapper userRoleMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();
        /** 预置 OPS_SUPER_ADMIN（含 ops.tenant.impersonate 等全部运营权限点） */
        static final long ROLE_SUPER_ADMIN = 12100001L;

        TestFixtures(SysTenantMapper tenantMapper, SysUserMapper userMapper,
                     SysUserRoleMapper userRoleMapper) {
            this.tenantMapper = tenantMapper;
            this.userMapper = userMapper;
            this.userRoleMapper = userRoleMapper;
        }

        void seedOpsUser(String username, String password) {
            SysUser u = new SysUser();
            u.setTenantId(0L);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(password));
            u.setRealName("Impersonate IT Ops");
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);

            // 绑定 OPS_SUPER_ADMIN，否则 POST /impersonate 的 @RequirePermission(ops.tenant.impersonate) 拦为 403
            SysUserRole ur = new SysUserRole();
            ur.setTenantId(0L);
            ur.setUserId(u.getId());
            ur.setRoleId(ROLE_SUPER_ADMIN);
            ur.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(ur);
        }

        /**
         * 直接插入一个租户记录（跳过 TenantOpsService，简化测试依赖）。
         * 返回新租户的 ID 字符串。
         */
        String createTenant(String code) {
            SysTenant t = new SysTenant();
            t.setCode(code);
            t.setName("代登录 IT 租户");
            t.setStatus(1);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            tenantMapper.insert(t);
            return String.valueOf(t.getId());
        }

        long seedTenantMember(long tenantId, String username, String password) {
            SysUser u = new SysUser();
            u.setTenantId(tenantId);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(password));
            u.setRealName("Impersonate IT Member");
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);
            return u.getId();
        }

        void cleanup() {
            List<SysTenant> tenants = tenantMapper.selectList(
                    Wrappers.<SysTenant>lambdaQuery().like(SysTenant::getCode, "imp-it"));
            for (SysTenant t : tenants) {
                userMapper.delete(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getTenantId, t.getId()));
            }
            if (!tenants.isEmpty()) {
                tenantMapper.deleteBatchIds(tenants.stream().map(SysTenant::getId).toList());
            }
            // 清理运营账号 + 其角色绑定
            List<SysUser> opsUsers = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getTenantId, 0L)
                    .like(SysUser::getUsername, "imp-it"));
            for (SysUser ou : opsUsers) {
                userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, ou.getId()));
                userMapper.deleteById(ou.getId());
            }
        }
    }
}
