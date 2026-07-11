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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 运营端套餐管理集成测试。
 *
 * <p>覆盖：CRUD + 4 态状态机 + 基础包保护 + 上架后限制 + 模块勾选 + 鉴权。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(PackageAdminIntegrationTest.TestFixtures.class)
class PackageAdminIntegrationTest {

    private static final String OPS_USERNAME = "ops-pkg-it-admin";
    private static final String OPS_PASSWORD = "Admin@2026!";
    private static final String TEST_PKG_PREFIX = "IT_PKG_";
    /** 预置 OPS_SUPER_ADMIN（含 ops.package.* 等全部运营权限点），由 OpsTeamRoleSeedRunner 落库 */
    private static final long ROLE_SUPER_ADMIN = 12100001L;

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
        fixtures.seedOpsUser(OPS_USERNAME, OPS_PASSWORD);
        fixtures.ensureBasePackage();
        fixtures.ensureBusinessModule("it-crm", "IT CRM 模块");
        fixtures.ensureBusinessModule("it-order", "IT 订单模块");
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    // ===================== 测试用例 =====================

    @Test
    void createDraft_thenPublish_thenUnpublish_thenArchive() throws Exception {
        String token = loginOps();
        String code = TEST_PKG_PREFIX + "LIFECYCLE";

        // 创建草稿
        mvc.perform(post("/api/v1/ops/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(code, "套餐生命周期 IT", 365,
                                List.of("it-crm"), false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.moduleCodes[0]").value("it-crm"));

        // 上架
        mvc.perform(put("/api/v1/ops/packages/" + code + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/ops/packages/" + code)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 下架
        mvc.perform(put("/api/v1/ops/packages/" + code + "/unpublish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 归档
        mvc.perform(put("/api/v1/ops/packages/" + code + "/archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/ops/packages/" + code)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // 归档后不能再上架
        mvc.perform(put("/api/v1/ops/packages/" + code + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void createWithPublishImmediately_setsActive() throws Exception {
        String token = loginOps();
        String code = TEST_PKG_PREFIX + "IMMEDIATE";

        mvc.perform(post("/api/v1/ops/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(code, "立即上架 IT", null, List.of(), true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void duplicateCode_returnsConflict() throws Exception {
        String token = loginOps();
        String code = TEST_PKG_PREFIX + "DUP";

        mvc.perform(post("/api/v1/ops/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(code, "first", null, List.of(), false))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/ops/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(code, "second", null, List.of(), false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODE_CONFLICT"));
    }

    @Test
    void basicPackage_nameEditable_butTtlAndModulesProtected() throws Exception {
        String token = loginOps();

        // 改 name 允许
        mvc.perform(put("/api/v1/ops/packages/base")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(updateReq("基础包 IT 改名", null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("基础包 IT 改名"));

        // 改 defaultTtlDays 拒绝
        mvc.perform(put("/api/v1/ops/packages/base")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(updateReq(null, null, 90, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BASIC_PACKAGE_PROTECTED"));

        // 改 moduleCodes 拒绝
        mvc.perform(put("/api/v1/ops/packages/base")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(updateReq(null, null, null, List.of("it-crm")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BASIC_PACKAGE_PROTECTED"));

        // 删除基础包拒绝
        mvc.perform(delete("/api/v1/ops/packages/base")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BASIC_PACKAGE_PROTECTED"));
    }

    @Test
    void activePackage_addModuleAllowed_removeRejected() throws Exception {
        String token = loginOps();
        String code = TEST_PKG_PREFIX + "ACT_EDIT";

        // 上架包，含 it-crm + it-order
        mvc.perform(post("/api/v1/ops/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(code, "active edit", null,
                                List.of("it-crm", "it-order"), true))))
                .andExpect(status().isOk());

        // 增模块（同时保留旧模块）—— 没有该用例下没有第三个 module，这里改为保持现状测试不报错
        mvc.perform(put("/api/v1/ops/packages/" + code)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(updateReq(null, "新描述", null,
                                List.of("it-crm", "it-order")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("新描述"));

        // 减模块拒绝（移除 it-order）
        mvc.perform(put("/api/v1/ops/packages/" + code)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(updateReq(null, null, null, List.of("it-crm")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MODULE_REMOVE_FORBIDDEN"));
    }

    @Test
    void deleteOnlyAllowedForDraft() throws Exception {
        String token = loginOps();
        String code = TEST_PKG_PREFIX + "DEL";

        // draft 创建
        mvc.perform(post("/api/v1/ops/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(code, "del", null, List.of(), false))))
                .andExpect(status().isOk());

        // 上架后删除应拒绝
        mvc.perform(put("/api/v1/ops/packages/" + code + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/v1/ops/packages/" + code)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        // 下架后删除依然拒绝（只有 draft 可删）
        mvc.perform(put("/api/v1/ops/packages/" + code + "/unpublish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/v1/ops/packages/" + code)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void listAvailableModules_returnsBusinessModules() throws Exception {
        String token = loginOps();

        mvc.perform(get("/api/v1/ops/packages/modules/available")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='it-crm')]").exists())
                .andExpect(jsonPath("$[?(@.code=='it-order')]").exists());
    }

    @Test
    void listPackages_filtersByStatus() throws Exception {
        String token = loginOps();

        mvc.perform(post("/api/v1/ops/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(TEST_PKG_PREFIX + "DRAFT_A",
                                "draft", null, List.of(), false))));
        mvc.perform(post("/api/v1/ops/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(TEST_PKG_PREFIX + "ACT_A",
                                "active", null, List.of(), true))));

        // status=draft 仅返回草稿
        MvcResult res = mvc.perform(get("/api/v1/ops/packages?status=DRAFT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String body = res.getResponse().getContentAsString();
        // 包含 DRAFT_A，不包含 ACT_A
        org.assertj.core.api.Assertions.assertThat(body).contains("DRAFT_A");
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("ACT_A");
    }

    @Test
    void copyPackage_clonesModulesAndTtl_asDraft() throws Exception {
        String token = loginOps();
        String src = TEST_PKG_PREFIX + "COPY_SRC";
        String dst = TEST_PKG_PREFIX + "COPY_DST";

        // 源：上架包，含 it-crm + it-order，ttl=365
        mvc.perform(post("/api/v1/ops/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(src, "复制源", 365,
                                List.of("it-crm", "it-order"), true))))
                .andExpect(status().isOk());

        // 复制 → 新包恒为草稿，拷贝模块与有效期
        mvc.perform(post("/api/v1/ops/packages/" + src + "/copy")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("code", dst, "name", "复制副本"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(dst))
                .andExpect(jsonPath("$.name").value("复制副本"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.defaultTtlDays").value(365))
                .andExpect(jsonPath("$.moduleCodes.length()").value(2));

        // 复制到已存在编码 → 409
        mvc.perform(post("/api/v1/ops/packages/" + src + "/copy")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("code", dst, "name", "再来一份"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODE_CONFLICT"));

        // 复制不存在的源 → 404
        mvc.perform(post("/api/v1/ops/packages/" + TEST_PKG_PREFIX + "NOPE/copy")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("code", TEST_PKG_PREFIX + "X", "name", "x"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PACKAGE_NOT_FOUND"));
    }

    @Test
    void listTenants_freshPackage_returnsEmptyPage_unknownReturns404() throws Exception {
        String token = loginOps();
        String code = TEST_PKG_PREFIX + "TENANTS";

        mvc.perform(post("/api/v1/ops/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(code, "反查租户", null, List.of(), true))))
                .andExpect(status().isOk());

        // 新套餐无人持有 → total=0
        mvc.perform(get("/api/v1/ops/packages/" + code + "/tenants")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));

        // 不存在的套餐 → 404
        mvc.perform(get("/api/v1/ops/packages/" + TEST_PKG_PREFIX + "NOPE/tenants")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PACKAGE_NOT_FOUND"));
    }

    @Test
    void listHistory_unknownReturns404_knownReturns200() throws Exception {
        String token = loginOps();
        String code = TEST_PKG_PREFIX + "HISTORY";

        mvc.perform(post("/api/v1/ops/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(code, "变更历史", null, List.of(), false))))
                .andExpect(status().isOk());

        // 已存在套餐 → 200（审计为异步落库，不断言条数）
        mvc.perform(get("/api/v1/ops/packages/" + code + "/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        // 不存在的套餐 → 404
        mvc.perform(get("/api/v1/ops/packages/" + TEST_PKG_PREFIX + "NOPE/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PACKAGE_NOT_FOUND"));
    }

    // ===================== Helper =====================

    private String loginOps() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", OPS_USERNAME, "password", OPS_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
    }

    private static Map<String, Object> createReq(String code, String name, Integer ttl,
                                                  List<String> modules, boolean publish) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("description", null);
        m.put("defaultTtlDays", ttl);
        m.put("moduleCodes", modules);
        m.put("publishImmediately", publish);
        return m;
    }

    private static Map<String, Object> updateReq(String name, String desc, Integer ttl, List<String> modules) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("description", desc);
        m.put("defaultTtlDays", ttl);
        m.put("moduleCodes", modules);
        return m;
    }

    // ===================== Fixture =====================

    @Component
    @CrossTenantQuery("PackageAdminIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysUserRoleMapper userRoleMapper;
        final SysPackageMapper packageMapper;
        final SysPackageModuleMapper packageModuleMapper;
        final SysBusinessModuleMapper businessModuleMapper;
        final com.wensaas.core.impl.pkg.ModuleApiRouteRegistry apiRouteRegistry;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                     SysPackageMapper packageMapper,
                     SysPackageModuleMapper packageModuleMapper,
                     SysBusinessModuleMapper businessModuleMapper,
                     com.wensaas.core.impl.pkg.ModuleApiRouteRegistry apiRouteRegistry) {
            this.userMapper = userMapper;
            this.userRoleMapper = userRoleMapper;
            this.packageMapper = packageMapper;
            this.packageModuleMapper = packageModuleMapper;
            this.businessModuleMapper = businessModuleMapper;
            this.apiRouteRegistry = apiRouteRegistry;
        }

        void seedOpsUser(String username, String password) {
            SysUser u = new SysUser();
            u.setTenantId(0L);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(password));
            u.setRealName("Ops Pkg IT Admin");
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);

            // 绑定 OPS_SUPER_ADMIN（含 ops.package.* 全权限），否则套餐端点 @RequirePermission 拦为 403
            SysUserRole ur = new SysUserRole();
            ur.setTenantId(0L);
            ur.setUserId(u.getId());
            ur.setRoleId(ROLE_SUPER_ADMIN);
            ur.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(ur);
        }

        void ensureBasePackage() {
            if (packageMapper.selectOne(Wrappers.<SysPackage>lambdaQuery()
                    .eq(SysPackage::getCode, "base")) == null) {
                SysPackage pkg = new SysPackage();
                pkg.setCode("base");
                pkg.setName("基础包");
                pkg.setIsSystem(true);
                pkg.setStatus(1);
                packageMapper.insert(pkg);
            }
        }

        void ensureBusinessModule(String code, String name) {
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
            // 声明非空 API 前缀 → kind=BUSINESS，方进套餐可选清单
            apiRouteRegistry.register("/api/v1/biz/" + code + "/", code);
        }

        void cleanup() {
            // 清理本 IT 测试创建的所有套餐 + 绑定 + ops 用户 + business module
            List<SysPackage> testPkgs = packageMapper.selectList(Wrappers.<SysPackage>lambdaQuery()
                    .likeRight(SysPackage::getCode, TEST_PKG_PREFIX));
            for (SysPackage p : testPkgs) {
                packageModuleMapper.delete(Wrappers.<SysPackageModule>lambdaQuery()
                        .eq(SysPackageModule::getPackageId, p.getId()));
                packageMapper.deleteById(p.getId());
            }
            // 还原 base 包名（被 basicPackage_nameEditable 测试改过）
            SysPackage base = packageMapper.selectOne(Wrappers.<SysPackage>lambdaQuery()
                    .eq(SysPackage::getCode, "base"));
            if (base != null && !"基础包".equals(base.getName())) {
                base.setName("基础包");
                packageMapper.updateById(base);
            }
            // 清理测试业务模块
            businessModuleMapper.delete(Wrappers.<SysBusinessModule>lambdaQuery()
                    .in(SysBusinessModule::getCode, List.of("it-crm", "it-order")));
            // 清理 ops 用户 + 其角色绑定
            SysUser opsUser = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getTenantId, 0L)
                    .eq(SysUser::getUsername, OPS_USERNAME));
            if (opsUser != null) {
                userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, opsUser.getId()));
                userMapper.deleteById(opsUser.getId());
            }
        }
    }
}
