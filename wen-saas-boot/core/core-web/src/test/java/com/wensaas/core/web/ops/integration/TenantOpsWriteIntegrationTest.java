package com.wensaas.core.web.ops.integration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.auth.filter.AuthenticationFilter;
import com.wensaas.core.impl.auth.password.BCryptPasswordHasher;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.ops.OpsAuthWebFilter;
import com.wensaas.core.impl.ops.SysTenantSeatAddonMapper;
import com.wensaas.core.impl.permission.role.SysRole;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 运营端租户管理写操作集成测试。
 *
 * <p>覆盖：plans CRUD / seat-addon / 重新指定主管理员 / 编辑名称备注 / OpsAuthWebFilter 拦截
 * / 运营数据范围守卫（OPS_OWNED 单租户读写端点：非名下 403、名下放行）。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(TenantOpsWriteIntegrationTest.TestFixtures.class)
class TenantOpsWriteIntegrationTest {

    private static final String OPS_USERNAME   = "ops-write-it-admin";
    private static final String CSM_USERNAME   = "ops-write-it-csm";
    private static final String OPS_PASSWORD   = "Admin@2026!";
    private static final String TENANT_CODE    = "ops-write-it";
    private static final String ADMIN_USERNAME = "ops-write-it-mgr";
    private static final String BIZ_PKG_CODE   = "BIZ_TEST_PKG";

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
        fixtures.seedOpsUser(OPS_USERNAME, OPS_PASSWORD, TestFixtures.ROLE_SUPER_ADMIN);
        fixtures.ensureBasePackage();
        fixtures.ensureBizPackage(BIZ_PKG_CODE);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    // ===== 辅助 =====

    private String loginOps() throws Exception {
        return login(OPS_USERNAME);
    }

    private String login(String username) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", username, "password", OPS_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
    }

    /** 开通一个租户，返回 tenantId。 */
    private String createTenant(String opsToken) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/ops/tenants")
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of(
                                "tenantName", "Ops Write IT Tenant",
                                "tenantCode", TENANT_CODE,
                                "adminUsername", ADMIN_USERNAME,
                                "adminRealName", "IT Manager",
                                "adminMobile", "13800138000",
                                "adminEmail", "ops-write-it@example.com",
                                "seatAddonQty", 0,
                                "packageCodes", List.of()))))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("tenantId");
    }

    // ===== 测试用例 =====

    @Test
    void createTenant_omitSeatAddonQty_succeeds() throws Exception {
        String token = loginOps();
        // 回归：seatAddonQty 契约可选，省略不应 400。之前 record 用原始 int，
        // Jackson 反序列化缺失字段 → HttpMessageNotReadableException("Failed to read request")。
        mvc.perform(post("/api/v1/ops/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of(
                                "tenantName", "Ops Write IT Tenant",
                                "tenantCode", TENANT_CODE,
                                "adminUsername", ADMIN_USERNAME,
                                "adminRealName", "IT Manager",
                                "adminMobile", "13800138000",
                                "adminEmail", "ops-write-it@example.com",
                                "packageCodes", List.of()))))
                .andExpect(status().isOk());
    }

    @Test
    void assignRevokeRenewPlan_happyPath() throws Exception {
        String token = loginOps();
        String tenantId = createTenant(token);

        // 1. 勾选套餐包
        mvc.perform(post("/api/v1/ops/tenants/" + tenantId + "/plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("packageCode", BIZ_PKG_CODE, "ttlDays", 30))))
                .andExpect(status().isNoContent());

        // 2. 验证已勾选
        mvc.perform(get("/api/v1/ops/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans[?(@.code=='" + BIZ_PKG_CODE + "')].readonly").value(false));

        // 3. 续费
        mvc.perform(post("/api/v1/ops/tenants/" + tenantId + "/plans/" + BIZ_PKG_CODE + "/renewals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("ttlDays", 90))))
                .andExpect(status().isNoContent());

        // 4. 取消勾选
        mvc.perform(delete("/api/v1/ops/tenants/" + tenantId + "/plans/" + BIZ_PKG_CODE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("reason", "IT 测试取消", "notifyAdmin", false))))
                .andExpect(status().isNoContent());

        // 5. 验证已取消（plans 中该包的 revokedAt != null，所以不再出现在激活 plans 中）
        mvc.perform(get("/api/v1/ops/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // plans 列表中 BIZ_TEST_PKG 不应该以 readonly=false 出现（已撤销不显示）
                .andExpect(jsonPath("$.plans[?(@.code=='" + BIZ_PKG_CODE + "')]").isEmpty());
    }

    @Test
    void addSeatAddon_increasesSeatCount() throws Exception {
        String token = loginOps();
        String tenantId = createTenant(token);

        // 初始 totalQuantity 应为 0（开通时写入 qty=0 记录）
        mvc.perform(get("/api/v1/ops/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatAddons.totalQuantity").value(0));

        // 加购 10 席位
        mvc.perform(post("/api/v1/ops/tenants/" + tenantId + "/seat-addons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("quantity", 10, "remark", "IT 升级"))))
                .andExpect(status().isNoContent());

        // 再加购 5 席位
        mvc.perform(post("/api/v1/ops/tenants/" + tenantId + "/seat-addons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("quantity", 5, "remark", "IT 二次加购"))))
                .andExpect(status().isNoContent());

        // 总席位 = 0 + 10 + 5 = 15
        mvc.perform(get("/api/v1/ops/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatAddons.totalQuantity").value(15))
                .andExpect(jsonPath("$.seatAddons.history.length()").value(3));
    }

    @Test
    void updateNameAndRemark() throws Exception {
        String token = loginOps();
        String tenantId = createTenant(token);

        // 编辑名称
        mvc.perform(patch("/api/v1/ops/tenants/" + tenantId + "/name")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("newName", "新租户名称 IT", "reason", "合同变更"))))
                .andExpect(status().isNoContent());

        // 编辑备注
        mvc.perform(patch("/api/v1/ops/tenants/" + tenantId + "/remark")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("remark", "IT 测试备注"))))
                .andExpect(status().isNoContent());

        // 验证
        mvc.perform(get("/api/v1/ops/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant.name").value("新租户名称 IT"))
                .andExpect(jsonPath("$.tenant.remark").value("IT 测试备注"));
    }

    @Test
    void transferMainAdmin_existingUser() throws Exception {
        String token = loginOps();
        String tenantId = createTenant(token);

        // 在该租户下创建另一个成员
        String member2Id = fixtures.seedTenantMember(Long.parseLong(tenantId), "member2-it");

        // 重新指定主管理员为 member2
        MvcResult res = mvc.perform(post("/api/v1/ops/tenants/" + tenantId + "/main-admin")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of(
                                "mode", "existing",
                                "userId", member2Id,
                                "oldAdminHandling", "demote_admin"))))
                .andExpect(status().isOk())
                .andReturn();

        JSONObject body = JSON.parseObject(res.getResponse().getContentAsString());
        assertThat(body.getString("newAdminUserId")).isEqualTo(member2Id);
        assertThat(body.getString("newAdminUsername")).isEqualTo("member2-it");
        assertThat(body.getString("resetToken")).isNull();

        // sys_tenant.main_admin_user_id 已更新
        SysTenant tenant = fixtures.findTenantById(Long.parseLong(tenantId));
        assertThat(String.valueOf(tenant.getMainAdminUserId())).isEqualTo(member2Id);
    }

    @Test
    void transferMainAdmin_newUser_returnsResetToken() throws Exception {
        String token = loginOps();
        String tenantId = createTenant(token);

        MvcResult res = mvc.perform(post("/api/v1/ops/tenants/" + tenantId + "/main-admin")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of(
                                "mode", "new",
                                "username", "new-admin-it",
                                "realName", "New Admin IT",
                                "mobile", "13800138001",
                                "email", "new-admin@example.com",
                                "oldAdminHandling", "demote_admin"))))
                .andExpect(status().isOk())
                .andReturn();

        JSONObject body = JSON.parseObject(res.getResponse().getContentAsString());
        assertThat(body.getString("newAdminUsername")).isEqualTo("new-admin-it");
        assertThat(body.getString("resetToken")).isNotBlank();
        assertThat(body.getString("resetExpiresAt")).isNotBlank();
    }

    @Test
    void opsAuthWebFilter_rejectsNonOpsUser() throws Exception {
        // 先用运营账号开通一个租户，再用其主管理员账号（tenant_id != 0）调 ops 接口
        String opsToken = loginOps();
        String tenantId = createTenant(opsToken);

        // 创建租户成员账号并激活（设置密码）
        String tenantUserToken = fixtures.seedAndLoginTenantUser(mvc,
                Long.parseLong(tenantId), "tenant-user-it", "Pass@2026!");

        // 用租户账号调 ops 接口应得到 403
        mvc.perform(get("/api/v1/ops/tenants")
                        .header("Authorization", "Bearer " + tenantUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OPS_FORBIDDEN"));
    }

    @Test
    void csmOwnedScope_nonOwnedTenant_returns403() throws Exception {
        // 超管开通租户（csm 未归属该 CSM）→ OWNED 客户经理直连单租户读写端点必须被数据范围守卫拒绝
        String adminToken = loginOps();
        String tenantId = createTenant(adminToken);
        fixtures.seedOpsUser(CSM_USERNAME, OPS_PASSWORD, TestFixtures.ROLE_CSM);
        String csmToken = login(CSM_USERNAME);
        String base = "/api/v1/ops/tenants/" + tenantId;

        // 暂停（状态）
        mvc.perform(post(base + "/suspend").header("Authorization", "Bearer " + csmToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // 编辑名称
        mvc.perform(patch(base + "/name").header("Authorization", "Bearer " + csmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("newName", "越权改名", "reason", "test"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // 勾选套餐
        mvc.perform(post(base + "/plans").header("Authorization", "Bearer " + csmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("packageCode", BIZ_PKG_CODE, "ttlDays", 30))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // 加购席位
        mvc.perform(post(base + "/seat-addons").header("Authorization", "Bearer " + csmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("quantity", 5, "remark", "越权加购"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // 重发密码重置链接
        mvc.perform(post(base + "/password-reset-link").header("Authorization", "Bearer " + csmToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // 解除登录锁定
        mvc.perform(delete(base + "/members/1/login-lock").header("Authorization", "Bearer " + csmToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // 更换主管理员
        mvc.perform(post(base + "/main-admin").header("Authorization", "Bearer " + csmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of(
                                "mode", "new", "username", "hijack-admin-it",
                                "mobile", "13800138009", "oldAdminHandling", "demote_admin"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // 代登录
        mvc.perform(post(base + "/impersonate").header("Authorization", "Bearer " + csmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("asUserId", "1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IMPERSONATE_TENANT_NOT_IN_SCOPE"));

        // 单租户读端点同样收敛：成员列表 / 使用情况 / 变更历史
        mvc.perform(get(base + "/members").header("Authorization", "Bearer " + csmToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get(base + "/usage").header("Authorization", "Bearer " + csmToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get(base + "/events").header("Authorization", "Bearer " + csmToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // 明文联系方式：CSM 预置角色本就无 ops.tenant.contact.reveal 权限点（权限层 403）；
        // service 层守卫兜底自建 OWNED 角色带该权限的场景，这里只断言状态
        mvc.perform(get(base + "/members/1/contact").header("Authorization", "Bearer " + csmToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void csmOwnedScope_ownedTenant_succeeds() throws Exception {
        // 归属到自己（csm_user_id=self）的租户，OWNED 客户经理读写操作正常放行
        String adminToken = loginOps();
        String tenantId = createTenant(adminToken);
        long csmId = fixtures.seedOpsUser(CSM_USERNAME, OPS_PASSWORD, TestFixtures.ROLE_CSM);
        fixtures.setTenantCsm(Long.parseLong(tenantId), csmId);
        String csmToken = login(CSM_USERNAME);
        String base = "/api/v1/ops/tenants/" + tenantId;

        // 读：成员列表 / 变更历史
        mvc.perform(get(base + "/members").header("Authorization", "Bearer " + csmToken))
                .andExpect(status().isOk());
        mvc.perform(get(base + "/events").header("Authorization", "Bearer " + csmToken))
                .andExpect(status().isOk());
        // 写：备注 / 暂停
        mvc.perform(patch(base + "/remark").header("Authorization", "Bearer " + csmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("remark", "名下租户备注 IT"))))
                .andExpect(status().isNoContent());
        mvc.perform(post(base + "/suspend").header("Authorization", "Bearer " + csmToken))
                .andExpect(status().isNoContent());
    }

    // ===== Helper / Fixture Bean =====

    @Component
    @CrossTenantQuery("TenantOpsWriteIT: test fixture")
    static class TestFixtures {

        final SysTenantMapper tenantMapper;
        final SysUserMapper userMapper;
        final SysRoleMapper roleMapper;
        final SysUserRoleMapper userRoleMapper;
        final SysTenantSeatAddonMapper seatAddonMapper;
        final SysPackageMapper packageMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();
        /** 预置 OPS_SUPER_ADMIN（含 ops.tenant.* 等全部运营权限点，data_scope=ALL） */
        static final long ROLE_SUPER_ADMIN = 12100001L;
        /** 预置 OPS_CSM（客户经理，data_scope=OWNED：仅 csm_user_id=self 的租户） */
        static final long ROLE_CSM = 12100002L;

        TestFixtures(SysTenantMapper tenantMapper, SysUserMapper userMapper,
                     SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper,
                     SysTenantSeatAddonMapper seatAddonMapper, SysPackageMapper packageMapper) {
            this.tenantMapper = tenantMapper;
            this.userMapper = userMapper;
            this.roleMapper = roleMapper;
            this.userRoleMapper = userRoleMapper;
            this.seatAddonMapper = seatAddonMapper;
            this.packageMapper = packageMapper;
        }

        /** 种运营账号并绑指定预置角色（超管=ALL / CSM=OWNED），返回 userId。 */
        long seedOpsUser(String username, String password, long roleId) {
            SysUser u = new SysUser();
            u.setTenantId(0L);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(password));
            u.setRealName("Ops IT " + username);
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);

            com.wensaas.core.impl.permission.role.SysUserRole ur = new com.wensaas.core.impl.permission.role.SysUserRole();
            ur.setTenantId(0L);
            ur.setUserId(u.getId());
            ur.setRoleId(roleId);
            ur.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(ur);
            return u.getId();
        }

        /** 把租户归属到指定客户经理（csm_user_id），用于 OWNED 数据范围用例。 */
        void setTenantCsm(long tenantId, Long csmUserId) {
            SysTenant update = new SysTenant();
            update.setId(tenantId);
            update.setCsmUserId(csmUserId);
            tenantMapper.updateById(update);
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

        void ensureBizPackage(String code) {
            if (packageMapper.selectOne(Wrappers.<SysPackage>lambdaQuery()
                    .eq(SysPackage::getCode, code)) == null) {
                SysPackage pkg = new SysPackage();
                pkg.setCode(code);
                pkg.setName("IT 业务测试包");
                pkg.setIsSystem(false);
                pkg.setStatus(1);
                pkg.setDefaultTtlDays(365);
                packageMapper.insert(pkg);
            }
        }

        /** 在指定租户下插入一个已激活（status=1）的成员，返回其 userId（字符串）。 */
        String seedTenantMember(long tenantId, String username) {
            SysUser u = new SysUser();
            u.setTenantId(tenantId);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash("Pass@2026!"));
            u.setRealName("Member IT");
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);
            return String.valueOf(u.getId());
        }

        /** 种一个租户成员 + 登录拿 JWT（tenantCode 来自 sys_tenant.code）。 */
        String seedAndLoginTenantUser(MockMvc mvc, long tenantId, String username, String password) throws Exception {
            SysUser u = new SysUser();
            u.setTenantId(tenantId);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(password));
            u.setRealName("Tenant User IT");
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);

            SysTenant tenant = tenantMapper.selectById(tenantId);
            MvcResult res = mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.toJSONString(Map.of(
                                    "username", username,
                                    "password", password,
                                    "tenantCode", tenant.getCode()))))
                    .andReturn();
            return JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
        }

        SysTenant findTenantById(long id) {
            return tenantMapper.selectById(id);
        }

        void cleanup() {
            // 清理该 IT 测试创建的租户及相关数据（物理删除，绕过软删避免唯一索引冲突）
            List<SysTenant> tenants = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                    .like(SysTenant::getCode, "ops-write-it"));
            for (SysTenant t : tenants) {
                Long tid = t.getId();
                userRoleMapper.delete(Wrappers.<com.wensaas.core.impl.permission.role.SysUserRole>lambdaQuery()
                        .eq(com.wensaas.core.impl.permission.role.SysUserRole::getTenantId, tid));
                roleMapper.delete(Wrappers.<SysRole>lambdaQuery()
                        .eq(SysRole::getTenantId, tid));
                userMapper.delete(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getTenantId, tid));
                seatAddonMapper.delete(Wrappers.<com.wensaas.core.impl.ops.SysTenantSeatAddon>lambdaQuery()
                        .eq(com.wensaas.core.impl.ops.SysTenantSeatAddon::getTenantId, tid));
            }
            // 物理删除租户（绕过软删避免 unique index 冲突）
            for (SysTenant t : tenants) {
                tenantMapper.deleteById(t.getId());
            }
            // 清理 ops 测试用户 + 其 OPS_SUPER_ADMIN 角色绑定（tenant_id=0）
            List<SysUser> opsUsers = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getTenantId, 0L)
                    .and(w -> w.like(SysUser::getUsername, "ops-write-it")
                            .or().eq(SysUser::getUsername, OPS_USERNAME)));
            for (SysUser ou : opsUsers) {
                userRoleMapper.delete(Wrappers.<com.wensaas.core.impl.permission.role.SysUserRole>lambdaQuery()
                        .eq(com.wensaas.core.impl.permission.role.SysUserRole::getUserId, ou.getId()));
                userMapper.deleteById(ou.getId());
            }
        }
    }
}
