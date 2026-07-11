package com.wensaas.core.web.ops.integration;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.ops.health.OpsHealthService.HealthMetrics;
import com.wensaas.core.api.ops.health.OpsHealthService.RiskSignal;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.auth.filter.AuthenticationFilter;
import com.wensaas.core.impl.auth.password.BCryptPasswordHasher;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.ops.OpsAuthWebFilter;
import com.wensaas.core.impl.ops.health.SysTenantHealth;
import com.wensaas.core.impl.ops.health.SysTenantHealthMapper;
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
 * 客户健康度集成测试。
 *
 * <p>覆盖：总览/列表/详情三端点 + 权限守卫 + 数据范围（OPS_CSM 仅看 csm_user_id=self）+
 * 详情实时重算 + 404。健康度快照行直接经 {@link SysTenantHealthMapper} 注入（生产由每日 Job 计算）。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(OpsHealthIntegrationTest.TestFixtures.class)
class OpsHealthIntegrationTest {

    private static final long ROLE_SUPER_ADMIN = 12100001L;
    private static final long ROLE_CSM = 12100002L;

    private static final String PASSWORD = "Admin@2026!";
    private static final String ADMIN_USER = "ops-health-it-admin";
    private static final String CSM_USER = "ops-health-it-csm";
    private static final String TENANT_PREFIX = "OPSHEALTH_IT_";

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
    void overview_superAdmin_returns200() throws Exception {
        String token = login(ADMIN_USER);
        mvc.perform(get("/api/v1/ops/health/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").exists())
                .andExpect(jsonPath("$.warning").exists())
                .andExpect(jsonPath("$.danger").exists());
    }

    @Test
    void list_superAdmin_returns200() throws Exception {
        String token = login(ADMIN_USER);
        mvc.perform(get("/api/v1/ops/health/tenants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records").isArray())
                .andExpect(jsonPath("$.total").exists());
    }

    @Test
    void list_csm_scopedToOwnTenants_andLevelFilter() throws Exception {
        long csmUserId = fixtures.seedOpsUser(CSM_USER, ROLE_CSM);
        long tA = fixtures.seedTenant(TENANT_PREFIX + "A", csmUserId);
        long tB = fixtures.seedTenant(TENANT_PREFIX + "B", csmUserId);
        long tOther = fixtures.seedTenant(TENANT_PREFIX + "OTHER", 99999999L);
        fixtures.seedHealth(tA, 30, "danger");
        fixtures.seedHealth(tB, 40, "danger");
        fixtures.seedHealth(tOther, 20, "danger"); // 归属他人，CSM 不应看到

        String token = login(CSM_USER);
        mvc.perform(get("/api/v1/ops/health/tenants?level=danger")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("2"));
    }

    @Test
    void detail_returnsLiveScoreAndMetrics() throws Exception {
        long tid = fixtures.seedTenant(TENANT_PREFIX + "DETAIL", 0L);
        String token = login(ADMIN_USER);
        mvc.perform(get("/api/v1/ops/health/tenants/" + tid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(String.valueOf(tid)))
                .andExpect(jsonPath("$.score").exists())
                .andExpect(jsonPath("$.level").exists())
                .andExpect(jsonPath("$.metrics").exists())
                .andExpect(jsonPath("$.riskSignals").isArray());
    }

    @Test
    void detail_unknownTenant_returns404() throws Exception {
        String token = login(ADMIN_USER);
        mvc.perform(get("/api/v1/ops/health/tenants/99887766")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    void noToken_unauthorized() throws Exception {
        mvc.perform(get("/api/v1/ops/health/overview"))
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
    @CrossTenantQuery("OpsHealthIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysUserRoleMapper userRoleMapper;
        final SysTenantMapper tenantMapper;
        final SysTenantHealthMapper healthMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                     SysTenantMapper tenantMapper, SysTenantHealthMapper healthMapper) {
            this.userMapper = userMapper;
            this.userRoleMapper = userRoleMapper;
            this.tenantMapper = tenantMapper;
            this.healthMapper = healthMapper;
        }

        long seedOpsUser(String username, long roleId) {
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

        void seedHealth(long tenantId, int score, String level) {
            SysTenantHealth h = new SysTenantHealth();
            h.setTenantId(tenantId);
            h.setScore(score);
            h.setLevel(level);
            h.setRiskSignals(List.of(new RiskSignal("expiring_soon", "距到期 5 天", 20)));
            h.setMetrics(new HealthMetrics(1, 5, 12L, -60, null, null));
            h.setComputedAt(Instant.now());
            h.setCreatedAt(LocalDateTime.now());
            h.setUpdatedAt(LocalDateTime.now());
            healthMapper.insert(h);
        }

        void cleanup() {
            for (String username : List.of(ADMIN_USER, CSM_USER)) {
                SysUser u = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getTenantId, 0L)
                        .eq(SysUser::getUsername, username));
                if (u != null) {
                    userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                            .eq(SysUserRole::getUserId, u.getId()));
                    userMapper.deleteById(u.getId());
                }
            }
            List<SysTenant> tenants = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
            for (SysTenant t : tenants) {
                healthMapper.delete(Wrappers.<SysTenantHealth>lambdaQuery()
                        .eq(SysTenantHealth::getTenantId, t.getId()));
            }
            tenantMapper.delete(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
        }
    }
}
