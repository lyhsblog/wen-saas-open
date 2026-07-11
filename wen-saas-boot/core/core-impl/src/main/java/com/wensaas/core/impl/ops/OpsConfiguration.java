package com.wensaas.core.impl.ops;

import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.ops.OpsBusinessModuleService;
import com.wensaas.core.api.ops.OpsProfileService;
import com.wensaas.core.api.ops.TenantOpsService;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminService;
import com.wensaas.core.api.ops.role.RecommendedRoleAdminService;
import com.wensaas.core.api.pkg.PackageAdminService;
import com.wensaas.core.impl.audit.log.SysLoginLogMapper;
import com.wensaas.core.impl.audit.log.SysOperationLogMapper;
import com.wensaas.core.impl.auth.AuthProperties;
import com.wensaas.core.impl.auth.login.LoginAttemptGuard;
import com.wensaas.core.impl.auth.mfa.MfaService;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.auth.password.PasswordResetTokenService;
import com.wensaas.core.impl.auth.password.PasswordResetTokenServiceImpl;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.password.SysPasswordResetTokenMapper;
import com.wensaas.core.impl.auth.password.SecurityPolicySettings;
import com.wensaas.core.impl.auth.user.UserCommandService;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.notification.template.NotificationTemplateAdminServiceImpl;
import com.wensaas.core.impl.notification.template.SysNotificationTemplateMapper;
import com.wensaas.core.impl.notification.template.TemplateResolver;
import com.wensaas.core.impl.ops.spi.OpsBusinessModuleServiceImpl;
import com.wensaas.core.impl.pkg.ModuleApiRouteRegistry;
import com.wensaas.core.impl.pkg.PackageAdminServiceImpl;
import com.wensaas.core.impl.ops.role.RecommendedRoleAdminServiceImpl;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplateActivationMapper;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplateMapper;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplatePackageMapper;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplatePermissionMapper;
import com.wensaas.core.impl.pkg.SysPackageMapper;
import com.wensaas.core.impl.pkg.SysPackageModuleMapper;
import com.wensaas.core.impl.pkg.TenantCacheEvictor;
import com.wensaas.core.impl.pkg.TenantPlansResolver;
import com.wensaas.core.impl.spi.module.BusinessModuleRegistry;
import com.wensaas.core.impl.spi.registry.DashboardCardRegistry;
import com.wensaas.core.impl.spi.registry.DictRegistry;
import com.wensaas.core.impl.spi.registry.H5PageRegistry;
import com.wensaas.core.impl.spi.registry.NotificationTemplateRegistry;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;
import com.wensaas.core.impl.spi.module.SysBusinessModuleMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 运营功能 Bean 装配：密码重置、推荐角色 / 套餐 / 业务模块管理、平台默认通知模板维护、平台监控等运营端能力
 * （工作台 / 系统管理 / 团队 / 操作日志见 {@link OpsTeamConfiguration}）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpsProperties.class)
public class OpsConfiguration {

    /** 初始运营超管邮箱回填（密文列无法由迁移 SQL 写入，启动期加密补种）。 */
    @Bean
    public InitialOpsAdminEmailSeeder initialOpsAdminEmailSeeder(
            SysUserMapper userMapper, UserCommandService userCommandService, OpsProperties opsProperties) {
        return new InitialOpsAdminEmailSeeder(
                userMapper, userCommandService, opsProperties.getInitialAdminEmail());
    }

    @Bean
    public PasswordResetTokenService passwordResetTokenService(
            SysPasswordResetTokenMapper tokenMapper,
            AuthProperties authProperties) {
        return new PasswordResetTokenServiceImpl(tokenMapper, authProperties);
    }

    // ===== 运营租户管理（M18 God Class 拆分）：共享件 + 4 协作类 + 门面 =====

    @Bean
    public TenantOpsSupport tenantOpsSupport(
            SysTenantMapper tenantMapper,
            com.wensaas.core.api.ops.access.OpsAccessContextResolver accessResolver) {
        return new TenantOpsSupport(tenantMapper, accessResolver);
    }

    /** 开通激活邮件直投器（EMAIL 通道适配器可能因通知模块关闭而缺席，用 ObjectProvider 容忍）。 */
    @Bean
    public ActivationEmailSender activationEmailSender(
            ObjectProvider<com.wensaas.core.impl.notification.adapter.EmailChannelAdapter> emailAdapter) {
        return new ActivationEmailSender(emailAdapter.getIfAvailable());
    }

    @Bean
    public TenantProvisioningService tenantProvisioningService(
            TenantOpsSupport tenantOpsSupport,
            SysTenantMapper tenantMapper,
            SysUserMapper userMapper,
            UserCommandService userCommandService,
            SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper,
            SysTenantSeatAddonMapper seatAddonMapper,
            SysPackageMapper packageMapper,
            PasswordResetTokenService passwordResetTokenService,
            PasswordHasher passwordHasher,
            com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore roleDefaultScopeStore,
            com.wensaas.core.impl.tenant.TenantProperties tenantProperties,
            ActivationEmailSender activationEmailSender) {
        return new TenantProvisioningService(tenantOpsSupport, tenantMapper, userMapper, userCommandService,
                roleMapper, userRoleMapper, seatAddonMapper, packageMapper, passwordResetTokenService,
                passwordHasher, roleDefaultScopeStore, tenantProperties, activationEmailSender);
    }

    @Bean
    public TenantPlanOpsService tenantPlanOpsService(
            TenantOpsSupport tenantOpsSupport,
            SysTenantMapper tenantMapper,
            SysPackageMapper packageMapper,
            SysTenantSeatAddonMapper seatAddonMapper,
            com.wensaas.core.impl.ops.monitor.SysTenantRenewalLogMapper renewalLogMapper,
            TenantCacheEvictor tenantCacheEvictor) {
        return new TenantPlanOpsService(tenantOpsSupport, tenantMapper, packageMapper, seatAddonMapper,
                renewalLogMapper, tenantCacheEvictor);
    }

    @Bean
    public TenantMemberOpsService tenantMemberOpsService(
            TenantOpsSupport tenantOpsSupport,
            SysTenantMapper tenantMapper,
            SysUserMapper userMapper,
            UserCommandService userCommandService,
            SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper,
            PasswordHasher passwordHasher,
            PasswordResetTokenService passwordResetTokenService,
            LoginAttemptGuard loginAttemptGuard) {
        return new TenantMemberOpsService(tenantOpsSupport, tenantMapper, userMapper, userCommandService, roleMapper,
                userRoleMapper, passwordHasher, passwordResetTokenService, loginAttemptGuard);
    }

    @Bean
    public TenantDetailOpsService tenantDetailOpsService(
            TenantOpsSupport tenantOpsSupport,
            SysTenantMapper tenantMapper,
            SysUserMapper userMapper,
            SysTenantSeatAddonMapper seatAddonMapper,
            SysOperationLogMapper operationLogMapper,
            TenantPlansResolver plansResolver,
            com.wensaas.core.api.ops.access.OpsAccessContextResolver accessResolver,
            com.wensaas.core.impl.namesource.EntityNameResolver entityNameResolver,
            TenantCacheEvictor tenantCacheEvictor) {
        return new TenantDetailOpsService(tenantOpsSupport, tenantMapper, userMapper, seatAddonMapper,
                operationLogMapper, plansResolver, accessResolver, entityNameResolver, tenantCacheEvictor);
    }

    @Bean
    public TenantOpsService tenantOpsService(
            TenantProvisioningService tenantProvisioningService,
            TenantPlanOpsService tenantPlanOpsService,
            TenantMemberOpsService tenantMemberOpsService,
            TenantDetailOpsService tenantDetailOpsService) {
        return new TenantOpsServiceImpl(tenantProvisioningService, tenantPlanOpsService,
                tenantMemberOpsService, tenantDetailOpsService);
    }

    @Bean
    public OpsProfileService opsProfileService(
            SysUserMapper userMapper,
            UserCommandService userCommandService,
            SysUserRoleMapper userRoleMapper,
            SysRoleMapper roleMapper,
            SysLoginLogMapper loginLogMapper,
            SessionStore sessionStore,
            PasswordHasher passwordHasher,
            FileService fileService,
            AuthProperties authProperties,
            ObjectProvider<MfaService> mfaServiceProvider,
            org.springframework.beans.factory.ObjectProvider<com.wensaas.core.api.verification.VerificationCodeService> verificationCodeServiceProvider,
            org.springframework.data.redis.core.StringRedisTemplate redis,
            SecurityPolicySettings securityPolicy) {
        return new OpsProfileServiceImpl(userMapper, userCommandService, userRoleMapper, roleMapper, loginLogMapper,
                sessionStore, passwordHasher, fileService, authProperties, mfaServiceProvider,
                verificationCodeServiceProvider, redis, securityPolicy);
    }

    @Bean
    public PackageAdminService packageAdminService(
            SysPackageMapper packageMapper,
            SysPackageModuleMapper packageModuleMapper,
            SysBusinessModuleMapper businessModuleMapper,
            com.wensaas.core.impl.pkg.ModuleApiRouteRegistry apiRouteRegistry,
            TenantOpsService tenantOpsService) {
        return new PackageAdminServiceImpl(packageMapper, packageModuleMapper, businessModuleMapper,
                apiRouteRegistry, tenantOpsService);
    }

    @Bean
    public OpsBusinessModuleService opsBusinessModuleService(
            SysBusinessModuleMapper businessModuleMapper,
            BusinessModuleRegistry moduleRegistry,
            PermissionRegistry permissionRegistry,
            DictRegistry dictRegistry,
            DashboardCardRegistry dashboardCardRegistry,
            H5PageRegistry h5PageRegistry,
            NotificationTemplateRegistry notificationTemplateRegistry,
            ModuleApiRouteRegistry apiRouteRegistry,
            SysPackageMapper packageMapper,
            SysPackageModuleMapper packageModuleMapper) {
        return new OpsBusinessModuleServiceImpl(businessModuleMapper, moduleRegistry,
                permissionRegistry, dictRegistry, dashboardCardRegistry, h5PageRegistry,
                notificationTemplateRegistry, apiRouteRegistry, packageMapper, packageModuleMapper);
    }

    @Bean
    public RecommendedRoleAdminService recommendedRoleAdminService(
            SysRecommendedRoleTemplateMapper templateMapper,
            SysRecommendedRoleTemplatePackageMapper templatePackageMapper,
            SysRecommendedRoleTemplatePermissionMapper templatePermissionMapper,
            SysRecommendedRoleTemplateActivationMapper templateActivationMapper,
            SysPackageMapper packageMapper,
            PermissionRegistry permissionRegistry,
            TenantOpsService tenantOpsService,
            SysTenantMapper tenantMapper,
            SysUserMapper userMapper,
            com.wensaas.core.impl.permission.role.SysRoleMapper roleMapper,
            com.wensaas.core.impl.permission.role.SysUserRoleMapper userRoleMapper,
            com.wensaas.core.api.menu.MenuQueryService menuQueryService,
            com.wensaas.core.impl.pkg.PackageModuleGate packageModuleGate) {
        return new RecommendedRoleAdminServiceImpl(templateMapper, templatePackageMapper,
                templatePermissionMapper, templateActivationMapper, packageMapper, permissionRegistry,
                tenantOpsService, tenantMapper, userMapper, roleMapper, userRoleMapper,
                menuQueryService, packageModuleGate);
    }

    @Bean
    public NotificationTemplateAdminService notificationTemplateAdminService(
            SysNotificationTemplateMapper notificationTemplateMapper,
            ObjectProvider<TemplateResolver> templateResolverProvider) {
        return new NotificationTemplateAdminServiceImpl(notificationTemplateMapper, templateResolverProvider);
    }

    @Bean
    public com.wensaas.core.impl.ops.health.HealthScoreSettings healthScoreSettings(
            ObjectProvider<com.wensaas.core.api.ops.setting.OpsSettingService> settingProvider) {
        return new com.wensaas.core.impl.ops.health.HealthScoreSettings(settingProvider);
    }

    @Bean
    public com.wensaas.core.impl.ops.health.HealthScoreCalculator healthScoreCalculator(
            SysLoginLogMapper loginLogMapper,
            com.wensaas.core.api.auth.user.MemberCountQuery memberCount,
            SysOperationLogMapper operationLogMapper,
            com.wensaas.core.impl.ticket.TicketTicketMapper ticketMapper,
            com.wensaas.core.impl.ops.monitor.SysTenantRenewalLogMapper renewalLogMapper,
            com.wensaas.core.impl.ops.health.HealthScoreSettings healthScoreSettings) {
        return new com.wensaas.core.impl.ops.health.HealthScoreCalculator(
                loginLogMapper, memberCount, operationLogMapper, ticketMapper, renewalLogMapper, healthScoreSettings);
    }

    @Bean
    public com.wensaas.core.api.ops.health.OpsHealthService opsHealthService(
            com.wensaas.core.impl.ops.health.SysTenantHealthMapper healthMapper,
            SysTenantMapper tenantMapper,
            SysPackageMapper packageMapper,
            com.wensaas.core.impl.ops.health.HealthScoreCalculator healthScoreCalculator,
            com.wensaas.core.api.ops.access.OpsAccessContextResolver accessResolver) {
        return new com.wensaas.core.impl.ops.health.OpsHealthServiceImpl(
                healthMapper, tenantMapper, packageMapper, healthScoreCalculator, accessResolver);
    }

    // ===== 平台监控看板（O9）：模块访问埋点 =====

    @Bean
    public com.wensaas.core.impl.ops.monitor.ModuleAccessBatchBuffer moduleAccessBatchBuffer(
            com.wensaas.core.impl.ops.monitor.SysModuleAccessLogMapper mapper) {
        return new com.wensaas.core.impl.ops.monitor.ModuleAccessBatchBuffer(mapper);
    }

    @Bean
    public com.wensaas.core.impl.ops.monitor.ModuleAccessLogFilter moduleAccessLogFilter(
            ModuleApiRouteRegistry apiRouteRegistry,
            com.wensaas.core.impl.ops.monitor.ModuleAccessBatchBuffer buffer) {
        return new com.wensaas.core.impl.ops.monitor.ModuleAccessLogFilter(apiRouteRegistry, buffer);
    }

    @Bean
    public com.wensaas.core.api.ops.monitor.OpsPlatformMonitorService opsPlatformMonitorService(
            SysTenantMapper tenantMapper,
            SysLoginLogMapper loginLogMapper,
            com.wensaas.core.impl.openapi.calllog.SysApiCallLogMapper apiCallLogMapper,
            com.wensaas.core.impl.webhook.SysWebhookLogMapper webhookLogMapper,
            com.wensaas.core.impl.webhook.SysWebhookMapper webhookMapper,
            com.wensaas.core.impl.ticket.TicketTicketMapper ticketMapper,
            com.wensaas.core.impl.file.SysFileMapper fileMapper,
            SysPackageMapper packageMapper,
            SysBusinessModuleMapper businessModuleMapper,
            com.wensaas.core.impl.ops.monitor.SysModuleAccessLogMapper moduleAccessMapper,
            com.wensaas.core.impl.ops.monitor.SysTenantRenewalLogMapper renewalLogMapper,
            SysTenantSeatAddonMapper seatAddonMapper,
            com.wensaas.core.api.ops.access.OpsAccessContextResolver accessResolver) {
        return new com.wensaas.core.impl.ops.monitor.OpsPlatformMonitorServiceImpl(
                tenantMapper, loginLogMapper, apiCallLogMapper, webhookLogMapper, webhookMapper,
                ticketMapper, fileMapper, packageMapper, businessModuleMapper, moduleAccessMapper,
                renewalLogMapper, seatAddonMapper, accessResolver);
    }

    @Bean
    public OpsAuthWebFilter opsAuthWebFilter() {
        return new OpsAuthWebFilter();
    }

    @Bean
    public FilterRegistrationBean<OpsAuthWebFilter> opsAuthWebFilterRegistration(OpsAuthWebFilter filter) {
        FilterRegistrationBean<OpsAuthWebFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/v1/ops/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 160);
        return reg;
    }
}
