package com.wensaas.core.impl.auth.login;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.user.AuthenticatedIdentity;
import com.wensaas.core.api.auth.login.AuthenticationException;
import com.wensaas.core.api.auth.login.LoginCredentials;
import com.wensaas.core.api.auth.login.LoginIdentityProvider;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;

/**
 * 账号 + 密码登录 Provider。
 *
 * <p>登录时尚未建立 TenantContext，且需跨租户匹配 (tenant_id, username)，因此整个类加
 * {@link CrossTenantQuery} 让 MP TenantLine 跳过；查询条件里手工带 tenant_id 决定走运营账号
 * （tenant_id=0）还是指定租户。
 *
 * <p>认证成功后由 {@link IdentityResolver} 组装 {@link AuthenticatedIdentity}（含 tenantOwner /
 * mainDeptId，与 OAuth 登录共用）。
 */
@CrossTenantQuery
public class PasswordLoginIdentityProvider
        implements LoginIdentityProvider<LoginCredentials.PasswordCredentials> {

    private final SysUserMapper userMapper;
    private final TenantQueryService tenantQuery;
    private final PasswordHasher passwordHasher;
    private final LoginAttemptGuard attemptGuard;
    private final IdentityResolver identityResolver;

    public PasswordLoginIdentityProvider(SysUserMapper userMapper,
                                          TenantQueryService tenantQuery,
                                          PasswordHasher passwordHasher,
                                          LoginAttemptGuard attemptGuard,
                                          IdentityResolver identityResolver) {
        this.userMapper = userMapper;
        this.tenantQuery = tenantQuery;
        this.passwordHasher = passwordHasher;
        this.attemptGuard = attemptGuard;
        this.identityResolver = identityResolver;
    }

    @Override
    public Class<LoginCredentials.PasswordCredentials> supports() {
        return LoginCredentials.PasswordCredentials.class;
    }

    @Override
    public AuthenticatedIdentity authenticate(LoginCredentials.PasswordCredentials c) {
        if (c.username() == null || c.username().isBlank()
                || c.password() == null || c.password().isEmpty()) {
            throw new AuthenticationException(AuthenticationException.Code.BAD_CREDENTIALS,
                    "用户名或密码不能为空");
        }

        // tenantCode 非空 → 反查 sys_tenant.id 后按该租户匹配；为空 → 运营账号 (tenant_id=0)。
        // 多租户重名账号同租户内仍由 (tenant_id, username) 唯一索引保证。
        long tenantId = TenantContext.PLATFORM_TENANT_ID;
        if (c.tenantCode() != null && !c.tenantCode().isBlank()) {
            TenantInfo tenant = tenantQuery.tenantByCode(c.tenantCode().trim());
            if (tenant == null || tenant.status() == null || tenant.status() != 1) {
                throw new AuthenticationException(AuthenticationException.Code.BAD_CREDENTIALS,
                        "用户名或密码错误");
            }
            tenantId = tenant.id();
        }

        // 锁定检查：在查用户之前执行，已锁定直接拒绝（避免 DB 查询浪费）。
        attemptGuard.check(tenantId, c.username());

        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, c.username());
        SysUser user = userMapper.selectOne(q);
        if (user == null) {
            // 用户不存在：不计入失败次数（防止枚举账号），抛内部码 USER_NOT_FOUND（advice 同样 401）。
            throw new AuthenticationException(AuthenticationException.Code.USER_NOT_FOUND,
                    "用户名或密码错误");
        }
        if (!passwordHasher.verify(c.password(), user.getPasswordHash())) {
            Integer left = attemptGuard.recordFailure(tenantId, c.username());
            throw new AuthenticationException(AuthenticationException.Code.BAD_CREDENTIALS,
                    "用户名或密码错误", left);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new AuthenticationException(AuthenticationException.Code.DISABLED, "账号已停用");
        }

        // 登录成功，清除失败计数。
        attemptGuard.clearFailure(tenantId, c.username());
        return identityResolver.of(user);
    }
}
