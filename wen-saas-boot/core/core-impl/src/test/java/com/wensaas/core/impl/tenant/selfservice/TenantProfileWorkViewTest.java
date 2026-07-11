package com.wensaas.core.impl.tenant.selfservice;

import com.wensaas.core.api.auth.org.DeptQueryService;
import com.wensaas.core.api.auth.org.DeptQueryService.DeptBinding;
import com.wensaas.core.api.auth.org.DeptQueryService.DeptInfo;
import com.wensaas.core.api.auth.permission.UserRoleQueryService;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.tenant.selfservice.TenantProfileService.Work;
import com.wensaas.core.impl.auth.AuthProperties;
import com.wensaas.core.impl.auth.mfa.MfaService;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.auth.password.SecurityPolicySettings;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.user.UserCommandService;
import com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组织与角色视图（getWork）的部门名 / 全路径拼装单测：
 * 部门信息统一来自整租户部门缓存快照（{@code deptInfosOfTenant}），不再逐级 {@code deptById} 查库。
 */
class TenantProfileWorkViewTest {

    private static final long TENANT = 9200L;
    private static final long USER = 9201L;

    private SysUserMapper userMapper;
    private DeptQueryService deptQuery;
    private UserRoleQueryService userRoleQuery;
    private TenantProfileServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        deptQuery = mock(DeptQueryService.class);
        userRoleQuery = mock(UserRoleQueryService.class);
        service = new TenantProfileServiceImpl(
                userMapper,
                mock(UserCommandService.class),
                userRoleQuery,
                deptQuery,
                mock(SessionStore.class),
                mock(PasswordHasher.class),
                mock(FileService.class),
                mock(AuthProperties.class),
                mock(SecurityPolicySettings.class),
                (ObjectProvider<MfaService>) mock(ObjectProvider.class),
                (ObjectProvider<com.wensaas.core.api.verification.VerificationCodeService>) mock(ObjectProvider.class),
                mock(StringRedisTemplate.class),
                mock(RoleDefaultScopeStore.class),
                mock(com.wensaas.core.impl.security.SensitiveFieldCipher.class));

        SysUser u = new SysUser();
        u.setId(USER);
        u.setTenantId(TENANT);
        u.setStatus(1);
        when(userMapper.selectById(USER)).thenReturn(u);
        when(userRoleQuery.rolesOfUser(TENANT, USER)).thenReturn(List.of());
    }

    @Test
    void 主部门全路径与副部门名_均取自租户部门快照_不逐级查库() {
        when(deptQuery.deptBindingsOfUser(TENANT, USER)).thenReturn(List.of(
                new DeptBinding(30L, true),
                new DeptBinding(40L, false)));
        // ancestors 段 0 是虚根，快照中不存在，应被跳过
        when(deptQuery.deptInfosOfTenant(TENANT)).thenReturn(Map.of(
                10L, new DeptInfo(10L, "总公司", "/0/"),
                20L, new DeptInfo(20L, "华东区", "/0/10/"),
                30L, new DeptInfo(30L, "上海分部", "/0/10/20/"),
                40L, new DeptInfo(40L, "测试部", "/0/10/")));

        Work work = service.getWork(TENANT, USER, true);

        assertThat(work.mainDeptName()).isEqualTo("上海分部");
        assertThat(work.mainDeptPath()).isEqualTo("总公司 / 华东区 / 上海分部");
        assertThat(work.secondaryDeptNames()).containsExactly("测试部");
        // 0 查库语义：不再走逐级单查入口
        verify(deptQuery, never()).deptById(anyLong());
    }

    @Test
    void 主部门已删除_快照缺失_名称与路径为空_副部门不受影响() {
        when(deptQuery.deptBindingsOfUser(TENANT, USER)).thenReturn(List.of(
                new DeptBinding(99L, true),
                new DeptBinding(40L, false)));
        when(deptQuery.deptInfosOfTenant(TENANT)).thenReturn(Map.of(
                40L, new DeptInfo(40L, "测试部", "/0/")));

        Work work = service.getWork(TENANT, USER, true);

        assertThat(work.mainDeptName()).isNull();
        assertThat(work.mainDeptPath()).isNull();
        assertThat(work.secondaryDeptNames()).containsExactly("测试部");
    }

    @Test
    void 祖先链中间缺失段跳过_其余层级照拼() {
        when(deptQuery.deptBindingsOfUser(TENANT, USER)).thenReturn(List.of(
                new DeptBinding(30L, true)));
        // 中间层 20 缺失（如脏数据），只跳过该段，不截断更高层
        when(deptQuery.deptInfosOfTenant(TENANT)).thenReturn(Map.of(
                10L, new DeptInfo(10L, "总公司", "/0/"),
                30L, new DeptInfo(30L, "上海分部", "/0/10/20/")));

        Work work = service.getWork(TENANT, USER, true);

        assertThat(work.mainDeptPath()).isEqualTo("总公司 / 上海分部");
    }

    @Test
    void 无部门绑定_不取部门快照() {
        when(deptQuery.deptBindingsOfUser(TENANT, USER)).thenReturn(List.of());

        Work work = service.getWork(TENANT, USER, true);

        assertThat(work.mainDeptName()).isNull();
        assertThat(work.mainDeptPath()).isNull();
        assertThat(work.secondaryDeptNames()).isEmpty();
        verify(deptQuery, never()).deptInfosOfTenant(anyLong());
    }
}
