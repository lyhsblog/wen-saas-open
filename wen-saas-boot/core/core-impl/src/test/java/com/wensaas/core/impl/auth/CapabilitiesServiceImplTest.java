package com.wensaas.core.impl.auth;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.user.CapabilitiesService;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.menu.MenuQueryService;
import com.wensaas.core.api.menu.MenuQueryService.NavNode;
import com.wensaas.core.impl.auth.session.Session;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.CapabilitiesServiceImpl;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.permission.datascope.DataScope;
import com.wensaas.core.impl.permission.eval.PermissionResolver;
import com.wensaas.core.impl.permission.eval.UserPermissions;
import com.wensaas.core.impl.pkg.TenantPlansResolver;
import com.wensaas.core.impl.spi.module.SysBusinessModule;
import com.wensaas.core.impl.spi.module.SysBusinessModuleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilitiesServiceImplTest {

    private static final long TENANT_ID = 9001L;
    private static final long USER_ID = 1234567890L;

    private PermissionResolver permissionResolver;
    private SysBusinessModuleMapper moduleMapper;
    private MenuQueryService menuQueryService;
    private TenantPlansResolver plansResolver;
    private SessionStore sessionStore;
    private SysUserMapper userMapper;
    private TenantQueryService tenantQuery;
    private CapabilitiesServiceImpl service;

    @BeforeEach
    void setup() {
        permissionResolver = mock(PermissionResolver.class);
        moduleMapper = mock(SysBusinessModuleMapper.class);
        menuQueryService = mock(MenuQueryService.class);
        plansResolver = mock(TenantPlansResolver.class);
        sessionStore = mock(SessionStore.class);
        userMapper = mock(SysUserMapper.class);
        tenantQuery = mock(TenantQueryService.class);
        service = new CapabilitiesServiceImpl(permissionResolver, moduleMapper, menuQueryService,
                plansResolver, sessionStore, userMapper, tenantQuery);
        // 默认导航空树 —— 大多数测试不关心菜单
        when(menuQueryService.navTree(anyInt(), any(), anyBoolean(), any())).thenReturn(List.of());

        UserContext.set(new UserPrincipal(USER_ID, TENANT_ID, "alice", "sess-1",
                false, 100L, false, null));
        // 默认 plans 空 —— 大多数测试不关心 plans
        when(plansResolver.resolve(anyLong())).thenReturn(TenantPlansResolver.ResolvedPlans.empty());
        // 默认：用户有真实姓名；租户无水印偏好行（NULL）→ 水印默认开启
        SysUser self = new SysUser();
        self.setRealName("爱丽丝");
        when(userMapper.selectById(USER_ID)).thenReturn(self);
        when(tenantQuery.tenantById(anyLong())).thenReturn(null);
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    @Test
    void resolve_basicUser_sortsPermissions_andDefaultsDataScope() {
        when(permissionResolver.resolve(TENANT_ID, USER_ID))
                .thenReturn(new UserPermissions(
                        Set.of("dict.group.list", "org.member.create", "auth.login"),
                        DataScope.SELF, Set.of()));

        CapabilitiesService.Capabilities cap = service.resolve();

        assertThat(cap.userId()).isEqualTo(String.valueOf(USER_ID));
        assertThat(cap.tenantId()).isEqualTo(String.valueOf(TENANT_ID));
        assertThat(cap.tenantOwner()).isFalse();
        assertThat(cap.permissions()).containsExactly("auth.login", "dict.group.list", "org.member.create");
        assertThat(cap.dataScope()).isEqualTo("SELF");
        assertThat(cap.visibleDeptIds()).isEmpty();
        assertThat(cap.enabledModules()).isEmpty();
        assertThat(cap.activePackages()).isEmpty();
        assertThat(cap.menus()).isEmpty();
        // 普通会话（sessionStore 默认返回 empty）→ 非代登录态
        assertThat(cap.impersonating()).isFalse();
        assertThat(cap.operatorName()).isNull();
        // 防截屏水印：真实姓名透传 + 租户无偏好行默认开启
        assertThat(cap.realName()).isEqualTo("爱丽丝");
        assertThat(cap.screenshotWatermark()).isTrue();
    }

    @Test
    void resolve_screenshotWatermarkDisabledByTenant_returnsFalse() {
        when(permissionResolver.resolve(anyLong(), anyLong()))
                .thenReturn(new UserPermissions(Set.of(), DataScope.SELF, Set.of()));
        TenantInfo tenant = new TenantInfo(TENANT_ID, "c", "n", 1, null, null, null, false, null);
        when(tenantQuery.tenantById(TENANT_ID)).thenReturn(tenant);

        CapabilitiesService.Capabilities cap = service.resolve();

        assertThat(cap.screenshotWatermark()).isFalse();
    }

    @Test
    void resolve_impersonatedSession_exposesOperatorName() {
        when(permissionResolver.resolve(anyLong(), anyLong()))
                .thenReturn(new UserPermissions(Set.of(), DataScope.SELF, Set.of()));
        // 当前 session 为代登录态：originalSessionId 非空 + operatorName 已快照
        Session impSession = new Session("sess-1", USER_ID, TENANT_ID, "alice", false, 100L,
                Instant.now(), Instant.now(), "ops-orig-sid", "opsAdmin");
        when(sessionStore.findBySessionId("sess-1")).thenReturn(Optional.of(impSession));

        CapabilitiesService.Capabilities cap = service.resolve();

        assertThat(cap.impersonating()).isTrue();
        assertThat(cap.operatorName()).isEqualTo("opsAdmin");
    }

    @Test
    void resolve_deptAndBelow_includesSortedDeptIds() {
        when(permissionResolver.resolve(anyLong(), anyLong()))
                .thenReturn(new UserPermissions(Set.of(), DataScope.DEPT_AND_BELOW,
                        Set.of(120L, 100L, 110L)));

        CapabilitiesService.Capabilities cap = service.resolve();

        assertThat(cap.dataScope()).isEqualTo("DEPT_AND_BELOW");
        assertThat(cap.visibleDeptIds()).containsExactly("100", "110", "120");
    }

    @Test
    void resolve_allScope_omitsDeptIds_evenIfPresent() {
        when(permissionResolver.resolve(anyLong(), anyLong()))
                .thenReturn(new UserPermissions(Set.of("admin.all"), DataScope.ALL, Set.of(999L)));

        CapabilitiesService.Capabilities cap = service.resolve();

        assertThat(cap.dataScope()).isEqualTo("ALL");
        assertThat(cap.visibleDeptIds()).isEmpty();
    }

    @Test
    void resolve_enabledModules_filteredByActiveTenantPlans() {
        when(permissionResolver.resolve(anyLong(), anyLong()))
                .thenReturn(new UserPermissions(Set.of(), DataScope.SELF, Set.of()));
        when(plansResolver.resolve(anyLong())).thenReturn(new TenantPlansResolver.ResolvedPlans(
                Set.of("crm", "hrm"), Set.of(),
                List.of(new CapabilitiesService.PackageView("crm-pro", "CRM 专业版", null, false))));
        // moduleMapper 仅返 crm（只有 crm 在已注册业务模块 ∩ plans 的交集中）
        SysBusinessModule m1 = newModule("crm", "客户管理", "1.0.0");
        when(moduleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(m1));

        CapabilitiesService.Capabilities cap = service.resolve();

        assertThat(cap.enabledModules()).hasSize(1);
        assertThat(cap.enabledModules().get(0).code()).isEqualTo("crm");
        assertThat(cap.enabledModules().get(0).readonly()).isFalse();
        assertThat(cap.activePackages()).hasSize(1);
        assertThat(cap.activePackages().get(0).code()).isEqualTo("crm-pro");
    }

    @Test
    void resolve_enabledModules_emptyPlans_returnsEmpty() {
        when(permissionResolver.resolve(anyLong(), anyLong()))
                .thenReturn(new UserPermissions(Set.of(), DataScope.SELF, Set.of()));
        // 无 plans → 即使平台有 sys_business_module，也不应返回（保护查询：visibleCodes 为空时跳过 mapper）
        CapabilitiesService.Capabilities cap = service.resolve();

        assertThat(cap.enabledModules()).isEmpty();
        assertThat(cap.activePackages()).isEmpty();
    }

    @Test
    void resolve_expiredPlan_marksReadonly() {
        when(permissionResolver.resolve(anyLong(), anyLong()))
                .thenReturn(new UserPermissions(Set.of(), DataScope.SELF, Set.of()));
        when(plansResolver.resolve(anyLong())).thenReturn(new TenantPlansResolver.ResolvedPlans(
                Set.of(), Set.of("crm"),
                List.of(new CapabilitiesService.PackageView("crm-trial", "CRM 试用", "2026-01-01T00:00:00Z", true))));
        when(moduleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(newModule("crm", "客户管理", "1.0.0")));

        CapabilitiesService.Capabilities cap = service.resolve();

        assertThat(cap.enabledModules()).hasSize(1);
        assertThat(cap.enabledModules().get(0).readonly()).isTrue();
        assertThat(cap.activePackages().get(0).readonly()).isTrue();
    }

    @Test
    void resolve_menus_mapsNavTreeFromMenuQueryService() {
        when(permissionResolver.resolve(anyLong(), anyLong()))
                .thenReturn(new UserPermissions(Set.of(), DataScope.SELF, Set.of()));
        // 普通租户账号（tenantId != 0）→ navTree 走 SCOPE_TENANT；菜单结构直接来自 MenuQueryService
        when(menuQueryService.navTree(eq(MenuQueryService.SCOPE_TENANT), any(), anyBoolean(), any()))
                .thenReturn(List.of(
                        new NavNode("g.system", "系统管理", "Setting", null, 30, List.of(
                                new NavNode("dict", "字典管理", "Book", "/system/dictionaries", 10, List.of()))),
                        new NavNode("crm", "客户", "User", "/crm-demo/customers", 10, List.of())));

        CapabilitiesService.Capabilities cap = service.resolve();

        // NavNode → MenuNode 映射：GROUP path 为 null、label → name、children 递归保序
        assertThat(cap.menus()).extracting(CapabilitiesService.MenuNode::name)
                .containsExactly("系统管理", "客户");
        CapabilitiesService.MenuNode group = cap.menus().get(0);
        assertThat(group.path()).isNull();
        assertThat(group.children()).extracting(CapabilitiesService.MenuNode::path)
                .containsExactly("/system/dictionaries");
        CapabilitiesService.MenuNode crm = cap.menus().get(1);
        assertThat(crm.path()).isEqualTo("/crm-demo/customers");
        assertThat(crm.icon()).isEqualTo("User");
    }

    @Test
    void resolve_tenantOwner_passesThrough() {
        UserContext.set(new UserPrincipal(USER_ID, TENANT_ID, "owner", "sess-2",
                true, null, false, null));
        when(permissionResolver.resolve(anyLong(), anyLong()))
                .thenReturn(new UserPermissions(Set.of("everything"), DataScope.ALL, Set.of()));

        CapabilitiesService.Capabilities cap = service.resolve();

        assertThat(cap.tenantOwner()).isTrue();
    }

    private SysBusinessModule newModule(String code, String name, String version) {
        SysBusinessModule m = new SysBusinessModule();
        m.setCode(code);
        m.setName(name);
        m.setVersion(version);
        return m;
    }
}
