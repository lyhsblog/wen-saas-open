package com.wensaas.core.impl.ops.team;

import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.auth.permission.UserRoleQueryService;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.ops.team.OpsTeamAdminException;
import com.wensaas.core.api.ops.team.OpsTeamMemberAdminService;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.auth.password.SecurityPolicySettings;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.user.UserCommandService;
import com.wensaas.core.impl.auth.oauth.SysUserOAuthBindingMapper;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.security.SensitiveFieldCipher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超管成员保护守卫：目标持有 OPS_SUPER_ADMIN 时仅超管可操作；授予超管角色仅限超管。
 */
class OpsTeamMemberSuperAdminGuardTest {

    private static final long SUPER_ADMIN_USER = 1L;
    private static final long OTHER_SUPER_ADMIN = 5L;
    private static final long PLAIN_MEMBER = 2L;
    private static final long CSM_OPERATOR = 99L;
    private static final long SUPER_ROLE_ID = 10L;
    private static final String STRONG_PASSWORD = "Reset@2026!";
    private static final UserRoleQueryService.RoleBrief SUPER_ROLE =
            new UserRoleQueryService.RoleBrief(SUPER_ROLE_ID, "OPS_SUPER_ADMIN", "超级管理员");

    private SysUserMapper userMapper;
    private UserCommandService userCommandService;
    private SessionStore sessionStore;
    private UserRoleQueryService userRoleQueryService;
    private OpsTeamMemberAdminServiceImpl service;

    @BeforeEach
    void setup() {
        userMapper = mock(SysUserMapper.class);
        userCommandService = mock(UserCommandService.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        sessionStore = mock(SessionStore.class);
        SecurityPolicySettings securityPolicy = mock(SecurityPolicySettings.class);
        SensitiveFieldCipher cipher = mock(SensitiveFieldCipher.class);
        SysUserOAuthBindingMapper oauthBindingMapper = mock(SysUserOAuthBindingMapper.class);
        userRoleQueryService = mock(UserRoleQueryService.class);
        service = new OpsTeamMemberAdminServiceImpl(userMapper, userCommandService, userRoleMapper,
                roleMapper, passwordHasher, sessionStore, securityPolicy, cipher, oauthBindingMapper,
                userRoleQueryService);

        when(securityPolicy.passwordMinLength()).thenReturn(8);
        when(passwordHasher.hash(anyString())).thenReturn("hashed");

        // 角色归属：1 号与 5 号成员持有超管角色，其余无角色
        when(userRoleQueryService.rolesOfUser(anyLong(), anyLong())).thenReturn(List.of());
        when(userRoleQueryService.rolesOfUser(0L, SUPER_ADMIN_USER)).thenReturn(List.of(SUPER_ROLE));
        when(userRoleQueryService.rolesOfUser(0L, OTHER_SUPER_ADMIN)).thenReturn(List.of(SUPER_ROLE));
        when(userRoleQueryService.rolesByIds(anyLong(), anyList())).thenReturn(List.of());
        when(userRoleQueryService.rolesByIds(0L, List.of(SUPER_ROLE_ID))).thenReturn(List.of(SUPER_ROLE));
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

    private void stubTargetUser(long userId) {
        SysUser u = new SysUser();
        u.setId(userId);
        u.setRealName("成员" + userId);
        when(userMapper.selectOne(any())).thenReturn(u);
    }

    @Test
    void resetPassword_superAdminTarget_byNonSuperAdmin_rejected() {
        loginAs(CSM_OPERATOR);
        stubTargetUser(SUPER_ADMIN_USER);

        assertThatThrownBy(() -> service.resetPassword(SUPER_ADMIN_USER, STRONG_PASSWORD))
                .isInstanceOf(OpsTeamAdminException.class)
                .extracting(e -> ((OpsTeamAdminException) e).code())
                .isEqualTo(OpsTeamAdminException.Code.CANNOT_OPERATE_SUPER_ADMIN);
    }

    @Test
    void resetPassword_superAdminTarget_bySuperAdmin_allowed() {
        loginAs(OTHER_SUPER_ADMIN);
        stubTargetUser(SUPER_ADMIN_USER);

        service.resetPassword(SUPER_ADMIN_USER, STRONG_PASSWORD);

        verify(userCommandService).updateById(any(SysUser.class));
        verify(sessionStore).invalidateAllByUser(0L, SUPER_ADMIN_USER);
    }

    @Test
    void resetPassword_plainTarget_byNonSuperAdmin_allowed() {
        loginAs(CSM_OPERATOR);
        stubTargetUser(PLAIN_MEMBER);

        assertThatCode(() -> service.resetPassword(PLAIN_MEMBER, STRONG_PASSWORD))
                .doesNotThrowAnyException();
    }

    @Test
    void disable_superAdminTarget_byNonSuperAdmin_rejected() {
        loginAs(CSM_OPERATOR);
        stubTargetUser(SUPER_ADMIN_USER);

        assertThatThrownBy(() -> service.disableMember(SUPER_ADMIN_USER, CSM_OPERATOR))
                .isInstanceOf(OpsTeamAdminException.class)
                .extracting(e -> ((OpsTeamAdminException) e).code())
                .isEqualTo(OpsTeamAdminException.Code.CANNOT_OPERATE_SUPER_ADMIN);
    }

    @Test
    void updateMember_superAdminTarget_byNonSuperAdmin_rejected() {
        loginAs(CSM_OPERATOR);
        stubTargetUser(SUPER_ADMIN_USER);

        assertThatThrownBy(() -> service.updateMember(SUPER_ADMIN_USER,
                new OpsTeamMemberAdminService.UpdateMemberCommand(
                        "新名", null, null, null, null, null, null)))
                .isInstanceOf(OpsTeamAdminException.class)
                .extracting(e -> ((OpsTeamAdminException) e).code())
                .isEqualTo(OpsTeamAdminException.Code.CANNOT_OPERATE_SUPER_ADMIN);
    }

    @Test
    void createMember_grantSuperAdminRole_byNonSuperAdmin_rejected() {
        loginAs(CSM_OPERATOR);
        when(userMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.createMember(new OpsTeamMemberAdminService.CreateMemberCommand(
                "newops", STRONG_PASSWORD, "新成员", null, null, List.of(SUPER_ROLE_ID))))
                .isInstanceOf(OpsTeamAdminException.class)
                .extracting(e -> ((OpsTeamAdminException) e).code())
                .isEqualTo(OpsTeamAdminException.Code.CANNOT_OPERATE_SUPER_ADMIN);
    }
}
