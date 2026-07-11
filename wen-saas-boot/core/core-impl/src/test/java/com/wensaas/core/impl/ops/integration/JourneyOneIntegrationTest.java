package com.wensaas.core.impl.ops.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.ops.TenantOpsService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.password.PasswordResetTokenService;
import com.wensaas.core.impl.auth.password.SysPasswordResetToken;
import com.wensaas.core.impl.auth.password.SysPasswordResetTokenMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.pkg.SysPackageMapper;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 旅程一（运营开通新租户）后端端到端集成测试。
 *
 * <p>覆盖：createTenant 8 步事务 + reset token 签发 + 密码重置（消费 token + 激活账号）。
 * 使用真 PG + Redis；Flyway 禁用，@BeforeAll 自建所需表（幂等 IF NOT EXISTS）。
 */
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JourneyOneIntegrationTest {

    private static final long OPS_USER_ID = 1L;
    private static final String TENANT_CODE = "journey1";
    private static final String ADMIN_USERNAME = "j1admin";

    @Autowired JdbcTemplate jdbc;
    @Autowired TenantOpsService tenantOpsService;
    @Autowired PasswordResetTokenService resetTokenService;
    @Autowired SysTenantMapper tenantMapper;
    @Autowired SysUserMapper userMapper;
    @Autowired SysRoleMapper roleMapper;
    @Autowired SysUserRoleMapper userRoleMapper;
    @Autowired SysPackageMapper packageMapper;
    @Autowired SysPasswordResetTokenMapper tokenMapper;

    @BeforeAll
    void setupClass() {
        // 表结构由单一基线迁移 V1.0.0 保证（测试连已迁移的 wen_saas 库，flyway.enabled=false）。
        // 仅保留测试所需的数据种子：确保 base 套餐存在且 status=1。
        jdbc.execute("INSERT INTO sys_package (id, code, name, status) VALUES (9999901, 'base', 'Base Package', 1) " +
                "ON CONFLICT (code) DO UPDATE SET status=1");
        cleanup();
    }

    @AfterAll
    void teardownClass() {
        cleanup();
        UserContext.clear();
        TenantContext.clear();
    }

    @BeforeEach
    void setupEach() {
        cleanup();
        // 模拟运营用户已登录（tenantId=0 平台账号）
        UserContext.set(new UserPrincipal(OPS_USER_ID, 0L, "ops-admin", "sess-ops",
                false, null, false, null));
        // service 内 @CrossTenantQuery 会绕过 TenantLine；测试方法体内直接查 mapper 时需要 TenantContext
        TenantContext.set(0L);
    }

    @AfterEach
    void cleanupEach() {
        cleanup();
        UserContext.clear();
        TenantContext.clear();
    }

    @Test
    void createTenant_fullFlow_allStepsExecuted() {
        TenantOpsService.CreateTenantCommand cmd = new TenantOpsService.CreateTenantCommand(
                "旅程一测试租户", TENANT_CODE, "测试备注",
                ADMIN_USERNAME, "旅程一管理员",
                null, null,
                null, null, null, null, null,
                null, null, null, null, null, null,
                0, null, List.of()
        );

        TenantOpsService.CreatedTenantResult result = tenantOpsService.createTenant(cmd);

        // 基本结果
        assertThat(result.tenantId()).isPositive();
        assertThat(result.tenantCode()).isEqualTo(TENANT_CODE);
        assertThat(result.tenantNo()).startsWith("T");
        assertThat(result.adminUsername()).isEqualTo(ADMIN_USERNAME);
        assertThat(result.resetToken()).isNotBlank();
        assertThat(result.resetExpiresAt()).isAfter(Instant.now());

        // sys_tenant 存在（平台表，不受 TenantLine 影响）
        assertThat(tenantMapper.selectOne(Wrappers.<com.wensaas.core.impl.tenant.SysTenant>lambdaQuery()
                .eq(com.wensaas.core.impl.tenant.SysTenant::getCode, TENANT_CODE))).isNotNull();

        // 切到新租户上下文以查混合表（sys_user / sys_role / sys_user_role 均带 tenant_id）
        TenantContext.set(result.tenantId());

        // sys_user 已创建（status=0，未激活）
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, ADMIN_USERNAME));
        assertThat(user).isNotNull();
        assertThat(user.getStatus()).isEqualTo(0);

        // 3 个系统角色已创建
        assertThat(roleMapper.selectCount(Wrappers.<com.wensaas.core.impl.permission.role.SysRole>lambdaQuery()
                .eq(com.wensaas.core.impl.permission.role.SysRole::getTenantId, result.tenantId())
                .in(com.wensaas.core.impl.permission.role.SysRole::getCode,
                        List.of("SYSTEM_OWNER", "SYSTEM_ADMIN", "SYSTEM_MEMBER")))).isEqualTo(3L);

        // 主管理员绑定了 SYSTEM_OWNER 角色
        assertThat(userRoleMapper.selectCount(Wrappers.<com.wensaas.core.impl.permission.role.SysUserRole>lambdaQuery()
                .eq(com.wensaas.core.impl.permission.role.SysUserRole::getUserId, user.getId()))).isEqualTo(1L);

        // reset token 存在且 purpose=init_admin
        SysPasswordResetToken token = resetTokenService.findByToken(result.resetToken());
        assertThat(token).isNotNull();
        assertThat(token.getPurpose()).isEqualTo("init_admin");
        assertThat(token.getUsedAt()).isNull();
        assertThat(token.getInvalidatedAt()).isNull();
    }

    @Test
    void createTenant_resetToken_canBeConsumed() {
        TenantOpsService.CreateTenantCommand cmd = new TenantOpsService.CreateTenantCommand(
                "Token 消费测试租户", TENANT_CODE + "-2", null,
                ADMIN_USERNAME + "2", null,
                null, null,
                null, null, null, null, null,
                null, null, null, null, null, null,
                0, null, List.of()
        );
        TenantOpsService.CreatedTenantResult result = tenantOpsService.createTenant(cmd);

        // 消费 token
        resetTokenService.markUsed(resetTokenService.findByToken(result.resetToken()).getId());

        SysPasswordResetToken used = resetTokenService.findByToken(result.resetToken());
        assertThat(used.getUsedAt()).isNotNull().isBefore(Instant.now().plusSeconds(5));
    }

    @Test
    void getTenantDetail_afterOpen_returnsAllSections() {
        TenantOpsService.CreateTenantCommand cmd = new TenantOpsService.CreateTenantCommand(
                "详情测试租户", TENANT_CODE + "-detail", "详情备注",
                ADMIN_USERNAME + "d", "明细管理员",
                null, null,
                null, null, null, null, null,
                null, null, null, null, null, null,
                7, "加购 7", List.of()
        );
        TenantOpsService.CreatedTenantResult created = tenantOpsService.createTenant(cmd);

        TenantOpsService.TenantDetail detail = tenantOpsService.getTenantDetail(created.tenantId());

        // 基本信息
        assertThat(detail.tenant().code()).isEqualTo(TENANT_CODE + "-detail");
        assertThat(detail.tenant().tenantNo()).startsWith("T");
        assertThat(detail.tenant().remark()).isEqualTo("详情备注");
        assertThat(detail.tenant().activationStatus()).isEqualTo("pending_activation");
        // 主管理员
        assertThat(detail.mainAdmin()).isNotNull();
        assertThat(detail.mainAdmin().username()).isEqualTo(ADMIN_USERNAME + "d");
        assertThat(detail.mainAdmin().status()).isEqualTo(0);
        // 套餐（base 永久）
        assertThat(detail.plans()).hasSize(1);
        assertThat(detail.plans().get(0).code()).isEqualTo("base");
        assertThat(detail.plans().get(0).readonly()).isFalse();
        // 加购席位
        assertThat(detail.seatAddons().totalQuantity()).isEqualTo(7);
        assertThat(detail.seatAddons().history()).hasSize(1);
        assertThat(detail.seatAddons().history().get(0).quantity()).isEqualTo(7);
        assertThat(detail.seatAddons().history().get(0).remark()).isEqualTo("加购 7");
    }

    @Test
    void reissueResetLink_invalidatesPreviousAndReturnsNewToken() {
        TenantOpsService.CreateTenantCommand cmd = new TenantOpsService.CreateTenantCommand(
                "重发链接测试", TENANT_CODE + "-rs", null,
                ADMIN_USERNAME + "rs", null,
                null, null,
                null, null, null, null, null,
                null, null, null, null, null, null,
                0, null, List.of()
        );
        TenantOpsService.CreatedTenantResult created = tenantOpsService.createTenant(cmd);
        String oldToken = created.resetToken();

        TenantOpsService.IssuedResetLink reissued = tenantOpsService.reissueResetLink(created.tenantId());

        assertThat(reissued.token()).isNotBlank().isNotEqualTo(oldToken);
        assertThat(reissued.adminUsername()).isEqualTo(ADMIN_USERNAME + "rs");
        assertThat(reissued.expiresAt()).isAfter(Instant.now());

        SysPasswordResetToken old = resetTokenService.findByToken(oldToken);
        assertThat(old.getInvalidatedAt()).isNotNull();
        SysPasswordResetToken fresh = resetTokenService.findByToken(reissued.token());
        assertThat(fresh.getInvalidatedAt()).isNull();
        assertThat(fresh.getUsedAt()).isNull();
        assertThat(fresh.getPurpose()).isEqualTo("reset_admin");
    }

    @Test
    void getTenantDetail_notFound_throws() {
        assertThatThrownBy(() -> tenantOpsService.getTenantDetail(9999999L))
                .isInstanceOf(com.wensaas.core.api.ops.TenantOpsException.class)
                .hasMessageContaining("租户不存在");
    }

    @Test
    void createTenant_secondCall_invalidatesPreviousToken() {
        // 第一次开通
        TenantOpsService.CreateTenantCommand cmd1 = new TenantOpsService.CreateTenantCommand(
                "重复 Token 测试", TENANT_CODE + "-3", null,
                ADMIN_USERNAME + "3", null,
                null, null,
                null, null, null, null, null,
                null, null, null, null, null, null,
                0, null, List.of()
        );
        TenantOpsService.CreatedTenantResult r1 = tenantOpsService.createTenant(cmd1);
        Long userId = resetTokenService.findByToken(r1.resetToken()).getUserId();

        // 再次签发 token（模拟重新生成）
        PasswordResetTokenService.IssuedToken r2 = resetTokenService.issue(
                userId, r1.tenantId(), TENANT_CODE + "-3", ADMIN_USERNAME + "3", "init_admin");

        // 第一个 token 应被 invalidated
        SysPasswordResetToken old = resetTokenService.findByToken(r1.resetToken());
        assertThat(old.getInvalidatedAt()).isNotNull();

        // 第二个 token 应仍有效
        SysPasswordResetToken fresh = resetTokenService.findByToken(r2.token());
        assertThat(fresh.getInvalidatedAt()).isNull();
        assertThat(fresh.getUsedAt()).isNull();
    }

    @Test
    void listTenants_sortBranches_usage_opsUsers_smoke() {
        // 运营账号（tenant_id=0）作为客户经理
        long csmId = 920001L;
        jdbc.update("INSERT INTO sys_user (id, tenant_id, username, password_hash, real_name, status, created_at, updated_at) " +
                "VALUES (?, 0, 'csm-smoke', 'x', '客户经理烟雾', 1, now(), now()) ON CONFLICT (id) DO NOTHING", csmId);

        TenantOpsService.CreateTenantCommand cmd = new TenantOpsService.CreateTenantCommand(
                "列表烟雾租户", TENANT_CODE + "-list", null,
                ADMIN_USERNAME + "list", null,
                null, null,
                csmId, 50, 10_737_418_240L, Instant.parse("2027-01-01T00:00:00Z"), "Asia/Shanghai",
                null, null, null, null, null, null,
                0, null, List.of());
        TenantOpsService.CreatedTenantResult created = tenantOpsService.createTenant(cmd);

        // 三个排序分支都要能跑通（验证 .last() ORDER BY 子查询 + MP 分页 count 不报错）
        for (String sort : List.of("lastActiveAt:desc", "createdAt:desc", "nearestExpireAt:asc")) {
            TenantOpsService.TenantPage page = tenantOpsService.listTenants(
                    1, 20, TENANT_CODE + "-list", null, null, null, null, sort);
            assertThat(page.total()).isGreaterThanOrEqualTo(1);
            assertThat(page.items()).isNotEmpty();
            assertThat(page.items().get(0).csmName()).isEqualTo("客户经理烟雾");
            assertThat(page.items().get(0).csmUserId()).isEqualTo(String.valueOf(csmId));
            // 列表新字段（OPS-T-020/023）：base 套餐 1 个、加购席位 0
            assertThat(page.items().get(0).planCount()).isGreaterThanOrEqualTo(1);
            assertThat(page.items().get(0).planCodes()).contains("base");
            assertThat(page.items().get(0).seatAddonTotal()).isEqualTo(0);
        }

        // packageCode 筛选（OPS-T-019）：勾了 base 的能命中，不存在的包筛不到
        assertThat(tenantOpsService.listTenants(1, 20, TENANT_CODE + "-list",
                null, null, null, List.of("base"), null).total()).isGreaterThanOrEqualTo(1);
        assertThat(tenantOpsService.listTenants(1, 20, TENANT_CODE + "-list",
                null, null, null, List.of("nonexistent-pkg"), null).total()).isEqualTo(0);

        // 使用情况：至少主管理员 1 名；趋势列表非 null
        TenantOpsService.UsageStats usage = tenantOpsService.getTenantUsage(created.tenantId());
        assertThat(usage.memberCount()).isGreaterThanOrEqualTo(1);
        assertThat(usage.loginTrend()).isNotNull();

        // 运营候选人查询
        assertThat(tenantOpsService.listOpsUsers("csm-smoke"))
                .anyMatch(u -> "csm-smoke".equals(u.username()));
    }

    @Test
    void createTenant_invalidCsm_throws() {
        TenantOpsService.CreateTenantCommand cmd = new TenantOpsService.CreateTenantCommand(
                "非法CSM租户", TENANT_CODE + "-badcsm", null,
                ADMIN_USERNAME + "bc", null,
                null, null,
                88888888L, null, null, null, null,
                null, null, null, null, null, null,
                0, null, List.of());
        assertThatThrownBy(() -> tenantOpsService.createTenant(cmd))
                .isInstanceOf(com.wensaas.core.api.ops.TenantOpsException.class)
                .hasMessageContaining("客户经理");
    }

    /** 夹具租户 code 一律 'journey1' 或 'journey1-*'（见各用例），清理模式须覆盖全部。 */
    private void cleanup() {
        jdbc.execute("DELETE FROM sys_user WHERE username = 'csm-smoke'");
        jdbc.execute("DELETE FROM sys_user_role WHERE tenant_id IN " +
                "(SELECT id FROM sys_tenant WHERE code LIKE 'journey1%')");
        jdbc.execute("DELETE FROM sys_role WHERE tenant_id IN " +
                "(SELECT id FROM sys_tenant WHERE code LIKE 'journey1%')");
        jdbc.execute("DELETE FROM sys_user WHERE username LIKE 'j1admin%'");
        jdbc.execute("DELETE FROM sys_tenant WHERE code LIKE 'journey1%'");
        jdbc.execute("DELETE FROM sys_password_reset_token WHERE tenant_code LIKE 'journey1%'");
        jdbc.execute("DELETE FROM sys_tenant_seat_addon WHERE remark IS NULL AND " +
                "tenant_id NOT IN (SELECT id FROM sys_tenant WHERE 1=1)");
        // clean seat addons for non-existent tenants
        jdbc.execute("DELETE FROM sys_tenant_seat_addon sa WHERE NOT EXISTS " +
                "(SELECT 1 FROM sys_tenant t WHERE t.id = sa.tenant_id)");
    }
}
