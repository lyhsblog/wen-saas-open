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
import com.wensaas.core.impl.ops.monitor.SysTenantRenewalLog;
import com.wensaas.core.impl.ops.monitor.SysTenantRenewalLogMapper;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台运维监控看板集成测试。
 *
 * <p>覆盖 7 聚合端点 200 + 结构、CSM 数据范围收敛（activeTenants 仅算自己归属）、
 * 无角色运营账号缺 {@code ops.monitor.view} 权限 403、无 token 401、续费台账聚合可读。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(OpsPlatformMonitorIntegrationTest.TestFixtures.class)
class OpsPlatformMonitorIntegrationTest {

    private static final long ROLE_SUPER_ADMIN = 12100001L;
    private static final long ROLE_CSM = 12100002L;

    private static final String PASSWORD = "Admin@2026!";
    private static final String ADMIN_USER = "ops-monitor-it-admin";
    private static final String CSM_USER = "ops-monitor-it-csm";
    private static final String NOROLE_USER = "ops-monitor-it-norole";
    private static final String TENANT_PREFIX = "OPSMON_IT_";

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
        fixtures.seedOpsUser(ADMIN_USER, ROLE_SUPER_ADMIN);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    @Test
    void overview_returns200WithStructure() throws Exception {
        String token = login(ADMIN_USER);
        mvc.perform(get("/api/v1/ops/platform/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTenants").exists())
                .andExpect(jsonPath("$.dau").exists())
                .andExpect(jsonPath("$.dauSpark").isArray())
                .andExpect(jsonPath("$.expiringIn30Days").exists());
    }

    @Test
    void trends_allTypes_return200() throws Exception {
        String token = login(ADMIN_USER);
        for (String type : List.of("active", "tenant", "api", "ticket")) {
            mvc.perform(get("/api/v1/ops/platform/trends?type=" + type + "&range=7d")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dates").isArray())
                    .andExpect(jsonPath("$.y1").isArray())
                    .andExpect(jsonPath("$.y2").isArray());
        }
    }

    @Test
    void secondaryEndpoints_return200() throws Exception {
        String token = login(ADMIN_USER);
        mvc.perform(get("/api/v1/ops/platform/modules/metrics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/v1/ops/platform/plans/distribution").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/v1/ops/platform/renewals/trend").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").isArray())
                .andExpect(jsonPath("$.renewedThisMonth").exists());
        mvc.perform(get("/api/v1/ops/platform/resources/usage").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalSize").exists());
        mvc.perform(get("/api/v1/ops/platform/api-webhook/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.topFailures").isArray());
    }

    @Test
    void overview_csm_scopedToOwnTenants() throws Exception {
        long csmUserId = fixtures.seedOpsUser(CSM_USER, ROLE_CSM);
        fixtures.seedTenant(TENANT_PREFIX + "A", csmUserId);
        fixtures.seedTenant(TENANT_PREFIX + "B", csmUserId);
        fixtures.seedTenant(TENANT_PREFIX + "OTHER", 99999999L); // 归属他人，不计入 CSM

        String token = login(CSM_USER);
        mvc.perform(get("/api/v1/ops/platform/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTenants").value("2"));
    }

    @Test
    void renewalsTrend_csm_countsSeededRenew() throws Exception {
        long csmUserId = fixtures.seedOpsUser(CSM_USER, ROLE_CSM);
        long tA = fixtures.seedTenant(TENANT_PREFIX + "R", csmUserId);
        fixtures.seedRenewal(tA, SysTenantRenewalLog.EVENT_RENEW);

        String token = login(CSM_USER);
        mvc.perform(get("/api/v1/ops/platform/renewals/trend").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renewedThisMonth").value("1"));
    }

    @Test
    void noRole_lacksPermission_forbidden() throws Exception {
        // 未绑任何运营角色 → 无 ops.monitor.view，@RequirePermission 拦下 403
        fixtures.seedOpsUserWithoutRole(NOROLE_USER);
        String token = login(NOROLE_USER);
        mvc.perform(get("/api/v1/ops/platform/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_unauthorized() throws Exception {
        mvc.perform(get("/api/v1/ops/platform/overview"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
    }

    @Component
    @CrossTenantQuery("OpsPlatformMonitorIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysUserRoleMapper userRoleMapper;
        final SysTenantMapper tenantMapper;
        final SysTenantRenewalLogMapper renewalLogMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                     SysTenantMapper tenantMapper, SysTenantRenewalLogMapper renewalLogMapper) {
            this.userMapper = userMapper;
            this.userRoleMapper = userRoleMapper;
            this.tenantMapper = tenantMapper;
            this.renewalLogMapper = renewalLogMapper;
        }

        long seedOpsUser(String username, long roleId) {
            long userId = seedOpsUserWithoutRole(username);

            SysUserRole ur = new SysUserRole();
            ur.setTenantId(0L);
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            ur.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(ur);

            return userId;
        }

        /** 建运营账号但不绑任何角色（无权限基线）。 */
        long seedOpsUserWithoutRole(String username) {
            SysUser u = new SysUser();
            u.setTenantId(0L);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(PASSWORD));
            u.setRealName(username);
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);
            return u.getId();
        }

        long seedTenant(String code, long csmUserId) {
            SysTenant t = new SysTenant();
            t.setCode(code);
            t.setName(code);
            t.setStatus(1);
            t.setCsmUserId(csmUserId == 0L ? null : csmUserId);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            tenantMapper.insert(t);
            return t.getId();
        }

        void seedRenewal(long tenantId, String eventType) {
            SysTenantRenewalLog r = new SysTenantRenewalLog();
            r.setTenantId(tenantId);
            r.setPackageCode("base");
            r.setEventType(eventType);
            r.setNewExpireAt(Instant.now().plusSeconds(86400L * 30));
            r.setTtlDays(30);
            r.setOperatorId(0L);
            r.setCreatedAt(LocalDateTime.now());
            renewalLogMapper.insert(r);
        }

        void cleanup() {
            List<SysTenant> tenants = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
            for (SysTenant t : tenants) {
                renewalLogMapper.delete(Wrappers.<SysTenantRenewalLog>lambdaQuery()
                        .eq(SysTenantRenewalLog::getTenantId, t.getId()));
            }
            tenantMapper.delete(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
            for (String username : List.of(ADMIN_USER, CSM_USER, NOROLE_USER)) {
                SysUser u = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getTenantId, 0L)
                        .eq(SysUser::getUsername, username));
                if (u != null) {
                    userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                            .eq(SysUserRole::getUserId, u.getId()));
                    userMapper.deleteById(u.getId());
                }
            }
        }
    }
}
