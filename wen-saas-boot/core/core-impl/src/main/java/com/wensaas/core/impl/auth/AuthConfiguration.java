package com.wensaas.core.impl.auth;
import com.wensaas.core.api.tenant.TenantQueryService;

import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.auth.login.LoginIdentityProvider;
import com.wensaas.core.api.auth.user.UserQueryService;
import com.wensaas.core.api.auth.oauth.OAuthBindingService;
import com.wensaas.core.api.auth.oauth.OAuthClient;
import com.wensaas.core.api.verification.VerificationCodeService;
import com.wensaas.core.impl.auth.filter.AuthPathMatcher;
import com.wensaas.core.impl.auth.filter.AuthenticationFilter;
import com.wensaas.core.impl.auth.filter.PublicEndpointRateLimitFilter;
import com.wensaas.core.impl.auth.filter.PublicRateLimitProperties;
import com.wensaas.core.impl.auth.impersonate.ImpersonateNonceStore;
import com.wensaas.core.impl.auth.impersonate.ImpersonateService;
import com.wensaas.core.impl.auth.impersonate.ImpersonateServiceImpl;
import com.wensaas.core.impl.auth.impersonate.RedisImpersonateNonceStore;
import com.wensaas.core.impl.auth.login.*;
import com.wensaas.core.impl.auth.mfa.*;
import com.wensaas.core.impl.auth.stepup.*;
import com.wensaas.core.impl.auth.oauth.*;
import com.wensaas.core.api.auth.org.DeptQueryService;
import com.wensaas.core.impl.auth.password.*;
import com.wensaas.core.impl.auth.session.JwtTokenProvider;
import com.wensaas.core.impl.auth.session.RedisSessionStore;
import com.wensaas.core.impl.auth.session.RefreshTokenService;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.user.UserQueryServiceImpl;
import com.wensaas.core.impl.auth.user.UserCommandService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

// ImpersonateService 所需依赖由同模块其他 Bean 提供，直接在此统一装配。

/**
 * 认证与会话装配：密码哈希、JWT、会话存储、登录 / 代登录 / MFA / OAuth 等 Bean 在此统一注册。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AuthProperties.class, MfaProperties.class, DingtalkOAuthProperties.class,
        PublicRateLimitProperties.class})
public class AuthConfiguration {

    @Bean
    public PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    /**
     * 安全策略参数门面。{@code OpsSettingService} 惰性可选注入：
     * 纯 auth 上下文（无 ops 装配）回退 {@link AuthProperties} 默认值。
     */
    @Bean
    public SecurityPolicySettings securityPolicySettings(
            ObjectProvider<com.wensaas.core.api.ops.setting.OpsSettingService> settingProvider,
            AuthProperties props) {
        return new SecurityPolicySettings(settingProvider, props);
    }

    @Bean
    public JwtTokenProvider jwtTokenProvider(AuthProperties props, SecurityPolicySettings security) {
        return new JwtTokenProvider(props, security);
    }

    @Bean
    public SessionStore sessionStore(StringRedisTemplate redis, AuthProperties props, SecurityPolicySettings security) {
        return new RedisSessionStore(redis, props, security);
    }

    @Bean
    public RefreshTokenService refreshTokenService(StringRedisTemplate redis, AuthProperties props,
                                                   SecurityPolicySettings security, SessionStore sessionStore) {
        return new RefreshTokenService(redis, props, security, sessionStore);
    }

    @Bean
    public LoginAttemptGuard loginAttemptGuard(StringRedisTemplate redis, SecurityPolicySettings security) {
        return new LoginAttemptGuard(redis, security);
    }

    /** 已认证身份组装（tenantOwner / mainDeptId）：账密与 OAuth 登录共用。 */
    @Bean
    public IdentityResolver identityResolver(TenantQueryService tenantQuery, DeptQueryService deptQuery) {
        return new IdentityResolver(tenantQuery, deptQuery);
    }

    @Bean
    public PasswordLoginIdentityProvider passwordLoginIdentityProvider(
            SysUserMapper userMapper, TenantQueryService tenantQuery,
            PasswordHasher passwordHasher, LoginAttemptGuard loginAttemptGuard,
            IdentityResolver identityResolver) {
        return new PasswordLoginIdentityProvider(userMapper, tenantQuery, passwordHasher,
                loginAttemptGuard, identityResolver);
    }

    // 钉钉 OAuth 客户端：mode 缺省即 mock（无真实凭据时本地联调用）；
    // 配 wen-saas-boot.oauth.dingtalk.mode=real 互斥切换到 RealDingtalkOAuthClient。
    @Bean
    @ConditionalOnProperty(prefix = "wen-saas-boot.oauth.dingtalk", name = "mode",
            havingValue = "mock", matchIfMissing = true)
    public OAuthClient mockDingtalkOAuthClient() {
        return new MockDingtalkOAuthClient();
    }

    @Bean
    @ConditionalOnProperty(prefix = "wen-saas-boot.oauth.dingtalk", name = "mode", havingValue = "real")
    public OAuthClient realDingtalkOAuthClient(DingtalkOAuthProperties props) {
        return new RealDingtalkOAuthClient(props);
    }

    // 微信 / 企微 OAuth 客户端：本期仅 mock（真实凭据未定）。真实接入照钉钉补
    // Real{Wechat,Wecom}OAuthClient + 配置类，用 mode=real 互斥切换。
    @Bean
    @ConditionalOnProperty(prefix = "wen-saas-boot.oauth.wechat", name = "mode",
            havingValue = "mock", matchIfMissing = true)
    public OAuthClient mockWechatOAuthClient() {
        return new MockWechatOAuthClient();
    }

    @Bean
    @ConditionalOnProperty(prefix = "wen-saas-boot.oauth.wecom", name = "mode",
            havingValue = "mock", matchIfMissing = true)
    public OAuthClient mockWecomOAuthClient() {
        return new MockWecomOAuthClient();
    }

    @Bean
    public OAuthBindPreAuthStore oauthBindPreAuthStore(StringRedisTemplate redis) {
        return new OAuthBindPreAuthStore(redis);
    }

    @Bean
    public OAuthStateStore oauthStateStore(StringRedisTemplate redis) {
        return new OAuthStateStore(redis);
    }

    @Bean
    public OAuthLoginIdentityProvider oauthLoginIdentityProvider(
            List<OAuthClient> oauthClients, SysUserOAuthBindingMapper oauthBindingMapper,
            SysUserMapper userMapper, IdentityResolver identityResolver,
            OAuthStateStore oauthStateStore) {
        return new OAuthLoginIdentityProvider(oauthClients, oauthBindingMapper, userMapper,
                identityResolver, oauthStateStore);
    }

    /** 租户成员自助第三方账号绑定（列表 / 绑定 / 解绑）。 */
    @Bean
    public OAuthBindingService oauthBindingService(List<OAuthClient> oauthClients,
                                                   SysUserOAuthBindingMapper oauthBindingMapper) {
        return new OAuthBindingServiceImpl(oauthClients, oauthBindingMapper);
    }

    @Bean
    public AuthService authService(List<LoginIdentityProvider<?>> providers,
                                   SessionStore sessionStore,
                                   JwtTokenProvider jwtTokenProvider,
                                   UserCommandService userCommandService,
                                   AuditLogRecorder auditLogRecorder,
                                   ObjectProvider<MfaService> mfaServiceProvider,
                                   OAuthBindPreAuthStore oauthBindPreAuthStore,
                                   PasswordLoginIdentityProvider passwordLoginIdentityProvider,
                                   SysUserOAuthBindingMapper oauthBindingMapper,
                                   AuthProperties props) {
        return new AuthServiceImpl(providers, sessionStore, jwtTokenProvider, userCommandService,
                auditLogRecorder, mfaServiceProvider, oauthBindPreAuthStore,
                passwordLoginIdentityProvider, oauthBindingMapper, props.isOpsMfaEnforced());
    }

    /** 返回具体类型，使其实现的 4 个 ISP 读接口（UserQueryService / OpsAccountQuery /
     * UserDeliveryContactQuery / MemberExportQuery）都可按类型注入到对应消费方。 */
    @Bean
    public UserQueryServiceImpl userQueryService(SysUserMapper userMapper,
                                                 com.wensaas.core.impl.security.SensitiveFieldCipher cipher) {
        return new UserQueryServiceImpl(userMapper, cipher);
    }

    @Bean
    public UserCommandService userCommandService(SysUserMapper userMapper) {
        return new UserCommandService(userMapper);
    }

    @Bean
    public MfaSecretCipher mfaSecretCipher(MfaProperties props) {
        return new MfaSecretCipher(props.getSecretKey(), props.getSecretSalt());
    }

    @Bean
    public MfaPreAuthStore mfaPreAuthStore(StringRedisTemplate redis, MfaProperties props) {
        return new MfaPreAuthStore(redis, props.getPreAuthTtl());
    }

    // 与 VerificationConfiguration（提供 MfaService 依赖的 VerificationCodeService）同条件装配：
    // 均门控 wen-saas-boot.notification.enabled。此前用 @ConditionalOnBean(VerificationCodeService)，
    // 但 AuthConfiguration 早于 VerificationConfiguration 处理（字母序 A<V），定义时该 bean 尚未注册 →
    // 条件恒不成立 → MfaService 从不创建 → 运营账号「强制 MFA」被静默跳过（login 直发 token）。
    // 改 @ConditionalOnProperty 后由依赖图保证 VerificationCodeService 先于本 bean 实例化。
    @Bean
    @ConditionalOnProperty(prefix = "wen-saas-boot.notification", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public MfaService mfaService(SysMfaConfigMapper configMapper,
                                 SysMfaRecoveryCodeMapper recoveryMapper,
                                 SysUserMapper userMapper,
                                 UserCommandService userCommandService,
                                 MfaPreAuthStore preAuthStore,
                                 MfaSecretCipher secretCipher,
                                 VerificationCodeService verificationCodeService,
                                 SessionStore sessionStore,
                                 JwtTokenProvider jwtTokenProvider,
                                 AuditLogRecorder auditLogRecorder,
                                 StringRedisTemplate redis,
                                 MfaProperties props) {
        return new MfaServiceImpl(configMapper, recoveryMapper, userMapper, userCommandService, preAuthStore, secretCipher,
                verificationCodeService, sessionStore, jwtTokenProvider, auditLogRecorder, redis, props);
    }

    @Bean
    public StepUpTicketStore stepUpTicketStore(StringRedisTemplate redis, MfaProperties props) {
        return new StepUpTicketStore(redis, props.getStepUpTtl());
    }

    // 敏感操作 step-up：依赖 MfaService（TOTP）+ VerificationCodeService（短信/邮件），
    // 与 mfaService 同条件门控 notification.enabled。
    @Bean
    @ConditionalOnProperty(prefix = "wen-saas-boot.notification", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public StepUpService stepUpService(MfaService mfaService,
                                       VerificationCodeService verificationCodeService,
                                       SysUserMapper userMapper,
                                       StepUpTicketStore stepUpTicketStore) {
        return new StepUpServiceImpl(mfaService, verificationCodeService, userMapper, stepUpTicketStore);
    }

    @Bean
    public ImpersonateNonceStore impersonateNonceStore(StringRedisTemplate redis) {
        return new RedisImpersonateNonceStore(redis);
    }

    @Bean
    public ImpersonateService impersonateService(SessionStore sessionStore,
                                                 ImpersonateNonceStore nonceStore,
                                                 JwtTokenProvider jwtTokenProvider,
                                                 SysUserMapper userMapper,
                                                 TenantQueryService tenantQuery,
                                                 DeptQueryService deptQuery,
                                                 com.wensaas.core.api.ops.access.OpsAccessContextResolver accessResolver,
                                                 AuditLogRecorder auditLogRecorder,
                                                 ObjectProvider<com.wensaas.core.api.notification.send.MessageService>
                                                          messageServiceProvider) {
        return new ImpersonateServiceImpl(sessionStore, nonceStore, jwtTokenProvider,
                userMapper, tenantQuery, deptQuery, accessResolver, auditLogRecorder,
                messageServiceProvider.getIfAvailable());
    }

    @Bean
    public AuthPathMatcher authPathMatcher(AuthProperties props, ApplicationContext ctx) {
        return new AuthPathMatcher(props, ctx);
    }

    @Bean
    public AuthenticationFilter authenticationFilter(AuthPathMatcher pathMatcher,
                                                     JwtTokenProvider jwtTokenProvider,
                                                     SessionStore sessionStore) {
        return new AuthenticationFilter(pathMatcher, jwtTokenProvider, sessionStore);
    }

    /** 公开重置 / OAuth 端点 IP 级频控。复用统一的滑动窗口限流组件。 */
    @Bean
    public PublicEndpointRateLimitFilter publicEndpointRateLimitFilter(
            com.wensaas.core.impl.coordination.SlidingWindowRateLimiter rateLimiter,
            PublicRateLimitProperties props) {
        return new PublicEndpointRateLimitFilter(rateLimiter, props);
    }

    /** 公开密码重置编排（写路径带事务 + 窄 @CrossTenantQuery）。 */
    @Bean
    public PasswordResetPublicService passwordResetPublicService(PasswordResetTokenService resetTokenService,
                                                                 TenantQueryService tenantQuery,
                                                                 SysUserMapper userMapper,
                                                                 UserCommandService userCommandService,
                                                                 PasswordHasher passwordHasher,
                                                                 SessionStore sessionStore) {
        return new PasswordResetPublicService(resetTokenService, tenantQuery, userMapper, userCommandService, passwordHasher, sessionStore);
    }
}
