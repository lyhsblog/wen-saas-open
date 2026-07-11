package com.wensaas.core.impl.permission.integration;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.permission.datascope.DataScope;
import com.wensaas.core.impl.permission.eval.EffectiveScope;
import com.wensaas.core.impl.permission.eval.PermissionCacheEvictor;
import com.wensaas.core.impl.permission.eval.PermissionResolver;
import com.wensaas.core.impl.permission.eval.UserPermissions;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限引擎端到端集成测试（真 PostgreSQL + 真 Redis）。
 *
 * <p>验证按「用户 + 权限点」定向解析：
 * <ul>
 *   <li>{@code has}/{@code hasAny}：授予判定（启用且未软删的角色中任一授予即可）</li>
 *   <li>{@code scopeFor}：grant-coupled 数据范围——只在「授予该权限点的角色」内取最宽，并物化部门集合</li>
 *   <li>{@code resolve}：全量权限点并集 + 默认范围（capabilities 路径）</li>
 *   <li>角色禁用 / 无角色 / 请求级 memo 一致性 / 部门树 evict 后重载</li>
 * </ul>
 *
 * <p>前置：dev 已运行过一次，wen_saas 库已应用初始化。
 */
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermissionEngineIntegrationTest {

    private static final long TENANT_ID = 9001L;
    private static final long USER_ALL = 91001L;
    private static final long USER_DEPT = 91002L;
    private static final long USER_SELF = 91003L;
    private static final long USER_MIX = 91004L;     // grant-coupled 回归用例
    private static final long USER_NONE = 91005L;     // 无角色
    private static final long ROLE_A_ALL = 92001L;
    private static final long ROLE_B_DEPT = 92002L;
    private static final long ROLE_C_SELF = 92003L;
    private static final long ROLE_GRANT_SELF = 92004L; // 授予 special、范围 SELF
    private static final long ROLE_NOGRANT_ALL = 92005L; // 不授予 special、范围 ALL
    private static final String VIEW = "crm.customer.view";
    private static final String EDIT = "crm.customer.edit";
    private static final String SPECIAL = "crm.customer.special";
    // 部门树： 9100 (根) ├─ 9110 ─ 9111   └─ 9120
    private static final long DEPT_ROOT = 9100L;
    private static final long DEPT_110 = 9110L;
    private static final long DEPT_111 = 9111L;
    private static final long DEPT_120 = 9120L;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PermissionResolver resolver;

    @Autowired
    PermissionCacheEvictor evictor;

    @BeforeAll
    void seed() {
        cleanup();

        insertRole(ROLE_A_ALL, "role-a-all", DataScope.ALL);
        insertRole(ROLE_B_DEPT, "role-b-dept", DataScope.DEPT_AND_BELOW);
        insertRole(ROLE_C_SELF, "role-c-self", DataScope.SELF);
        insertRole(ROLE_GRANT_SELF, "role-grant-self", DataScope.SELF);
        insertRole(ROLE_NOGRANT_ALL, "role-nogrant-all", DataScope.ALL);

        // 权限点：A=view+edit；B=view；C=view；GRANT_SELF=special；NOGRANT_ALL=view（不含 special）
        insertRolePermission(ROLE_A_ALL, VIEW);
        insertRolePermission(ROLE_A_ALL, EDIT);
        insertRolePermission(ROLE_B_DEPT, VIEW);
        insertRolePermission(ROLE_C_SELF, VIEW);
        insertRolePermission(ROLE_GRANT_SELF, SPECIAL);
        insertRolePermission(ROLE_NOGRANT_ALL, VIEW);

        insertUserRole(USER_ALL, ROLE_A_ALL);
        insertUserRole(USER_DEPT, ROLE_B_DEPT);
        insertUserRole(USER_SELF, ROLE_C_SELF);
        // USER_MIX 同时持「授予 special·SELF」与「不授予 special·ALL」两个角色
        insertUserRole(USER_MIX, ROLE_GRANT_SELF);
        insertUserRole(USER_MIX, ROLE_NOGRANT_ALL);

        insertDept(DEPT_ROOT, 0L, "根", "/");
        insertDept(DEPT_110, DEPT_ROOT, "110", "/" + DEPT_ROOT + "/");
        insertDept(DEPT_111, DEPT_110, "111", "/" + DEPT_ROOT + "/" + DEPT_110 + "/");
        insertDept(DEPT_120, DEPT_ROOT, "120", "/" + DEPT_ROOT + "/");
        // USER_DEPT 主部门 110 → 可见 {110, 111}
        insertUserDept(USER_DEPT, DEPT_110);
    }

    @AfterAll
    void teardown() {
        cleanup();
    }

    @AfterEach
    void clearContexts() {
        UserContext.clear();
        TenantContext.clear();
        evictor.evictDeptTree(TENANT_ID);
    }

    // ===== has / hasAny =====

    @Test
    void has_grantedAndUngranted() {
        assertThat(resolver.has(TENANT_ID, USER_ALL, VIEW)).isTrue();
        assertThat(resolver.has(TENANT_ID, USER_ALL, EDIT)).isTrue();
        assertThat(resolver.has(TENANT_ID, USER_DEPT, VIEW)).isTrue();
        assertThat(resolver.has(TENANT_ID, USER_DEPT, EDIT)).isFalse();
    }

    @Test
    void hasAny_atLeastOneGranted() {
        assertThat(resolver.hasAny(TENANT_ID, USER_DEPT, List.of(EDIT, VIEW))).isTrue();
        assertThat(resolver.hasAny(TENANT_ID, USER_DEPT, List.of(EDIT, "crm.customer.delete"))).isFalse();
        assertThat(resolver.hasAny(TENANT_ID, USER_DEPT, List.of())).isFalse();
    }

    @Test
    void has_userWithoutRoles_false() {
        assertThat(resolver.has(TENANT_ID, USER_NONE, VIEW)).isFalse();
    }

    // ===== scopeFor（grant-coupled）=====

    @Test
    void scopeFor_all_emptyDepts() {
        EffectiveScope s = resolver.scopeFor(TENANT_ID, USER_ALL, VIEW);
        assertThat(s.scope()).isEqualTo(DataScope.ALL);
        assertThat(s.deptIds()).isEmpty();
    }

    @Test
    void scopeFor_deptAndBelow_expandsSubtree() {
        EffectiveScope s = resolver.scopeFor(TENANT_ID, USER_DEPT, VIEW);
        assertThat(s.scope()).isEqualTo(DataScope.DEPT_AND_BELOW);
        assertThat(s.deptIds()).containsExactlyInAnyOrder(DEPT_110, DEPT_111);
    }

    @Test
    void scopeFor_self_emptyDepts() {
        EffectiveScope s = resolver.scopeFor(TENANT_ID, USER_SELF, VIEW);
        assertThat(s.scope()).isEqualTo(DataScope.SELF);
        assertThat(s.deptIds()).isEmpty();
    }

    /**
     * grant-coupled 关键回归：USER_MIX 持「授予 special·SELF」+「不授予 special·ALL」两角色。
     * special 的范围应为 SELF（只看授予该点的角色）；旧的「全角色取最宽」会错算成 ALL。
     */
    @Test
    void scopeFor_grantCoupled_ignoresUngrantingRoleScope() {
        EffectiveScope s = resolver.scopeFor(TENANT_ID, USER_MIX, SPECIAL);
        assertThat(s.scope()).isEqualTo(DataScope.SELF);
    }

    // ===== resolve（全量）=====

    @Test
    void resolve_unionsPermissions_andWidestDefaultScope() {
        UserPermissions up = resolver.resolve(TENANT_ID, USER_ALL);
        assertThat(up.permissions()).containsExactlyInAnyOrder(VIEW, EDIT);
        assertThat(up.scope()).isEqualTo(DataScope.ALL);
        assertThat(up.deptIds()).isEmpty();
    }

    @Test
    void resolve_userWithoutRoles_defaultsToSelfEmpty() {
        UserPermissions up = resolver.resolve(TENANT_ID, USER_NONE);
        assertThat(up.permissions()).isEmpty();
        assertThat(up.scope()).isEqualTo(DataScope.SELF);
        assertThat(up.deptIds()).isEmpty();
    }

    // ===== 角色禁用 =====

    @Test
    void disabledRole_dropsGrantsAndScope() {
        jdbc.update("UPDATE sys_role SET status = 0 WHERE id = ?", ROLE_C_SELF);
        try {
            assertThat(resolver.has(TENANT_ID, USER_SELF, VIEW)).isFalse();
            assertThat(resolver.resolve(TENANT_ID, USER_SELF).permissions()).isEmpty();
        } finally {
            jdbc.update("UPDATE sys_role SET status = 1 WHERE id = ?", ROLE_C_SELF);
        }
    }

    // ===== 跨租户安全：无 TenantContext 也能解析 =====

    @Test
    void resolve_isCrossTenantSafe_doesNotRequireTenantContext() {
        TenantContext.clear();
        assertThat(resolver.has(TENANT_ID, USER_SELF, VIEW)).isTrue();
        assertThat(resolver.resolve(TENANT_ID, USER_SELF).scope()).isEqualTo(DataScope.SELF);
    }

    // -- fixtures helpers -------------------------------------------------

    private void insertRole(long id, String code, DataScope scope) {
        jdbc.update("INSERT INTO sys_role (id, tenant_id, code, name, status) VALUES (?, ?, ?, ?, 1)",
                id, TENANT_ID, code, code);
        jdbc.update("INSERT INTO sys_role_data_scope (id, tenant_id, role_id, action, data_scope) "
                        + "VALUES (?, ?, ?, NULL, ?)",
                IdWorker.getId(), TENANT_ID, id, scope.code());
    }

    private void insertRolePermission(long roleId, String permissionCode) {
        jdbc.update("INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_code) VALUES (?, ?, ?, ?)",
                IdWorker.getId(), TENANT_ID, roleId, permissionCode);
    }

    private void insertUserRole(long userId, long roleId) {
        jdbc.update("INSERT INTO sys_user_role (id, tenant_id, user_id, role_id) VALUES (?, ?, ?, ?)",
                IdWorker.getId(), TENANT_ID, userId, roleId);
    }

    private void insertDept(long id, long parentId, String name, String ancestors) {
        jdbc.update("INSERT INTO sys_dept (id, tenant_id, parent_id, name, ancestors, sort_order, status) "
                        + "VALUES (?, ?, ?, ?, ?, 0, 1)",
                id, TENANT_ID, parentId, name, ancestors);
    }

    private void insertUserDept(long userId, long deptId) {
        jdbc.update("INSERT INTO sys_user_dept (id, tenant_id, user_id, dept_id, is_main) VALUES (?, ?, ?, ?, 1)",
                IdWorker.getId(), TENANT_ID, userId, deptId);
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_role WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_role_permission WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_role_data_scope WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_user_role WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_dept WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_user_dept WHERE tenant_id = ?", TENANT_ID);
    }
}
