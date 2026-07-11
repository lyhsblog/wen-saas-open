package com.wensaas.core.impl.permission;

import com.wensaas.core.api.auth.user.CapabilitiesService;
import com.wensaas.core.api.auth.org.DeptAdminService;
import com.wensaas.core.api.auth.permission.MemberAdminService;
import com.wensaas.core.api.auth.org.DeptQueryService;
import com.wensaas.core.api.auth.permission.UserRoleQueryService;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.auth.permission.PermissionCatalogService;
import com.wensaas.core.api.auth.permission.RoleAdminService;
import com.wensaas.core.api.auth.permission.TenantRecommendedRoleService;
import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.impl.auth.user.UserCommandService;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplateActivationMapper;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplateMapper;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplatePackageMapper;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplatePermissionMapper;
import com.wensaas.core.impl.auth.user.CapabilitiesServiceImpl;
import com.wensaas.core.impl.auth.login.LoginAttemptGuard;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.auth.password.PasswordResetTokenService;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.security.SensitiveFieldCipher;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;
import com.wensaas.core.impl.pkg.SysPackageMapper;
import com.wensaas.core.impl.pkg.SysPackageModuleMapper;
import com.wensaas.core.impl.pkg.TenantPlansResolver;
import com.wensaas.core.impl.spi.registry.DataPermissionTableRegistry;
import com.wensaas.core.api.menu.MenuQueryService;
import com.wensaas.core.impl.spi.module.SysBusinessModuleMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.wensaas.core.impl.permission.eval.MenuPermissionConsistencyValidator;
import com.wensaas.core.impl.permission.datascope.CrossDataPermissionQueryAspect;
import com.wensaas.core.impl.permission.datascope.DataPermissionActionRegistry;
import com.wensaas.core.impl.permission.datascope.DataPermissionAspect;
import com.wensaas.core.impl.permission.datascope.DataPermissionHandlerImpl;
import com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore;
import com.wensaas.core.impl.permission.datascope.SysRoleDataScopeMapper;
import com.wensaas.core.impl.permission.eval.PermissionCacheEvictor;
import com.wensaas.core.impl.permission.eval.PermissionCatalogServiceImpl;
import com.wensaas.core.impl.permission.eval.PermissionEvalMapper;
import com.wensaas.core.impl.permission.eval.PermissionResolver;
import com.wensaas.core.impl.permission.eval.RequestScopedPermissionResolver;
import com.wensaas.core.impl.permission.eval.RequirePermissionAspect;
import com.wensaas.core.impl.permission.eval.SysRolePermissionMapper;
import com.wensaas.core.impl.permission.org.DeptAdminServiceImpl;
import com.wensaas.core.impl.permission.org.DeptService;
import com.wensaas.core.impl.permission.org.DeptQueryServiceImpl;
import com.wensaas.core.impl.permission.org.MemberAdminServiceImpl;
import com.wensaas.core.impl.permission.org.SysDeptLeaderMapper;
import com.wensaas.core.impl.permission.org.SysDeptMapper;
import com.wensaas.core.impl.permission.org.SysUserDeptMapper;
import com.wensaas.core.impl.permission.role.RoleAdminServiceImpl;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.permission.role.TenantRecommendedRoleServiceImpl;
import com.wensaas.core.impl.permission.role.UserRoleQueryServiceImpl;

/**
 * 权限引擎装配。
 *
 * <p>{@code wen-saas-boot.permission.enabled=false} 时整体不装配（AOP + 数据权限拦截器都不生效）；
 * 默认开启。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PermissionProperties.class)
@ConditionalOnProperty(prefix = "wen-saas-boot.permission", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PermissionConfiguration {

    @Bean
    public DeptService deptService(SysDeptMapper deptMapper, CacheRegionFactory cacheRegionFactory,
                                    PermissionProperties props) {
        return new DeptService(deptMapper, cacheRegionFactory, props.getCacheTtl());
    }

    @Bean
    public UserRoleQueryService userRoleQueryService(SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper) {
        return new UserRoleQueryServiceImpl(userRoleMapper, roleMapper);
    }

    @Bean
    public DeptQueryService deptQueryService(SysUserDeptMapper userDeptMapper, SysDeptMapper deptMapper,
                                             DeptService deptService) {
        return new DeptQueryServiceImpl(userDeptMapper, deptMapper, deptService);
    }

    @Bean
    public PermissionResolver permissionResolver(PermissionEvalMapper permissionEvalMapper, DeptService deptService) {
        return new RequestScopedPermissionResolver(permissionEvalMapper, deptService);
    }

    @Bean
    public PermissionCacheEvictor permissionCacheEvictor(DeptService deptService) {
        return new PermissionCacheEvictor(deptService);
    }

    @Bean
    public RequirePermissionAspect requirePermissionAspect(PermissionResolver resolver) {
        return new RequirePermissionAspect(resolver);
    }

    @Bean
    public DataPermissionAspect dataPermissionAspect() {
        return new DataPermissionAspect();
    }

    @Bean
    public CrossDataPermissionQueryAspect crossDataPermissionQueryAspect() {
        return new CrossDataPermissionQueryAspect();
    }

    @Bean
    public DataPermissionHandlerImpl dataPermissionHandler(DataPermissionTableRegistry registry,
                                                             ObjectProvider<PermissionResolver> resolverProvider) {
        return new DataPermissionHandlerImpl(registry, resolverProvider);
    }

    @Bean
    public DeptAdminService deptAdminService(SysDeptMapper deptMapper,
                                              SysUserDeptMapper userDeptMapper,
                                              SysDeptLeaderMapper deptLeaderMapper,
                                              SysUserMapper userMapper,
                                              PermissionCacheEvictor permissionCacheEvictor) {
        return new DeptAdminServiceImpl(deptMapper, userDeptMapper, deptLeaderMapper, userMapper, permissionCacheEvictor);
    }

    @Bean
    public DataPermissionActionRegistry dataPermissionActionRegistry() {
        return new DataPermissionActionRegistry();
    }

    @Bean
    public RoleDefaultScopeStore roleDefaultScopeStore(SysRoleDataScopeMapper roleDataScopeMapper) {
        return new RoleDefaultScopeStore(roleDataScopeMapper);
    }

    /** 底座租户域权限点注册（构造期写入 PermissionRegistry，作权限码代码权威）。 */
    @Bean
    public BasePermissionContributor basePermissionContributor(PermissionRegistry permissionRegistry) {
        return new BasePermissionContributor(permissionRegistry);
    }

    @Bean
    public PermissionCatalogService permissionCatalogService(PermissionRegistry permissionRegistry,
                                                              DataPermissionActionRegistry actionRegistry) {
        return new PermissionCatalogServiceImpl(permissionRegistry, actionRegistry);
    }

    /** 启动期校验菜单 ↔ 权限码双向一致性。 */
    @Bean
    public MenuPermissionConsistencyValidator menuPermissionConsistencyValidator(
            org.springframework.beans.factory.ListableBeanFactory beanFactory,
            PermissionRegistry permissionRegistry,
            PermissionCatalogService permissionCatalogService,
            com.wensaas.core.impl.menu.SysMenuMapper menuMapper) {
        return new MenuPermissionConsistencyValidator(
                beanFactory, permissionRegistry, permissionCatalogService, menuMapper);
    }

    @Bean
    public MemberAdminService memberAdminService(SysUserMapper userMapper,
                                                 UserCommandService userCommandService,
                                                 SysUserDeptMapper userDeptMapper,
                                                 SysUserRoleMapper userRoleMapper,
                                                 SysDeptMapper deptMapper,
                                                 SysDeptLeaderMapper deptLeaderMapper,
                                                 SysRoleMapper roleMapper,
                                                 TenantQueryService tenantQuery,
                                                 PasswordHasher passwordHasher,
                                                 PasswordResetTokenService resetTokenService,
                                                 SessionStore sessionStore,
                                                 LoginAttemptGuard loginAttemptGuard,
                                                 SensitiveFieldCipher sensitiveFieldCipher,
                                                 com.wensaas.core.api.dict.DictService dictService,
                                                 com.wensaas.core.api.auth.oauth.OAuthBindingService oauthBindingService,
                                                 com.wensaas.core.impl.audit.log.SysLoginLogMapper loginLogMapper,
                                                 com.wensaas.core.impl.namesource.TransFiller transFiller,
                                                 org.springframework.beans.factory.ObjectProvider<com.wensaas.core.api.event.DomainEventPublisher> eventPublisher) {
        // 事件总线可关（wen-saas-boot.event.enabled=false 时无 Publisher bean）：可空注入，发布点判空
        return new MemberAdminServiceImpl(userMapper, userCommandService, userDeptMapper, userRoleMapper, deptMapper, deptLeaderMapper,
                roleMapper, tenantQuery, passwordHasher, resetTokenService, sessionStore, loginAttemptGuard,
                sensitiveFieldCipher, dictService, oauthBindingService, loginLogMapper, transFiller,
                eventPublisher.getIfAvailable());
    }

    @Bean
    public RoleAdminService roleAdminService(SysRoleMapper roleMapper,
                                              SysRolePermissionMapper rolePermissionMapper,
                                              SysUserRoleMapper userRoleMapper,
                                              SysUserMapper userMapper,
                                              SysRoleDataScopeMapper roleDataScopeMapper,
                                              RoleDefaultScopeStore roleDefaultScopeStore,
                                              com.wensaas.core.impl.pkg.PackageModuleGate packageModuleGate) {
        return new RoleAdminServiceImpl(roleMapper, rolePermissionMapper, userRoleMapper,
                userMapper, roleDataScopeMapper, roleDefaultScopeStore, packageModuleGate);
    }

    @Bean
    public TenantRecommendedRoleService tenantRecommendedRoleService(
            SysRecommendedRoleTemplateMapper templateMapper,
            SysRecommendedRoleTemplatePackageMapper packageMapper,
            SysRecommendedRoleTemplatePermissionMapper permissionMapper,
            SysRecommendedRoleTemplateActivationMapper activationMapper,
            SysRoleMapper roleMapper,
            SysRolePermissionMapper rolePermissionMapper,
            RoleDefaultScopeStore roleDefaultScopeStore,
            TenantPlansResolver tenantPlansResolver,
            PermissionCatalogService permissionCatalogService) {
        return new TenantRecommendedRoleServiceImpl(templateMapper, packageMapper, permissionMapper,
                activationMapper, roleMapper, rolePermissionMapper, roleDefaultScopeStore,
                tenantPlansResolver, permissionCatalogService);
    }

    @Bean
    public TenantPlansResolver tenantPlansResolver(TenantQueryService tenantQuery,
                                                    SysPackageMapper packageMapper,
                                                    SysPackageModuleMapper packageModuleMapper) {
        return new TenantPlansResolver(tenantQuery, packageMapper, packageModuleMapper);
    }

    @Bean
    public com.wensaas.core.api.menu.PermissionCodeCatalogService permissionCodeCatalogService(
            PermissionRegistry permissionRegistry,
            PermissionCatalogService permissionCatalogService,
            DataPermissionActionRegistry dataPermissionActionRegistry) {
        return new com.wensaas.core.impl.menu.PermissionCodeCatalogServiceImpl(
                permissionRegistry, permissionCatalogService, dataPermissionActionRegistry);
    }

    @Bean
    public MenuQueryService menuQueryService(com.wensaas.core.impl.menu.SysMenuMapper menuMapper,
                                             com.wensaas.core.api.menu.PermissionCodeCatalogService permissionCodeCatalogService,
                                             com.wensaas.core.impl.pkg.PackageModuleGate packageModuleGate) {
        return new com.wensaas.core.impl.menu.MenuQueryServiceImpl(menuMapper, permissionCodeCatalogService,
                packageModuleGate);
    }

    @Bean
    public com.wensaas.core.api.menu.MenuAdminService menuAdminService(
            com.wensaas.core.impl.menu.SysMenuMapper menuMapper,
            com.wensaas.core.api.menu.PermissionCodeCatalogService permissionCodeCatalogService) {
        return new com.wensaas.core.impl.menu.MenuAdminServiceImpl(menuMapper, permissionCodeCatalogService);
    }

    @Bean
    public CapabilitiesService capabilitiesService(PermissionResolver permissionResolver,
                                                    SysBusinessModuleMapper moduleMapper,
                                                    MenuQueryService menuQueryService,
                                                    TenantPlansResolver tenantPlansResolver,
                                                    SessionStore sessionStore,
                                                    SysUserMapper userMapper,
                                                    TenantQueryService tenantQuery) {
        return new CapabilitiesServiceImpl(permissionResolver, moduleMapper, menuQueryService,
                tenantPlansResolver, sessionStore, userMapper, tenantQuery);
    }
}
