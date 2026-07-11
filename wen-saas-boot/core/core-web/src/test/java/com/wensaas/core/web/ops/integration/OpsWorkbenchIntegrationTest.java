package com.wensaas.core.web.ops.integration;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.ops.setting.OpsSettingService;
import com.wensaas.core.api.ops.setting.SettingCategory;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.auth.filter.AuthenticationFilter;
import com.wensaas.core.impl.auth.password.BCryptPasswordHasher;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.ops.OpsAuthWebFilter;
import com.wensaas.core.impl.permission.role.SysRole;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 运营工作台 + 系统管理集成测试。
 *
 * <p>覆盖：运营 RBAC 强制（@RequirePermission allow/deny）、2 预置角色保护、权限树、
 * 系统参数读写 + 安全策略联动（password_min_length 改 12 → 弱密码新建成员被拒）、
 * 工作台指标按数据范围过滤（OPS_SUPER_ADMIN 全量 vs OPS_CSM 仅看 csm_user_id=self）。
 *
 * <p>预置角色 id 来自迁移；ops.* 权限点由 OpsTeamRoleSeedRunner 启动期绑定。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(OpsWorkbenchIntegrationTest.TestFixtures.class)
class OpsWorkbenchIntegrationTest {

    private static final long ROLE_SUPER_ADMIN = 12100001L;
    private static final long ROLE_CSM = 12100002L;

    private static final String PASSWORD = "Admin@2026!";
    private static final String ADMIN_USER = "ops-wb-it-admin";
    private static final String CSM_USER = "ops-wb-it-csm";
    private static final String TENANT_PREFIX = "OPSWB_IT_";

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationFilter authenticationFilter;
    @Autowired private TenantContextWebFilter tenantContextWebFilter;
    @Autowired private OpsAuthWebFilter opsAuthWebFilter;
    @Autowired private TestFixtures fixtures;

    private MockMvc mvc;
    private long csmUserId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(authenticationFilter, tenantContextWebFilter, opsAuthWebFilter)
                .build();
        fixtures.cleanup();
        fixtures.seedOpsUser(ADMIN_USER, ROLE_SUPER_ADMIN);
        csmUserId = fixtures.seedOpsUser(CSM_USER, ROLE_CSM);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    // ===================== 角色 Tab =====================

    @Test
    void roleList_superAdmin_returnsTwoPresets() throws Exception {
        String token = login(ADMIN_USER);
        mvc.perform(get("/api/v1/ops/team/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='OPS_SUPER_ADMIN')]").exists())
                .andExpect(jsonPath("$[?(@.code=='OPS_CSM')]").exists())
                // OPS_SUPPORT 已裁撤（工单由 OPS_CSM 承接），不应再出现
                .andExpect(jsonPath("$[?(@.code=='OPS_SUPPORT')]").doesNotExist());
    }

    @Test
    void deletePresetRole_returns422() throws Exception {
        String token = login(ADMIN_USER);
        mvc.perform(delete("/api/v1/ops/team/roles/" + ROLE_SUPER_ADMIN)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_PRESET_ROLE"));
    }

    @Test
    void createCustomRole_thenDelete_succeeds() throws Exception {
        String token = login(ADMIN_USER);
        Map<String, Object> body = Map.of(
                "code", "OPSWB_IT_FINANCE",
                "name", "IT 财务",
                "description", "集成测试自定义角色",
                "dataScope", "ALL");
        MvcResult res = mvc.perform(post("/api/v1/ops/team/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(body)))
                .andExpect(status().isOk())
                .andReturn();
        long roleId = JSON.parseObject(res.getResponse().getContentAsString()).getLongValue("id");
        // 自定义角色可删
        mvc.perform(delete("/api/v1/ops/team/roles/" + roleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // ===================== 系统参数 Tab + 安全策略联动 =====================

    @Test
    void settingsListAll_returnsFourCategories() throws Exception {
        String token = login(ADMIN_USER);
        mvc.perform(get("/api/v1/ops/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.category=='platform_info')]").exists())
                .andExpect(jsonPath("$[?(@.category=='health_score')]").exists())
                .andExpect(jsonPath("$[?(@.category=='tenant_default')]").exists())
                .andExpect(jsonPath("$[?(@.category=='security_policy')]").exists());
    }

    @Test
    void raisePasswordMinLength_thenWeakMemberCreate_rejected() throws Exception {
        String token = login(ADMIN_USER);
        // 1) 把 password_min_length 改成 12
        mvc.perform(put("/api/v1/ops/settings/security_policy")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("values", Map.of("password_min_length", 12)))))
                .andExpect(status().isNoContent());

        // 2) 用 11 位强密码新建成员：过 DTO @Size(min=8)，但被服务层 min_length=12 拦截
        Map<String, Object> member = Map.of(
                "username", "itwbmember",
                "password", "Abcdef1!ghi", // 11 位，含大小写/数字/特殊
                "realName", "弱密码成员",
                "email", "itwb@example.com",
                "mobile", "13900001234",
                "roleIds", List.of());
        mvc.perform(post("/api/v1/ops/team/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(member)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ===================== 工作台指标 + 数据范围 =====================

    @Test
    void dashboardOverview_superAdmin_returns200() throws Exception {
        String token = login(ADMIN_USER);
        mvc.perform(get("/api/v1/ops/dashboard/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTenants").exists());
    }

    @Test
    void dashboardOverview_csm_scopedToOwnTenants() throws Exception {
        // 2 个归属该 CSM 的在租租户 + 1 个归属他人的（CSM 不应数到）
        fixtures.seedTenant(TENANT_PREFIX + "A", csmUserId);
        fixtures.seedTenant(TENANT_PREFIX + "B", csmUserId);
        fixtures.seedTenant(TENANT_PREFIX + "OTHER", 99999999L);

        String token = login(CSM_USER);
        mvc.perform(get("/api/v1/ops/dashboard/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // OPS_CSM = OWNED：仅看 csm_user_id=self，应为 2（不含他人的那个）
                .andExpect(jsonPath("$.activeTenants").value("2"));
    }

    // ===================== 权限守卫 =====================

    @Test
    void csmRole_cannotCreateRole_forbidden() throws Exception {
        // OPS_CSM 默认白名单不含 ops.team.role.create
        String token = login(CSM_USER);
        Map<String, Object> body = Map.of(
                "code", "OPSWB_IT_NOPE", "name", "应被拒", "dataScope", "ALL");
        mvc.perform(post("/api/v1/ops/team/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_unauthorized() throws Exception {
        mvc.perform(get("/api/v1/ops/team/roles"))
                .andExpect(status().isUnauthorized());
    }

    // ===================== helpers =====================

    private String login(String username) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
    }

    // ===================== Fixture =====================

    @Component
    @CrossTenantQuery("OpsWorkbenchIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysUserRoleMapper userRoleMapper;
        final SysRoleMapper roleMapper;
        final SysTenantMapper tenantMapper;
        final OpsSettingService settingService;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                     SysRoleMapper roleMapper, SysTenantMapper tenantMapper,
                     OpsSettingService settingService) {
            this.userMapper = userMapper;
            this.userRoleMapper = userRoleMapper;
            this.roleMapper = roleMapper;
            this.tenantMapper = tenantMapper;
            this.settingService = settingService;
        }

        /** 建运营账号（tenant_id=0）并绑定预置角色；返回 userId。 */
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

        void seedTenant(String code, long csmUserId) {
            SysTenant t = new SysTenant();
            t.setCode(code);
            t.setName(code);
            t.setStatus(1);
            t.setCsmUserId(csmUserId);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            tenantMapper.insert(t);
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
            // 集成测试新建的成员
            userMapper.delete(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getTenantId, 0L)
                    .eq(SysUser::getUsername, "itwbmember"));
            // 集成测试自定义角色（OPSWB_IT_*）
            roleMapper.delete(Wrappers.<SysRole>lambdaQuery()
                    .eq(SysRole::getTenantId, 0L)
                    .likeRight(SysRole::getCode, TENANT_PREFIX));
            // 测试租户
            tenantMapper.delete(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
            // 还原安全策略：password_min_length 改回种子默认 8（避免污染共享 DB / 其他用例）
            settingService.update(SettingCategory.SECURITY_POLICY,
                    Map.of("password_min_length", 8), 0L);
        }
    }
}
