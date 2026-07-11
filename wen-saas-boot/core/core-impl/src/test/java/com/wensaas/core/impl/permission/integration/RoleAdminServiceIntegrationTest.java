package com.wensaas.core.impl.permission.integration;

import com.wensaas.core.api.auth.permission.RoleAdminException;
import com.wensaas.core.api.auth.permission.RoleAdminService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleAdminServiceIntegrationTest {

    private static final long TENANT_ID = 9801L;
    private static final long OTHER_TENANT = 9802L;

    @Autowired JdbcTemplate jdbc;
    @Autowired RoleAdminService service;
    @Autowired PermissionCacheEvictor evictor;

    @BeforeAll
    void setupClass() {
        cleanup();
    }

    @AfterAll
    void teardownClass() {
        cleanup();
        TenantContext.clear();
    }

    @BeforeEach
    void setTenant() {
        cleanup();
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
        evictor.evictAll();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_role_permission WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT);
        jdbc.update("DELETE FROM sys_user_role WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT);
        jdbc.update("DELETE FROM sys_role WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT);
        jdbc.update("DELETE FROM sys_user WHERE id IN (7001, 7002, 7003) AND tenant_id = ?", TENANT_ID);
    }

    /**
     * 种成员行：{@code listUserIdsByRole/countByRole} 自 92c44e4 起 JOIN {@code sys_user}
     * 排除孤儿绑定（仅计存活用户），裸 userId 绑定查不出来，必须有对应用户行。
     */
    private void seedUsers(long... ids) {
        for (long id : ids) {
            jdbc.update("INSERT INTO sys_user (id, tenant_id, username, password_hash, real_name, status, created_at, updated_at) "
                    + "VALUES (?, ?, ?, 'x', ?, 1, now(), now()) ON CONFLICT (id) DO NOTHING",
                    id, TENANT_ID, "role-it-u" + id, "角色IT成员" + id);
        }
    }

    @Test
    void createAndList() {
        RoleAdminService.RoleDetail r = service.create(new RoleAdminService.CreateRoleRequest(
                "finance_ops", "财务专员", "财务相关", "DEPT_AND_BELOW"));
        assertThat(r.dataScope()).isEqualTo("DEPT_AND_BELOW");
        assertThat(r.systemPreset()).isFalse();

        List<RoleAdminService.RoleView> all = service.listRoles();
        assertThat(all).extracting(RoleAdminService.RoleView::code).contains("finance_ops");
    }

    @Test
    void duplicateCodeOrName_throws() {
        service.create(new RoleAdminService.CreateRoleRequest(
                "r_dup_code", "R Dup Code", null, "SELF"));
        assertThatThrownBy(() -> service.create(new RoleAdminService.CreateRoleRequest(
                "r_dup_code", "另一个名", null, "SELF")))
                .isInstanceOf(RoleAdminException.class)
                .extracting(e -> ((RoleAdminException) e).code())
                .isEqualTo(RoleAdminException.Code.ROLE_CODE_DUPLICATE);

        assertThatThrownBy(() -> service.create(new RoleAdminService.CreateRoleRequest(
                "r_dup_code2", "R Dup Code", null, "SELF")))
                .isInstanceOf(RoleAdminException.class)
                .extracting(e -> ((RoleAdminException) e).code())
                .isEqualTo(RoleAdminException.Code.ROLE_NAME_DUPLICATE);
    }

    @Test
    void invalidDataScope_throws() {
        assertThatThrownBy(() -> service.create(new RoleAdminService.CreateRoleRequest(
                "r_bad_scope", "R Bad Scope", null, "EVERYWHERE")))
                .isInstanceOf(RoleAdminException.class)
                .extracting(e -> ((RoleAdminException) e).code())
                .isEqualTo(RoleAdminException.Code.INVALID_DATA_SCOPE);
    }

    @Test
    void updatePermissions_replacesFullSet() {
        RoleAdminService.RoleDetail r = service.create(new RoleAdminService.CreateRoleRequest(
                "r_perm_role", "R Perm", null, "ALL"));
        service.updatePermissions(r.id(), List.of("crm.customer.view", "crm.customer.edit"));
        RoleAdminService.RoleDetail after = service.getRole(r.id());
        assertThat(after.permissions()).containsExactlyInAnyOrder(
                "crm.customer.view", "crm.customer.edit");

        // 替换为空数组 → 清空
        service.updatePermissions(r.id(), List.of());
        assertThat(service.getRole(r.id()).permissions()).isEmpty();
    }

    @Test
    void assignAndRemoveMembers() {
        seedUsers(7001L, 7002L, 7003L);
        RoleAdminService.RoleDetail r = service.create(new RoleAdminService.CreateRoleRequest(
                "r_mem_role", "R Members", null, "DEPT_AND_BELOW"));
        service.assignMembers(r.id(), List.of(7001L, 7002L, 7003L));
        assertThat(service.listMembers(r.id())).extracting(RoleAdminService.MemberRef::userId)
                .containsExactlyInAnyOrder(7001L, 7002L, 7003L);

        service.removeMembers(r.id(), List.of(7002L));
        assertThat(service.listMembers(r.id())).extracting(RoleAdminService.MemberRef::userId)
                .containsExactlyInAnyOrder(7001L, 7003L);

        // 删除有成员的角色应阻断
        assertThatThrownBy(() -> service.delete(r.id()))
                .isInstanceOf(RoleAdminException.class)
                .extracting(e -> ((RoleAdminException) e).code())
                .isEqualTo(RoleAdminException.Code.ROLE_HAS_MEMBERS);
    }

    @Test
    void systemPresetOwner_completelyReadOnly() {
        // 模拟预置 SYSTEM_OWNER 角色（生产中由初始化脚本插入）
        long ownerId = 90001L;
        jdbc.update("INSERT INTO sys_role (id, tenant_id, code, name, status) " +
                        "VALUES (?, ?, 'SYSTEM_OWNER', '主管理员', 1)",
                ownerId, TENANT_ID);
        assertThatThrownBy(() -> service.update(ownerId, new RoleAdminService.UpdateRoleRequest(
                "改名失败", null, null)))
                .isInstanceOf(RoleAdminException.class)
                .extracting(e -> ((RoleAdminException) e).code())
                .isEqualTo(RoleAdminException.Code.ROLE_SYSTEM_OWNER_READONLY);
        assertThatThrownBy(() -> service.delete(ownerId))
                .isInstanceOf(RoleAdminException.class)
                .extracting(e -> ((RoleAdminException) e).code())
                .isEqualTo(RoleAdminException.Code.ROLE_SYSTEM_OWNER_READONLY);
    }

    @Test
    void crossTenant_cannotAccess() {
        long otherId = TenantContext.callAs(OTHER_TENANT, () ->
                service.create(new RoleAdminService.CreateRoleRequest(
                        "other_role", "其他租户角色", null, "ALL")).id());
        TenantContext.set(TENANT_ID);
        assertThatThrownBy(() -> service.getRole(otherId))
                .isInstanceOf(RoleAdminException.class)
                .extracting(e -> ((RoleAdminException) e).code())
                .isEqualTo(RoleAdminException.Code.ROLE_NOT_FOUND);
    }
}
