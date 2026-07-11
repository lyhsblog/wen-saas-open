package com.wensaas.core.impl.tenant;

import com.wensaas.core.api.auth.org.DeptQueryService;
import com.wensaas.core.api.auth.permission.UserRoleQueryService;
import com.wensaas.core.api.auth.user.UserQueryService;
import com.wensaas.core.api.compliance.ComplianceService;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.notification.inbox.MessageInboxService;
import com.wensaas.core.api.ops.TenantOpsService;
import com.wensaas.core.api.tenant.selfservice.TenantAccountService;
import com.wensaas.core.api.tenant.selfservice.TenantCompanyService;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.selfservice.TenantProfileService;
import com.wensaas.core.api.tenant.selfservice.TenantWatermarkService;
import com.wensaas.core.api.tenant.selfservice.TenantWorkbenchService;
import com.wensaas.core.api.tenant.audit.TenantOperationLogQueryService;
import com.wensaas.core.api.ticket.TicketService;
import com.wensaas.core.api.verification.VerificationCodeService;
import com.wensaas.core.impl.audit.log.AuditChangeRenderer;
import com.wensaas.core.impl.audit.log.SysDataChangeLogMapper;
import com.wensaas.core.impl.audit.log.SysOperationLogMapper;
import com.wensaas.core.impl.auth.user.UserCommandService;
import com.wensaas.core.impl.tenant.audit.TenantOperationLogQueryServiceImpl;
import com.wensaas.core.impl.spi.registry.DashboardCardRegistry;
import com.wensaas.core.impl.auth.AuthProperties;
import com.wensaas.core.impl.auth.mfa.MfaService;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.auth.password.SecurityPolicySettings;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.ops.SysTenantSeatAddonMapper;
import com.wensaas.core.impl.pkg.SysPackageMapper;
import com.wensaas.core.impl.pkg.SysPackageModuleMapper;
import com.wensaas.core.impl.pkg.TenantPlansResolver;
import com.wensaas.core.impl.spi.module.SysBusinessModuleMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.wensaas.core.impl.tenant.selfservice.TenantAccountServiceImpl;
import com.wensaas.core.impl.tenant.selfservice.TenantCompanyServiceImpl;
import com.wensaas.core.impl.tenant.selfservice.TenantProfileServiceImpl;
import com.wensaas.core.impl.tenant.selfservice.TenantWatermarkServiceImpl;
import com.wensaas.core.impl.tenant.selfservice.TenantWorkbenchServiceImpl;

/**
 * 租户自助（系统管理 · 公司信息 / 套餐与账号）Bean 装配。
 *
 * <p>存储设置复用既有 {@code StorageConfigManager}（FileConfiguration 已装配），
 * 此处不重复声明。
 */
@Configuration(proxyBeanMethods = false)
public class TenantSelfConfiguration {

    @Bean
    public TenantQueryService tenantQueryService(SysTenantMapper tenantMapper) {
        return new TenantQueryServiceImpl(tenantMapper);
    }

    @Bean
    public TenantLifecycleService tenantLifecycleService(SysTenantMapper tenantMapper,
                                                         com.wensaas.core.impl.pkg.TenantCacheEvictor tenantCacheEvictor) {
        return new TenantLifecycleService(tenantMapper, tenantCacheEvictor);
    }

    @Bean
    public TenantCompanyService tenantCompanyService(SysTenantMapper tenantMapper,
                                                     FileService fileService,
                                                     SysTenantRouteAliasMapper routeAliasMapper) {
        return new TenantCompanyServiceImpl(tenantMapper, fileService, routeAliasMapper);
    }

    @Bean
    public TenantWatermarkService tenantWatermarkService(SysTenantMapper tenantMapper) {
        return new TenantWatermarkServiceImpl(tenantMapper);
    }

    /**
     * 租户操作审计日志查询（UX3）。复用审计渲染器 {@link AuditChangeRenderer}（由 OpsTeamConfiguration
     * 装配）拼 ③ 数据变更；租户隔离交给租户线拦截器。
     */
    @Bean
    public TenantOperationLogQueryService tenantOperationLogQueryService(
            SysOperationLogMapper opLogMapper,
            SysDataChangeLogMapper dataChangeLogMapper,
            AuditChangeRenderer auditChangeRenderer) {
        return new TenantOperationLogQueryServiceImpl(opLogMapper, dataChangeLogMapper, auditChangeRenderer);
    }

    @Bean
    public TenantAccountService tenantAccountService(
            SysTenantMapper tenantMapper,
            SysTenantSeatAddonMapper seatAddonMapper,
            com.wensaas.core.api.auth.user.MemberCountQuery memberCount,
            SysPackageMapper packageMapper,
            SysPackageModuleMapper packageModuleMapper,
            SysBusinessModuleMapper businessModuleMapper,
            TenantPlansResolver plansResolver,
            TenantOpsService tenantOpsService,
            @Value("${wen-saas-boot.support.phone:400-000-0000}") String supportPhone,
            @Value("${wen-saas-boot.support.email:support@example.com}") String supportEmail) {
        return new TenantAccountServiceImpl(tenantMapper, seatAddonMapper, memberCount,
                packageMapper, packageModuleMapper, businessModuleMapper, plansResolver,
                tenantOpsService, supportPhone, supportEmail);
    }

    @Bean
    public TenantProfileService tenantProfileService(
            SysUserMapper userMapper,
            UserCommandService userCommandService,
            UserRoleQueryService userRoleQuery,
            DeptQueryService deptQuery,
            SessionStore sessionStore,
            PasswordHasher passwordHasher,
            FileService fileService,
            AuthProperties authProperties,
            SecurityPolicySettings securityPolicy,
            ObjectProvider<MfaService> mfaServiceProvider,
            ObjectProvider<VerificationCodeService> verificationCodeServiceProvider,
            StringRedisTemplate redis,
            com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore roleDefaultScopeStore,
            com.wensaas.core.impl.security.SensitiveFieldCipher sensitiveFieldCipher) {
        return new TenantProfileServiceImpl(userMapper, userCommandService, userRoleQuery, deptQuery,
                sessionStore, passwordHasher, fileService, authProperties, securityPolicy,
                mfaServiceProvider, verificationCodeServiceProvider, redis, roleDefaultScopeStore,
                sensitiveFieldCipher);
    }

    @Bean
    public TenantWorkbenchService tenantWorkbenchService(
            UserQueryService userQuery,
            UserRoleQueryService userRoleQuery,
            SysTenantMapper tenantMapper,
            TenantPlansResolver plansResolver,
            TicketService ticketService,
            ObjectProvider<MessageInboxService> messageInboxProvider,
            ObjectProvider<ComplianceService> complianceProvider,
            DashboardCardRegistry cardRegistry,
            org.springframework.context.ApplicationContext applicationContext) {
        return new TenantWorkbenchServiceImpl(userQuery, userRoleQuery, tenantMapper,
                plansResolver, ticketService, messageInboxProvider, complianceProvider,
                cardRegistry, applicationContext);
    }
}
