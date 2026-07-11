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
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplate;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplateActivation;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplateActivationMapper;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplateMapper;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplatePackage;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplatePackageMapper;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplatePermission;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplatePermissionMapper;
import com.wensaas.core.impl.permission.role.SysRole;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.role.SysUserRole;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.pkg.SysPackage;
import com.wensaas.core.impl.pkg.SysPackageMapper;
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
 * 运营端推荐角色管理集成测试。
 *
 * <p>覆盖：CRUD + 3 态状态机 + 已发布加不减（套餐包）+ 名称唯一 + 复制 + 删除守卫（已启用拒删）
 * + 权限点校验（非 ops.*）+ 套餐包存在校验 + RBAC 鉴权（@RequirePermission）。
 *
 * <p>ops 用户绑定预置 OPS_SUPER_ADMIN（id=12100001），由 OpsTeamRoleSeedRunner
 * 启动期获全部 ops.* 权限点（含 ops.recommended_role.*）。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(RecommendedRoleAdminIntegrationTest.TestFixtures.class)
class RecommendedRoleAdminIntegrationTest {

    private static final long ROLE_SUPER_ADMIN = 12100001L;
    private static final String OPS_USERNAME = "ops-rr-it-admin";
    private static final String OPS_PASSWORD = "Admin@2026!";
    private static final String NAME_PREFIX = "IT_RR_";
    private static final String PKG_A = "IT_RR_PKG_A";
    private static final String PKG_B = "IT_RR_PKG_B";
    private static final String PKG_C = "IT_RR_PKG_C";

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
        fixtures.seedOpsUser(OPS_USERNAME, ROLE_SUPER_ADMIN);
        fixtures.ensurePackage(PKG_A);
        fixtures.ensurePackage(PKG_B);
        fixtures.ensurePackage(PKG_C);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    // ===================== 测试用例 =====================

    @Test
    void createDraft_publish_unpublish_delete() throws Exception {
        String token = loginOps();

        MvcResult created = mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "销售员", 3,
                                List.of(PKG_A), List.of(), false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.dataScope").value(3))
                .andExpect(jsonPath("$.packageCodes[0]").value(PKG_A))
                .andExpect(jsonPath("$.activatedTenantCount").value(0))
                .andReturn();
        long id = JSON.parseObject(created.getResponse().getContentAsString()).getLongValue("id");

        mvc.perform(put("/api/v1/ops/recommended-roles/" + id + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/ops/recommended-roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(put("/api/v1/ops/recommended-roles/" + id + "/unpublish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/ops/recommended-roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        // 从未被启用 → 可删
        mvc.perform(delete("/api/v1/ops/recommended-roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/ops/recommended-roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));
    }

    @Test
    void createWithPublishImmediately_setsPublished() throws Exception {
        String token = loginOps();
        mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "立即发布", 1,
                                List.of(PKG_A), List.of(), true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void duplicateName_returnsConflict() throws Exception {
        String token = loginOps();
        String name = NAME_PREFIX + "重名";
        mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(name, 1, List.of(PKG_A), List.of(), false))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(name, 1, List.of(PKG_B), List.of(), false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_CONFLICT"));
    }

    @Test
    void publishedTemplate_addPackageAllowed_removeRejected() throws Exception {
        String token = loginOps();
        MvcResult created = mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "加不减", 1,
                                List.of(PKG_A, PKG_B), List.of(), true))))
                .andExpect(status().isOk())
                .andReturn();
        long id = JSON.parseObject(created.getResponse().getContentAsString()).getLongValue("id");

        // 加套餐包（保留旧 + 增 C）允许
        mvc.perform(put("/api/v1/ops/recommended-roles/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(updateReq(null, null, null,
                                List.of(PKG_A, PKG_B, PKG_C), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageCodes.length()").value(3));

        // 减套餐包（移除 B）拒绝
        mvc.perform(put("/api/v1/ops/recommended-roles/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(updateReq(null, null, null, List.of(PKG_A), null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PACKAGE_REMOVE_FORBIDDEN"));
    }

    @Test
    void invalidPermissionCode_rejected() throws Exception {
        String token = loginOps();
        // ops.* 权限点不允许出现在推荐角色（租户角色）中
        mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "坏权限", 1,
                                List.of(PKG_A), List.of("ops.package.list"), false))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PERMISSION_CODE"));
    }

    @Test
    void unknownPackage_rejected() throws Exception {
        String token = loginOps();
        mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "坏套餐", 1,
                                List.of("IT_RR_PKG_NOPE"), List.of(), false))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PACKAGE_NOT_FOUND"));
    }

    @Test
    void copy_clonesAsDraft_conflictAndNotFound() throws Exception {
        String token = loginOps();
        MvcResult src = mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "复制源", 2,
                                List.of(PKG_A, PKG_B), List.of(), true))))
                .andExpect(status().isOk())
                .andReturn();
        long srcId = JSON.parseObject(src.getResponse().getContentAsString()).getLongValue("id");

        String dstName = NAME_PREFIX + "复制副本";
        mvc.perform(post("/api/v1/ops/recommended-roles/" + srcId + "/copy")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("name", dstName))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(dstName))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.dataScope").value(2))
                .andExpect(jsonPath("$.packageCodes.length()").value(2));

        // 复制到已存在名 → 409
        mvc.perform(post("/api/v1/ops/recommended-roles/" + srcId + "/copy")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("name", dstName))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_CONFLICT"));

        // 复制不存在的源 → 404
        mvc.perform(post("/api/v1/ops/recommended-roles/99999999/copy")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("name", NAME_PREFIX + "x"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));
    }

    @Test
    void deleteWhenActivated_rejected_andCountReflected() throws Exception {
        String token = loginOps();
        MvcResult created = mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "已启用", 1,
                                List.of(PKG_A), List.of(), true))))
                .andExpect(status().isOk())
                .andReturn();
        long id = JSON.parseObject(created.getResponse().getContentAsString()).getLongValue("id");

        // 模拟某租户已启用该模板
        fixtures.seedActivation(id, 100L);

        mvc.perform(get("/api/v1/ops/recommended-roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activatedTenantCount").value(1));

        mvc.perform(delete("/api/v1/ops/recommended-roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_IN_USE"));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        String token = loginOps();
        mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "DRAFT_ONE", 1,
                                List.of(PKG_A), List.of(), false))));
        mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "PUB_ONE", 1,
                                List.of(PKG_A), List.of(), true))));

        MvcResult res = mvc.perform(get("/api/v1/ops/recommended-roles?status=DRAFT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String body = res.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("DRAFT_ONE");
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("PUB_ONE");
    }

    @Test
    void activations_emptyPageForFreshTemplate() throws Exception {
        String token = loginOps();
        MvcResult created = mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "启用列表", 1,
                                List.of(PKG_A), List.of(), true))))
                .andExpect(status().isOk())
                .andReturn();
        long id = JSON.parseObject(created.getResponse().getContentAsString()).getLongValue("id");

        mvc.perform(get("/api/v1/ops/recommended-roles/" + id + "/activations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));

        // 不存在模板 → 404
        mvc.perform(get("/api/v1/ops/recommended-roles/99999999/activations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));
    }

    @Test
    void activations_carriesRichColumnsAfterTenantActivate() throws Exception {
        String token = loginOps();
        MvcResult created = mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "富列", 1,
                                List.of(PKG_A), List.of(), true))))
                .andExpect(status().isOk())
                .andReturn();
        long id = JSON.parseObject(created.getResponse().getContentAsString()).getLongValue("id");

        // 模拟租户端「启用推荐角色 → 生成快照角色 + 分配 1 名成员」后落库的快照
        fixtures.seedActivatedTenant(id, "IT_RR_T_销售部", "IT_RR_C_客户经理李雷",
                "IT_RR_销售专员快照", 1);

        mvc.perform(get("/api/v1/ops/recommended-roles/" + id + "/activations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].tenantName").value("IT_RR_T_销售部"))
                .andExpect(jsonPath("$.items[0].csmName").value("IT_RR_C_客户经理李雷"))
                .andExpect(jsonPath("$.items[0].roleName").value("IT_RR_销售专员快照"))
                .andExpect(jsonPath("$.items[0].memberCount").value(1))
                .andExpect(jsonPath("$.items[0].createdRoleId").isNotEmpty());
    }

    @Test
    void detail_carriesPackageTenantCountAndArchivedFlags() throws Exception {
        String token = loginOps();
        MvcResult created = mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "富详情", 1,
                                List.of(PKG_A), List.of(), false))))
                .andExpect(status().isOk())
                .andReturn();
        long id = JSON.parseObject(created.getResponse().getContentAsString()).getLongValue("id");

        mvc.perform(get("/api/v1/ops/recommended-roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packages").isArray())
                .andExpect(jsonPath("$.packages[0].code").value(PKG_A))
                .andExpect(jsonPath("$.packages[0].tenantCount").value(0))
                .andExpect(jsonPath("$.packages[0].archived").value(false))
                .andExpect(jsonPath("$.allPackagesArchived").value(false))
                .andExpect(jsonPath("$.invalidPermissionCodes").isArray())
                .andExpect(jsonPath("$.invalidPermissionCodes.length()").value(0));
    }

    @Test
    void detail_flagsInvalidPermissionCodes() throws Exception {
        String token = loginOps();
        MvcResult created = mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "失效权限", 1,
                                List.of(PKG_A), List.of(), false))))
                .andExpect(status().isOk())
                .andReturn();
        long id = JSON.parseObject(created.getResponse().getContentAsString()).getLongValue("id");

        // 直接落库一条未在权限注册表中的权限点（模拟业务模块卸载后历史绑定残留）
        fixtures.seedPermission(id, "crm.customer.ghost");

        mvc.perform(get("/api/v1/ops/recommended-roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invalidPermissionCodes[?(@=='crm.customer.ghost')]").exists());
    }

    @Test
    void allPackagesArchived_lazyInactivates_andPublishRejected() throws Exception {
        String token = loginOps();
        MvcResult created = mvc.perform(post("/api/v1/ops/recommended-roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(createReq(NAME_PREFIX + "全归档", 1,
                                List.of(PKG_A), List.of(), true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andReturn();
        long id = JSON.parseObject(created.getResponse().getContentAsString()).getLongValue("id");

        // 归档其唯一关联套餐包
        fixtures.setPackageStatus(PKG_A, 2); // ARCHIVED

        // 读取时惰性翻转 PUBLISHED → INACTIVE，且 allPackagesArchived=true
        mvc.perform(get("/api/v1/ops/recommended-roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.allPackagesArchived").value(true))
                .andExpect(jsonPath("$.packages[0].archived").value(true));

        // 再发布被拒（关联包全归档）
        mvc.perform(put("/api/v1/ops/recommended-roles/" + id + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void permissionCatalog_and_availablePackages() throws Exception {
        String token = loginOps();
        // 权限目录树（sys_menu 派生的 catalogTree：tree + unbound 两个数组）
        mvc.perform(get("/api/v1/ops/recommended-roles/permission-catalog")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tree").isArray())
                .andExpect(jsonPath("$.unbound").isArray());
        // 可关联套餐包含已种入的 A/B/C
        mvc.perform(get("/api/v1/ops/recommended-roles/available-packages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='" + PKG_A + "')]").exists())
                .andExpect(jsonPath("$[?(@.code=='" + PKG_B + "')]").exists());
    }

    @Test
    void noToken_returnsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/ops/recommended-roles"))
                .andExpect(status().isUnauthorized());
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

    private static Map<String, Object> createReq(String name, int dataScope, List<String> packages,
                                                  List<String> permissions, boolean publish) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("description", null);
        m.put("dataScope", dataScope);
        m.put("packageCodes", packages);
        m.put("permissionCodes", permissions);
        m.put("publishImmediately", publish);
        return m;
    }

    private static Map<String, Object> updateReq(String name, String desc, Integer dataScope,
                                                  List<String> packages, List<String> permissions) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("description", desc);
        m.put("dataScope", dataScope);
        m.put("packageCodes", packages);
        m.put("permissionCodes", permissions);
        return m;
    }

    // ===================== Fixture =====================

    @Component
    @CrossTenantQuery("RecommendedRoleAdminIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysUserRoleMapper userRoleMapper;
        final SysPackageMapper packageMapper;
        final SysRecommendedRoleTemplateMapper templateMapper;
        final SysRecommendedRoleTemplatePackageMapper templatePackageMapper;
        final SysRecommendedRoleTemplatePermissionMapper templatePermissionMapper;
        final SysRecommendedRoleTemplateActivationMapper templateActivationMapper;
        final SysTenantMapper tenantMapper;
        final SysRoleMapper roleMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                     SysPackageMapper packageMapper,
                     SysRecommendedRoleTemplateMapper templateMapper,
                     SysRecommendedRoleTemplatePackageMapper templatePackageMapper,
                     SysRecommendedRoleTemplatePermissionMapper templatePermissionMapper,
                     SysRecommendedRoleTemplateActivationMapper templateActivationMapper,
                     SysTenantMapper tenantMapper, SysRoleMapper roleMapper) {
            this.userMapper = userMapper;
            this.userRoleMapper = userRoleMapper;
            this.packageMapper = packageMapper;
            this.templateMapper = templateMapper;
            this.templatePackageMapper = templatePackageMapper;
            this.templatePermissionMapper = templatePermissionMapper;
            this.templateActivationMapper = templateActivationMapper;
            this.tenantMapper = tenantMapper;
            this.roleMapper = roleMapper;
        }

        void seedOpsUser(String username, long roleId) {
            SysUser u = new SysUser();
            u.setTenantId(0L);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(OPS_PASSWORD));
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

        void ensurePackage(String code) {
            if (packageMapper.selectOne(Wrappers.<SysPackage>lambdaQuery()
                    .eq(SysPackage::getCode, code)) == null) {
                SysPackage p = new SysPackage();
                p.setCode(code);
                p.setName(code);
                p.setIsSystem(false);
                p.setStatus(1); // active
                packageMapper.insert(p);
            }
        }

        void seedActivation(long templateId, long tenantId) {
            SysRecommendedRoleTemplateActivation a = new SysRecommendedRoleTemplateActivation();
            a.setTemplateId(templateId);
            a.setTenantId(tenantId);
            a.setActivatedAt(LocalDateTime.now());
            templateActivationMapper.insert(a);
        }

        /**
         * 模拟租户端「启用推荐角色」落库的整套快照：租户 + 客户经理 + 快照角色 + N 名成员分配 + 启用登记。
         * 命名一律带 IT_RR_ 前缀，便于 {@link #cleanup()} 清理。
         */
        void seedActivatedTenant(long templateId, String tenantName, String csmRealName,
                                 String roleName, int memberCount) {
            // 客户经理（运营侧用户，tenantId=0；展示仅取 realName）
            SysUser csm = new SysUser();
            csm.setTenantId(0L);
            csm.setUsername("IT_RR_csm_" + tenantName);
            csm.setPasswordHash(hasher.hash(OPS_PASSWORD));
            csm.setRealName(csmRealName);
            csm.setStatus(1);
            csm.setCreatedAt(LocalDateTime.now());
            csm.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(csm);

            SysTenant tenant = new SysTenant();
            tenant.setCode("IT_RR_T_" + System.identityHashCode(tenantName));
            tenant.setName(tenantName);
            tenant.setStatus(1);
            tenant.setCsmUserId(csm.getId());
            tenant.setCreatedAt(LocalDateTime.now());
            tenant.setUpdatedAt(LocalDateTime.now());
            tenantMapper.insert(tenant);

            SysRole role = new SysRole();
            role.setTenantId(tenant.getId());
            role.setCode("IT_RR_ROLE_" + System.identityHashCode(roleName));
            role.setName(roleName);
            role.setStatus(1);
            role.setCreatedAt(LocalDateTime.now());
            role.setUpdatedAt(LocalDateTime.now());
            roleMapper.insert(role);

            for (int i = 0; i < memberCount; i++) {
                SysUser member = new SysUser();
                member.setTenantId(tenant.getId());
                member.setUsername("IT_RR_m_" + tenant.getId() + "_" + i);
                member.setPasswordHash(hasher.hash(OPS_PASSWORD));
                member.setRealName("IT_RR_成员" + i);
                member.setStatus(1);
                member.setCreatedAt(LocalDateTime.now());
                member.setUpdatedAt(LocalDateTime.now());
                userMapper.insert(member);

                SysUserRole ur = new SysUserRole();
                ur.setTenantId(tenant.getId());
                ur.setUserId(member.getId());
                ur.setRoleId(role.getId());
                ur.setCreatedAt(LocalDateTime.now());
                userRoleMapper.insert(ur);
            }

            SysRecommendedRoleTemplateActivation a = new SysRecommendedRoleTemplateActivation();
            a.setTemplateId(templateId);
            a.setTenantId(tenant.getId());
            a.setCreatedRoleId(role.getId());
            a.setActivatedAt(LocalDateTime.now());
            templateActivationMapper.insert(a);
        }

        /** 直接落库一条权限绑定（绕过 Service 校验，用于模拟业务模块卸载后的失效残留）。 */
        void seedPermission(long templateId, String code) {
            SysRecommendedRoleTemplatePermission p = new SysRecommendedRoleTemplatePermission();
            p.setTemplateId(templateId);
            p.setPermissionCode(code);
            templatePermissionMapper.insert(p);
        }

        /** 改套餐状态（0=draft / 1=active / 2=archived / 3=inactive）。 */
        void setPackageStatus(String code, int status) {
            SysPackage p = packageMapper.selectOne(Wrappers.<SysPackage>lambdaQuery()
                    .eq(SysPackage::getCode, code));
            if (p != null) {
                p.setStatus(status);
                packageMapper.updateById(p);
            }
        }

        void cleanup() {
            // 清模板及其关联 / 启用 / 权限行（按名称前缀定位模板）
            List<SysRecommendedRoleTemplate> tpls = templateMapper.selectList(
                    Wrappers.<SysRecommendedRoleTemplate>lambdaQuery()
                            .likeRight(SysRecommendedRoleTemplate::getName, NAME_PREFIX));
            for (SysRecommendedRoleTemplate t : tpls) {
                templatePackageMapper.delete(Wrappers.<SysRecommendedRoleTemplatePackage>lambdaQuery()
                        .eq(SysRecommendedRoleTemplatePackage::getTemplateId, t.getId()));
                templatePermissionMapper.delete(Wrappers.<SysRecommendedRoleTemplatePermission>lambdaQuery()
                        .eq(SysRecommendedRoleTemplatePermission::getTemplateId, t.getId()));
                templateActivationMapper.delete(Wrappers.<SysRecommendedRoleTemplateActivation>lambdaQuery()
                        .eq(SysRecommendedRoleTemplateActivation::getTemplateId, t.getId()));
                templateMapper.deleteById(t.getId());
            }
            // 清测试套餐包
            packageMapper.delete(Wrappers.<SysPackage>lambdaQuery()
                    .likeRight(SysPackage::getCode, "IT_RR_PKG_"));
            // 清 seedActivatedTenant 落库的快照租户 / 快照角色 / csm + 成员（均 IT_RR_ 前缀）
            List<SysUser> seededUsers = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                    .likeRight(SysUser::getUsername, "IT_RR_csm_")
                    .or().likeRight(SysUser::getUsername, "IT_RR_m_"));
            for (SysUser su : seededUsers) {
                userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, su.getId()));
                userMapper.deleteById(su.getId());
            }
            roleMapper.delete(Wrappers.<SysRole>lambdaQuery()
                    .likeRight(SysRole::getCode, "IT_RR_ROLE_"));
            tenantMapper.delete(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, "IT_RR_T_"));
            // 清 ops 用户 + 角色绑定
            SysUser u = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getTenantId, 0L)
                    .eq(SysUser::getUsername, OPS_USERNAME));
            if (u != null) {
                userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, u.getId()));
                userMapper.deleteById(u.getId());
            }
        }
    }
}
