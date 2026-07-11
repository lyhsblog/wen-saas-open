package com.wensaas.core.web.ops.integration;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.auth.filter.AuthenticationFilter;
import com.wensaas.core.impl.auth.password.BCryptPasswordHasher;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.ops.OpsAuthWebFilter;
import com.wensaas.core.impl.permission.role.SysUserRole;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.pkg.SysPackage;
import com.wensaas.core.impl.pkg.SysPackageMapper;
import com.wensaas.core.impl.pkg.SysPackageModule;
import com.wensaas.core.impl.pkg.SysPackageModuleMapper;
import com.wensaas.core.impl.spi.module.SysBusinessModule;
import com.wensaas.core.impl.spi.module.SysBusinessModuleMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 运营端业务模块管理（只读内省）集成测试。
 *
 * <p>覆盖：清单（含底座虚拟模块 base）+ 详情贡献能力计数 + 关联套餐反查 + 404 + RBAC 鉴权（@RequirePermission）。
 *
 * <p>ops 用户绑定预置 OPS_SUPER_ADMIN（id=12100001），由 OpsTeamRoleSeedRunner
 * 启动期按 SPI 注册的全量 {@code ops.*}（含本期新增 {@code ops.module.view}）授权。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(OpsBusinessModuleAdminIntegrationTest.TestFixtures.class)
class OpsBusinessModuleAdminIntegrationTest {

    private static final long ROLE_SUPER_ADMIN = 12100001L;
    private static final String OPS_ADMIN = "ops-mod-it-admin";
    private static final String OPS_PLAIN = "ops-mod-it-plain";
    private static final String OPS_PASSWORD = "Admin@2026!";
    private static final String TEST_MODULE = "it_x8_crm";
    private static final String TEST_PKG = "IT_X8_PKG";

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
        fixtures.seedOpsUser(OPS_ADMIN, ROLE_SUPER_ADMIN);
        fixtures.seedOpsUser(OPS_PLAIN, null); // 无角色 → 无 ops.module.view
        fixtures.seedBusinessModule(TEST_MODULE, "IT X8 CRM 模块");
        fixtures.seedPackageWithModule(TEST_PKG, "IT X8 套餐", TEST_MODULE);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    @Test
    void list_returnsRegisteredModules() throws Exception {
        String token = loginOps(OPS_ADMIN);

        mvc.perform(get("/api/v1/ops/business-modules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // 底座虚拟模块 base（由 SPI 启动期同步到 sys_business_module）
                .andExpect(jsonPath("$[?(@.code=='base')]").exists())
                // 本测试种入的模块
                .andExpect(jsonPath("$[?(@.code=='" + TEST_MODULE + "')]").exists());
    }

    @Test
    void detail_base_hasContributionCounts() throws Exception {
        String token = loginOps(OPS_ADMIN);

        // base 模块贡献：base.ticket.view_all / base.compliance.* 权限点 + base.ticket.my_status 工作台卡片
        // （permissions 计数含 BasePermissionContributor 以同 owner 注册的底座租户域权限点）
        mvc.perform(get("/api/v1/ops/business-modules/base")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("base"))
                .andExpect(jsonPath("$.contribution.permissions").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.contribution.dashboardCards").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void detail_relatedPackages_reverseLookup() throws Exception {
        String token = loginOps(OPS_ADMIN);

        mvc.perform(get("/api/v1/ops/business-modules/" + TEST_MODULE)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(TEST_MODULE))
                .andExpect(jsonPath("$.relatedPackages[?(@.code=='" + TEST_PKG + "')]").exists());
    }

    @Test
    void detail_unknownModule_returns404() throws Exception {
        String token = loginOps(OPS_ADMIN);

        mvc.perform(get("/api/v1/ops/business-modules/it_x8_nope")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    @Test
    void list_forbidden_whenMissingPermission() throws Exception {
        String token = loginOps(OPS_PLAIN);

        mvc.perform(get("/api/v1/ops/business-modules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ===================== Helper =====================

    private String loginOps(String username) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", username, "password", OPS_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
    }

    // ===================== Fixture =====================

    @Component
    @CrossTenantQuery("OpsBusinessModuleIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysUserRoleMapper userRoleMapper;
        final SysBusinessModuleMapper businessModuleMapper;
        final SysPackageMapper packageMapper;
        final SysPackageModuleMapper packageModuleMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                     SysBusinessModuleMapper businessModuleMapper,
                     SysPackageMapper packageMapper, SysPackageModuleMapper packageModuleMapper) {
            this.userMapper = userMapper;
            this.userRoleMapper = userRoleMapper;
            this.businessModuleMapper = businessModuleMapper;
            this.packageMapper = packageMapper;
            this.packageModuleMapper = packageModuleMapper;
        }

        void seedOpsUser(String username, Long roleId) {
            SysUser u = new SysUser();
            u.setTenantId(0L);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(OPS_PASSWORD));
            u.setRealName(username);
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);

            if (roleId != null) {
                SysUserRole ur = new SysUserRole();
                ur.setTenantId(0L);
                ur.setUserId(u.getId());
                ur.setRoleId(roleId);
                ur.setCreatedAt(LocalDateTime.now());
                userRoleMapper.insert(ur);
            }
        }

        void seedBusinessModule(String code, String name) {
            if (businessModuleMapper.selectOne(Wrappers.<SysBusinessModule>lambdaQuery()
                    .eq(SysBusinessModule::getCode, code)) == null) {
                SysBusinessModule m = new SysBusinessModule();
                m.setCode(code);
                m.setName(name);
                m.setVersion("1.0.0");
                m.setCreatedAt(LocalDateTime.now());
                m.setUpdatedAt(LocalDateTime.now());
                businessModuleMapper.insert(m);
            }
        }

        void seedPackageWithModule(String pkgCode, String pkgName, String moduleCode) {
            SysPackage p = new SysPackage();
            p.setCode(pkgCode);
            p.setName(pkgName);
            p.setIsSystem(false);
            p.setStatus(1); // active
            packageMapper.insert(p);

            SysPackageModule pm = new SysPackageModule();
            pm.setPackageId(p.getId());
            pm.setModuleCode(moduleCode);
            pm.setCreatedAt(LocalDateTime.now());
            packageModuleMapper.insert(pm);
        }

        void cleanup() {
            List<SysPackage> testPkgs = packageMapper.selectList(Wrappers.<SysPackage>lambdaQuery()
                    .likeRight(SysPackage::getCode, TEST_PKG));
            for (SysPackage p : testPkgs) {
                packageModuleMapper.delete(Wrappers.<SysPackageModule>lambdaQuery()
                        .eq(SysPackageModule::getPackageId, p.getId()));
                packageMapper.deleteById(p.getId());
            }
            businessModuleMapper.delete(Wrappers.<SysBusinessModule>lambdaQuery()
                    .likeRight(SysBusinessModule::getCode, "it_x8_"));
            userMapper.delete(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getTenantId, 0L)
                    .in(SysUser::getUsername, List.of(OPS_ADMIN, OPS_PLAIN)));
        }
    }
}
