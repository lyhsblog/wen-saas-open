package com.wensaas.core.impl.auth.integration;

import com.wensaas.core.api.auth.user.CapabilitiesService;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.permission.eval.PermissionCacheEvictor;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CapabilitiesService} 端到端集成测试（真 PG + Redis）。
 *
 * <p>覆盖：套餐数据模型 — permissions/dataScope 真路径 + sys_business_module ∩ tenant.plans
 * 真过滤 + activePackages 真填 + 跨租户隔离。
 *
 * <p>不复用 admin Service seed，直接 JdbcTemplate INSERT 进 6 张表：sys_role / sys_user_role / sys_role_permission /
 * sys_business_module / sys_package / sys_package_module + UPDATE sys_tenant.plans。
 *
 * <p>core-impl test config 默认 {@code flyway.enabled=false}，表结构由单一基线迁移 V1.0.0 保证
 * （测试连已迁移的 wen_saas 库）。
 */
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CapabilitiesIntegrationTest {

    private static final long TENANT_ID = 9701L;
    private static final long USER_ID = 970100001L;
    private static final long ROLE_ID = 970110001L;
    private static final long PKG_CRM_PRO = 970150001L;

    @Autowired JdbcTemplate jdbc;
    @Autowired CapabilitiesService capabilitiesService;
    @Autowired PermissionCacheEvictor evictor;

    @BeforeAll
    void setupClass() {
        // 表结构由单一基线迁移 V1.0.0 保证（测试连已迁移的 wen_saas 库，flyway.enabled=false）。
        cleanup();
    }

    @AfterAll
    void teardownClass() {
        cleanup();
        UserContext.clear();
    }

    @BeforeEach
    void beforeEach() {
        cleanup();
        evictor.evictAll();
        UserContext.set(new UserPrincipal(USER_ID, TENANT_ID, "alice", "sess-int",
                false, null, false, null));
        // 生产中 TenantContextWebFilter 从 UserContext 派生 TenantContext；测试无 filter，显式注入，
        // 让 resolve() 内带 tenant line 的查询（如 sys_user 水印查询）自洽，不依赖测试执行顺序。
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void afterEach() {
        UserContext.clear();
        TenantContext.clear();
        evictor.evictAll();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_user_role WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_role_permission WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_role_data_scope WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_role WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_business_module WHERE code IN ('cap_test_crm', 'cap_test_hrm')");
        jdbc.update("DELETE FROM sys_package_module WHERE package_id IN (?)", PKG_CRM_PRO);
        jdbc.update("DELETE FROM sys_package WHERE id = ?", PKG_CRM_PRO);
        jdbc.update("DELETE FROM sys_tenant WHERE id = ?", TENANT_ID);
    }

    @Test
    void emptyState_returnsDefaults() {
        CapabilitiesService.Capabilities cap = capabilitiesService.resolve();

        assertThat(cap.userId()).isEqualTo(String.valueOf(USER_ID));
        assertThat(cap.tenantId()).isEqualTo(String.valueOf(TENANT_ID));
        assertThat(cap.tenantOwner()).isFalse();
        assertThat(cap.permissions()).isEmpty();
        assertThat(cap.dataScope()).isEqualTo("SELF");
        assertThat(cap.visibleDeptIds()).isEmpty();
        assertThat(cap.enabledModules()).isEmpty();
        assertThat(cap.activePackages()).isEmpty();
    }

    @Test
    void withRolesAndPermissions_aggregatesCorrectly() {
        jdbc.update("INSERT INTO sys_role (id, tenant_id, code, name, status) " +
                "VALUES (?, ?, 'cap_test_role', '能力测试角色', 1)", ROLE_ID, TENANT_ID);
        // sys_role_data_scope NULL-action 行 = 角色默认数据范围（Phase 3）；data_scope=1=ALL
        jdbc.update("INSERT INTO sys_role_data_scope (id, tenant_id, role_id, action, data_scope) " +
                "VALUES (?, ?, ?, NULL, 1)", ROLE_ID + 200, TENANT_ID, ROLE_ID);
        jdbc.update("INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_code) VALUES (?, ?, ?, ?)",
                ROLE_ID + 1, TENANT_ID, ROLE_ID, "dict.group.list");
        jdbc.update("INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_code) VALUES (?, ?, ?, ?)",
                ROLE_ID + 2, TENANT_ID, ROLE_ID, "org.member.create");
        jdbc.update("INSERT INTO sys_user_role (id, tenant_id, user_id, role_id) VALUES (?, ?, ?, ?)",
                ROLE_ID + 100, TENANT_ID, USER_ID, ROLE_ID);
        evictor.evictAll();

        CapabilitiesService.Capabilities cap = capabilitiesService.resolve();

        assertThat(cap.permissions()).containsExactly("dict.group.list", "org.member.create");
        assertThat(cap.dataScope()).isEqualTo("ALL");
    }

    @Test
    void enabledModulesFilteredByActiveTenantPlans() {
        // seed sys_business_module：crm 与 hrm 均已注册（模块表无 status 概念）
        jdbc.update("INSERT INTO sys_business_module (id, code, name, version) " +
                "VALUES (?, ?, ?, ?)", 970120001L, "cap_test_crm", "CRM-T", "1.0.0");
        jdbc.update("INSERT INTO sys_business_module (id, code, name, version) " +
                "VALUES (?, ?, ?, ?)", 970120002L, "cap_test_hrm", "HRM-T", "0.9.0");

        // seed sys_package + sys_package_module：crm-pro 仅含 crm（hrm 未入套餐 → 不出现在 enabledModules）
        jdbc.update("INSERT INTO sys_package (id, code, name, is_system, status) " +
                "VALUES (?, 'cap_test_crm_pro', 'CRM 专业版（测试）', FALSE, 1)", PKG_CRM_PRO);
        jdbc.update("INSERT INTO sys_package_module (id, package_id, module_code) VALUES (?, ?, 'cap_test_crm')",
                PKG_CRM_PRO + 1, PKG_CRM_PRO);

        // seed sys_tenant + plans（永久有效）
        jdbc.update("INSERT INTO sys_tenant (id, code, name, status, plans) VALUES " +
                "(?, 'cap-test-co', '能力测试公司', 1, ?::jsonb)",
                TENANT_ID,
                "[{\"packageCode\":\"cap_test_crm_pro\",\"grantedAt\":\"2026-01-01T00:00:00Z\"}]");

        CapabilitiesService.Capabilities cap = capabilitiesService.resolve();

        // enabledModules 应仅含 cap_test_crm（hrm 未入套餐而排除）
        assertThat(cap.enabledModules()).extracting(CapabilitiesService.ModuleView::code)
                .contains("cap_test_crm")
                .doesNotContain("cap_test_hrm");
        // crm 在 active 套餐内，readonly=false
        CapabilitiesService.ModuleView crm = cap.enabledModules().stream()
                .filter(m -> "cap_test_crm".equals(m.code())).findFirst().orElseThrow();
        assertThat(crm.readonly()).isFalse();
        // activePackages 含 cap_test_crm_pro
        assertThat(cap.activePackages()).extracting(CapabilitiesService.PackageView::code)
                .contains("cap_test_crm_pro");
        // 菜单导航现由 sys_menu（scope=TENANT）派生，不再随业务模块注册；菜单的解析覆盖见 MenuQueryService 测试
    }

    @Test
    void expiredPlan_modulesMarkedReadonly() {
        jdbc.update("INSERT INTO sys_business_module (id, code, name, version) " +
                "VALUES (?, ?, ?, ?)", 970120001L, "cap_test_crm", "CRM-T", "1.0.0");
        jdbc.update("INSERT INTO sys_package (id, code, name, is_system, status) " +
                "VALUES (?, 'cap_test_crm_pro', 'CRM 专业版（测试）', FALSE, 1)", PKG_CRM_PRO);
        jdbc.update("INSERT INTO sys_package_module (id, package_id, module_code) VALUES (?, ?, 'cap_test_crm')",
                PKG_CRM_PRO + 1, PKG_CRM_PRO);
        // expiresAt 远在过去
        jdbc.update("INSERT INTO sys_tenant (id, code, name, status, plans) VALUES " +
                "(?, 'cap-test-co', '能力测试公司', 1, ?::jsonb)",
                TENANT_ID,
                "[{\"packageCode\":\"cap_test_crm_pro\",\"grantedAt\":\"2025-01-01T00:00:00Z\"," +
                        "\"expiresAt\":\"2025-12-31T23:59:59Z\"}]");

        CapabilitiesService.Capabilities cap = capabilitiesService.resolve();

        CapabilitiesService.ModuleView crm = cap.enabledModules().stream()
                .filter(m -> "cap_test_crm".equals(m.code())).findFirst().orElseThrow();
        assertThat(crm.readonly()).isTrue();
        CapabilitiesService.PackageView pkg = cap.activePackages().stream()
                .filter(p -> "cap_test_crm_pro".equals(p.code())).findFirst().orElseThrow();
        assertThat(pkg.readonly()).isTrue();
    }
}
