package com.wensaas.core.impl.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.api.ops.access.OpsAccessContextResolver;
import com.wensaas.core.api.ops.audit.OpsOperationLogQueryService;
import com.wensaas.core.api.ops.dashboard.OpsDashboardService;
import com.wensaas.core.api.ops.setting.OpsSettingService;
import com.wensaas.core.api.auth.permission.UserRoleQueryService;
import com.wensaas.core.api.ops.team.OpsTeamMemberAdminService;
import com.wensaas.core.api.ops.team.OpsTeamRoleAdminService;
import com.wensaas.core.impl.audit.log.AuditChangeRenderer;
import com.wensaas.core.impl.audit.log.SysAuditTableConfigMapper;
import com.wensaas.core.impl.audit.log.SysDataChangeLogMapper;
import com.wensaas.core.impl.audit.log.SysOperationLogMapper;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.auth.password.SecurityPolicySettings;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.UserCommandService;
import com.wensaas.core.impl.file.storage.StorageConfigManager;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.oauth.SysUserOAuthBindingMapper;
import com.wensaas.core.impl.namesource.ColumnRefRegistry;
import com.wensaas.core.impl.namesource.EntityNameResolver;
import com.wensaas.core.impl.ops.access.OpsAccessContextResolverImpl;
import com.wensaas.core.impl.ops.audit.OpsOperationLogQueryServiceImpl;
import com.wensaas.core.impl.ops.dashboard.OpsDashboardServiceImpl;
import com.wensaas.core.impl.ops.setting.OpsSettingProperties;
import com.wensaas.core.impl.ops.setting.OpsSettingServiceImpl;
import com.wensaas.core.impl.ops.setting.SysSettingMapper;
import com.wensaas.core.impl.ops.team.OpsTeamMemberAdminServiceImpl;
import com.wensaas.core.impl.ops.team.OpsTeamRoleAdminServiceImpl;
import com.wensaas.core.impl.ops.team.OpsTeamRoleSeedRunner;import com.wensaas.core.impl.permission.eval.PermissionResolver;
import com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore;
import com.wensaas.core.impl.security.SensitiveFieldCipher;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.eval.SysRolePermissionMapper;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 运营后台（工作台 + 系统管理 + 团队 + 操作日志）Bean 装配。
 *
 * <p>独立于 {@link OpsConfiguration}，避免单文件膨胀。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpsSettingProperties.class)
public class OpsTeamConfiguration {

    @Bean
    public OpsPermissionContributor opsPermissionContributor(PermissionRegistry permissionRegistry) {
        return new OpsPermissionContributor(permissionRegistry);
    }

    @Bean
    public OpsAccessContextResolver opsAccessContextResolver(PermissionResolver permissionResolver) {
        return new OpsAccessContextResolverImpl(permissionResolver);
    }

    @Bean
    public OpsSettingService opsSettingService(SysSettingMapper mapper,
                                               ObjectProvider<ObjectMapper> jackson,
                                               CacheRegionFactory cacheRegionFactory,
                                               OpsSettingProperties props) {
        return new OpsSettingServiceImpl(mapper, jackson.getIfAvailable(ObjectMapper::new),
                cacheRegionFactory, props);
    }

    @Bean
    public OpsTeamMemberAdminService opsTeamMemberAdminService(SysUserMapper userMapper,
                                                                UserCommandService userCommandService,
                                                                SysUserRoleMapper userRoleMapper,
                                                                SysRoleMapper roleMapper,
                                                                PasswordHasher passwordHasher,
                                                                SessionStore sessionStore,
                                                                SecurityPolicySettings securityPolicy,
                                                                SensitiveFieldCipher sensitiveFieldCipher,
                                                                SysUserOAuthBindingMapper oauthBindingMapper,
                                                                UserRoleQueryService userRoleQueryService) {
        return new OpsTeamMemberAdminServiceImpl(userMapper, userCommandService, userRoleMapper, roleMapper,
                passwordHasher, sessionStore, securityPolicy, sensitiveFieldCipher,
                oauthBindingMapper, userRoleQueryService);
    }

    @Bean
    public OpsTeamRoleAdminService opsTeamRoleAdminService(SysRoleMapper roleMapper,
                                                            SysRolePermissionMapper rolePermissionMapper,
                                                            SysUserRoleMapper userRoleMapper,
                                                            SysUserMapper userMapper,
                                                            RoleDefaultScopeStore roleDefaultScopeStore,
                                                            PermissionRegistry permissionRegistry,
                                                            UserRoleQueryService userRoleQueryService) {
        return new OpsTeamRoleAdminServiceImpl(roleMapper, rolePermissionMapper, userRoleMapper,
                userMapper, roleDefaultScopeStore, permissionRegistry, userRoleQueryService);
    }

    @Bean
    public AuditChangeRenderer auditChangeRenderer(SysAuditTableConfigMapper configMapper,
                                                    EntityNameResolver entityNameResolver,
                                                    ColumnRefRegistry columnRefRegistry,
                                                    ObjectProvider<tools.jackson.databind.ObjectMapper> jackson) {
        // 审计渲染走 Jackson 3（与 HTTP 响应同源）；本类其余 bean（opsSettingService）仍用 Jackson 2，故此处用全限定名
        return new AuditChangeRenderer(configMapper, entityNameResolver, columnRefRegistry,
                jackson.getIfAvailable(tools.jackson.databind.ObjectMapper::new));
    }

    @Bean
    public OpsOperationLogQueryService opsOperationLogQueryService(SysOperationLogMapper mapper,
                                                                    SysDataChangeLogMapper dataChangeLogMapper,
                                                                    AuditChangeRenderer changeRenderer,
                                                                    EntityNameResolver entityNameResolver,
                                                                    OpsAccessContextResolver accessResolver) {
        return new OpsOperationLogQueryServiceImpl(mapper, dataChangeLogMapper, changeRenderer,
                entityNameResolver, accessResolver);
    }

    @Bean
    public OpsDashboardService opsDashboardService(SysTenantMapper tenantMapper,
                                                    SysOperationLogMapper opLogMapper,
                                                    OpsAccessContextResolver accessResolver) {
        return new OpsDashboardServiceImpl(tenantMapper, opLogMapper, accessResolver);
    }

    @Bean
    public OpsTeamRoleSeedRunner opsTeamRoleSeedRunner(SysRoleMapper roleMapper,
                                                       SysRolePermissionMapper rolePermissionMapper,
                                                       PermissionRegistry permissionRegistry) {
        return new OpsTeamRoleSeedRunner(roleMapper, rolePermissionMapper, permissionRegistry);
    }
}
