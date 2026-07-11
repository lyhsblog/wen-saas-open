package com.wensaas.core.impl.ops.team;

import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.auth.permission.UserRoleQueryService;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.ops.team.OpsTeamAdminException;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.permission.datascope.DataScope;
import com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore;
import com.wensaas.core.impl.permission.eval.SysRolePermissionMapper;
import com.wensaas.core.impl.permission.role.SysRole;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OPS_SUPER_ADMIN 角色收紧：权限集 API 只读；成员变更仅限超管操作。
 */
class OpsTeamRoleSuperAdminGuardTest {

    private static final long SUPER_ROLE_ID = 10L;
    private static final long SUPER_ADMIN_USER = 1L;
    private static final long CSM_OPERATOR = 99L;
    private static final UserRoleQueryService.RoleBrief SUPER_ROLE =
            new UserRoleQueryService.RoleBrief(SUPER_ROLE_ID, "OPS_SUPER_ADMIN", "超级管理员");

    private SysRoleMapper roleMapper;
    private SysUserRoleMapper userRoleMapper;
    private OpsTeamRoleAdminServiceImpl service;

    @BeforeEach
    void setup() {
        roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper rolePermissionMapper = mock(SysRolePermissionMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        RoleDefaultScopeStore defaultScopeStore = mock(RoleDefaultScopeStore.class);
        PermissionRegistry permissionRegistry = mock(PermissionRegistry.class);
        UserRoleQueryService userRoleQueryService = mock(UserRoleQueryService.class);
        service = new OpsTeamRoleAdminServiceImpl(roleMapper, rolePermissionMapper, userRoleMapper,
                userMapper, defaultScopeStore, permissionRegistry, userRoleQueryService);

        when(defaultScopeStore.get(anyLong(), anyLong())).thenReturn(DataScope.ALL);
        when(permissionRegistry.getAll()).thenReturn(List.of());
        // 角色归属：仅 1 号成员是超管
        when(userRoleQueryService.rolesOfUser(anyLong(), anyLong())).thenReturn(List.of());
        when(userRoleQueryService.rolesOfUser(0L, SUPER_ADMIN_USER)).thenReturn(List.of(SUPER_ROLE));
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
        AuditObjects.clear();
    }

    private static void loginAs(long userId) {
        UserContext.set(new UserPrincipal(userId, 0L, "op-" + userId, "sess-" + userId,
                false, null, false, null));
    }

    private void stubRole(String code) {
        SysRole r = new SysRole();
        r.setId(SUPER_ROLE_ID);
        r.setCode(code);
        r.setName(code);
        when(roleMapper.selectOne(any())).thenReturn(r);
    }

    @Test
    void updatePermissions_superAdminRole_rejected_regardlessOfOperator() {
        loginAs(SUPER_ADMIN_USER);
        stubRole("OPS_SUPER_ADMIN");

        assertThatThrownBy(() -> service.updatePermissions(SUPER_ROLE_ID, List.of("ops.menu.manage")))
                .isInstanceOf(OpsTeamAdminException.class)
                .extracting(e -> ((OpsTeamAdminException) e).code())
                .isEqualTo(OpsTeamAdminException.Code.SUPER_ADMIN_PERMISSIONS_READONLY);
    }

    @Test
    void updatePermissions_csmRole_allowed() {
        loginAs(CSM_OPERATOR);
        stubRole("OPS_CSM");
        when(userRoleMapper.countByRole(anyLong(), anyLong())).thenReturn(0);

        assertThatCode(() -> service.updatePermissions(SUPER_ROLE_ID, List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void assignMembers_superAdminRole_byNonSuperAdmin_rejected() {
        loginAs(CSM_OPERATOR);
        stubRole("OPS_SUPER_ADMIN");

        assertThatThrownBy(() -> service.assignMembers(SUPER_ROLE_ID, List.of(CSM_OPERATOR)))
                .isInstanceOf(OpsTeamAdminException.class)
                .extracting(e -> ((OpsTeamAdminException) e).code())
                .isEqualTo(OpsTeamAdminException.Code.CANNOT_OPERATE_SUPER_ADMIN);
    }

    @Test
    void assignMembers_superAdminRole_bySuperAdmin_allowed() {
        loginAs(SUPER_ADMIN_USER);
        stubRole("OPS_SUPER_ADMIN");
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        assertThatCode(() -> service.assignMembers(SUPER_ROLE_ID, List.of(SUPER_ADMIN_USER)))
                .doesNotThrowAnyException();
    }
}
