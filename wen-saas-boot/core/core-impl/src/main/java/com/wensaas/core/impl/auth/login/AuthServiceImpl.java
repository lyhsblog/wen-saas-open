package com.wensaas.core.impl.auth.login;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.audit.LoginAuditEvent;
import com.wensaas.core.api.auth.user.AuthenticatedIdentity;
import com.wensaas.core.api.auth.login.AuthenticationException;
import com.wensaas.core.api.auth.login.LoginCredentials;
import com.wensaas.core.api.auth.login.LoginIdentityProvider;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.auth.oauth.OAuthBindConflictException;
import com.wensaas.core.api.auth.oauth.OAuthBindingRequiredException;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.mfa.MfaService;
import com.wensaas.core.impl.auth.oauth.OAuthBindPreAuth;
import com.wensaas.core.impl.auth.oauth.OAuthBindPreAuthStore;
import com.wensaas.core.impl.auth.oauth.SysUserOAuthBinding;
import com.wensaas.core.impl.auth.oauth.SysUserOAuthBindingMapper;
import com.wensaas.core.impl.auth.session.JwtTokenProvider;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.UserCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AuthService 默认实现。
 *
 * <p>装配若干 {@link LoginIdentityProvider}，按 {@code supports()} 类型分发；
 * 认证成功后创建 Session、签发 JWT、刷新 {@code last_login_at}。
 *
 * <p>{@link #login} 方法标 {@link CrossTenantQuery}：登录流程在 TenantContext 未建立前查 sys_user / 更新
 * last_login_at，需要 MP TenantLine 全程跳过。注解放方法上而非类上，是因为 Spring AOP 的
 * {@code @within} 在 JDK 动态代理 + 接口实现的组合下识别可能不稳定；{@code @annotation} 更直接可靠。
 */
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final Map<Class<? extends LoginCredentials>, LoginIdentityProvider<?>> providers;
    private final SessionStore sessionStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserCommandService userCommandService;
    private final AuditLogRecorder auditLogRecorder;
    private final ObjectProvider<MfaService> mfaServiceProvider;
    private final OAuthBindPreAuthStore oauthBindStore;
    private final PasswordLoginIdentityProvider passwordProvider;
    private final SysUserOAuthBindingMapper oauthBindingMapper;
    private final boolean opsMfaEnforced;

    public AuthServiceImpl(List<LoginIdentityProvider<?>> providers,
                           SessionStore sessionStore,
                           JwtTokenProvider jwtTokenProvider,
                           UserCommandService userCommandService,
                           AuditLogRecorder auditLogRecorder,
                           ObjectProvider<MfaService> mfaServiceProvider,
                           OAuthBindPreAuthStore oauthBindStore,
                           PasswordLoginIdentityProvider passwordProvider,
                           SysUserOAuthBindingMapper oauthBindingMapper,
                           boolean opsMfaEnforced) {
        this.providers = new HashMap<>();
        for (LoginIdentityProvider<?> p : providers) {
            this.providers.put(p.supports(), p);
        }
        this.sessionStore = sessionStore;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userCommandService = userCommandService;
        this.auditLogRecorder = auditLogRecorder;
        this.mfaServiceProvider = mfaServiceProvider;
        this.oauthBindStore = oauthBindStore;
        this.passwordProvider = passwordProvider;
        this.oauthBindingMapper = oauthBindingMapper;
        this.opsMfaEnforced = opsMfaEnforced;
    }

    @Override
    @CrossTenantQuery
    @SuppressWarnings({"unchecked", "rawtypes"})
    public LoginResult login(LoginCredentials credentials) {
        String account = accountOf(credentials);
        String method = methodOf(credentials);
        try {
            if (credentials == null) {
                throw new AuthenticationException(AuthenticationException.Code.BAD_CREDENTIALS,
                        "缺少登录凭证");
            }
            LoginIdentityProvider provider = providers.get(credentials.getClass());
            if (provider == null) {
                throw new AuthenticationException(AuthenticationException.Code.BAD_CREDENTIALS,
                        "不支持的登录方式：" + credentials.getClass().getSimpleName());
            }
            AuthenticatedIdentity identity = (AuthenticatedIdentity) provider.authenticate(credentials);

            // 二步验证：密码通过但会话未授予，返回预认证票据；真正的会话 token 在
            // /api/auth/mfa/verify 通过后由 MfaService 签发（含 login success 审计）。
            //  - 两端均看用户的「登录二步验证」开关（个人中心自助开/关）。
            //  - 运营账号（tenant_id=0）另受 auth.ops-mfa-enforced 配置：采用方设 true 时强制
            //    （未开启的账号在 verify 前强制 setup），默认 false 即可选。
            MfaService mfa = mfaServiceProvider.getIfAvailable();
            boolean opsAccount = identity.tenantId() == TenantContext.PLATFORM_TENANT_ID;
            if (mfa != null
                    && ((opsAccount && opsMfaEnforced) || mfa.status(identity.userId()).loginMfaEnabled())) {
                MfaService.BeginResult begin = mfa.beginLoginChallenge(identity, account, method);
                log.info("login.mfa_required userId={} enabled={}",
                        identity.userId(), begin.enabled());
                return LoginResult.mfaPending(begin.enabled(), begin.method(), begin.preAuthToken());
            }

            String sessionId = sessionStore.createSession(identity);
            String token = jwtTokenProvider.issue(sessionId);
            userCommandService.touchLastLogin(identity.userId());

            UserPrincipal principal = new UserPrincipal(
                    identity.userId(), identity.tenantId(), identity.username(), sessionId,
                    identity.tenantOwner(), identity.mainDeptId(), false, null, identity.realName());
            log.info("login.success userId={} tenantId={} username={}",
                    identity.userId(), identity.tenantId(), identity.username());
            recordLoginSuccess(identity, account, method);
            return LoginResult.completed(token, jwtTokenProvider.tokenTtl(), principal);
        } catch (OAuthBindingRequiredException ex) {
            // OAuth 授权成功但未绑定平台账号：暂存渠道身份，返回 bindToken 引导绑定（非失败，不记日志）。
            String bindToken = oauthBindStore.create(new OAuthBindPreAuth(
                    ex.getProvider(), ex.getExternalId(), ex.getUnionId(),
                    ex.getNickname(), ex.getAvatarUrl(), Instant.now()));
            log.info("login.oauth_binding_required provider={}", ex.getProvider());
            return LoginResult.bindingPending(bindToken,
                    new OAuthProfile(ex.getProvider(), ex.getNickname(), ex.getAvatarUrl()));
        } catch (AuthenticationException ex) {
            recordLoginFailure(account, method, ex);
            throw ex;
        }
    }

    @Override
    @CrossTenantQuery
    public LoginResult completeOAuthBinding(String bindToken, LoginCredentials.PasswordCredentials creds) {
        OAuthBindPreAuth pre = oauthBindStore.find(bindToken)
                .orElseThrow(() -> new AuthenticationException(AuthenticationException.Code.BAD_CREDENTIALS,
                        "绑定会话已过期，请重新扫码"));
        String account = creds == null || creds.username() == null ? "" : creds.username();
        String method = "oauth_" + pre.provider();
        try {
            // 1. 校验账密（复用密码 Provider，含锁定 / 停用等校验）。
            AuthenticatedIdentity identity = passwordProvider.authenticate(creds);
            // 2. 运营账号不支持第三方绑定。
            if (identity.tenantId() == TenantContext.PLATFORM_TENANT_ID) {
                throw new AuthenticationException(AuthenticationException.Code.BAD_CREDENTIALS,
                        "运营账号不支持第三方账号绑定");
            }
            // 3. 写绑定关系（登录态无 TenantContext，手填 tenant_id 并绕过 tenant line）。
            insertBinding(identity, pre);
            oauthBindStore.consume(bindToken);
            // 4. 签发会话（与 login 成功路径同构）。
            String sessionId = sessionStore.createSession(identity);
            String token = jwtTokenProvider.issue(sessionId);
            userCommandService.touchLastLogin(identity.userId());
            UserPrincipal principal = new UserPrincipal(
                    identity.userId(), identity.tenantId(), identity.username(), sessionId,
                    identity.tenantOwner(), identity.mainDeptId(), false, null, identity.realName());
            log.info("oauth.bind_success userId={} tenantId={} provider={}",
                    identity.userId(), identity.tenantId(), pre.provider());
            recordLoginSuccess(identity, account, method);
            return LoginResult.completed(token, jwtTokenProvider.tokenTtl(), principal);
        } catch (AuthenticationException ex) {
            recordLoginFailure(account, method, ex);
            throw ex;
        }
    }

    @Override
    public void logout(String sessionId) {
        sessionStore.invalidate(sessionId);
        log.info("logout sessionId={}", sessionId);
        recordLogout(sessionId);
    }

    /** 插入绑定行。唯一冲突（渠道账号已绑他人 / 本账号已绑同渠道）由 DB 唯一索引兜底转 409。 */
    private void insertBinding(AuthenticatedIdentity identity, OAuthBindPreAuth pre) {
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        try {
            SysUserOAuthBinding b = new SysUserOAuthBinding();
            b.setTenantId(identity.tenantId());
            b.setUserId(identity.userId());
            b.setProvider(pre.provider());
            b.setExternalId(pre.externalId());
            b.setUnionId(pre.unionId());
            b.setNickname(pre.nickname());
            b.setAvatarUrl(pre.avatarUrl());
            LocalDateTime now = LocalDateTime.now();
            b.setBoundAt(now);
            b.setCreatedAt(now);
            b.setUpdatedAt(now);
            b.setCreatedBy(identity.userId());
            b.setUpdatedBy(identity.userId());
            oauthBindingMapper.insert(b);
        } catch (DuplicateKeyException e) {
            throw new OAuthBindConflictException("该第三方账号已绑定其他平台账号", e);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    private void recordLoginSuccess(AuthenticatedIdentity identity, String account, String method) {
        auditLogRecorder.recordLogin(new LoginAuditEvent(
                null,
                identity.tenantId(),
                requestId(),
                identity.userId(),
                identity.username(),
                identity.mainDeptId(),
                account,
                method,
                "login",
                "success",
                null,
                null, null,
                Instant.now()));
    }

    private void recordLoginFailure(String account, String method, AuthenticationException ex) {
        auditLogRecorder.recordLogin(new LoginAuditEvent(
                null,
                TenantContext.PLATFORM_TENANT_ID,
                requestId(),
                null,
                null,
                null,
                account,
                method,
                "login",
                "fail",
                failReasonOf(ex, method),
                null, null,
                Instant.now()));
    }

    private void recordLogout(String sessionId) {
        // 调用时 UserContext 一般仍可用（filter 内 chain.doFilter 之前清理）；
        // 为了健壮，sessionId 不在 LoginAuditEvent record 中，仅靠 requestId 关联。
        auditLogRecorder.recordLogin(new LoginAuditEvent(
                null,
                TenantContext.PLATFORM_TENANT_ID,
                requestId(),
                null,
                null,
                null,
                sessionId == null ? "" : sessionId,
                "session",
                "logout",
                "success",
                null,
                null, null,
                Instant.now()));
    }

    private static String accountOf(LoginCredentials c) {
        if (c instanceof LoginCredentials.PasswordCredentials p) {
            return p.username() == null ? "" : p.username();
        }
        return "";
    }

    private static String methodOf(LoginCredentials c) {
        if (c instanceof LoginCredentials.PasswordCredentials) {
            return "password";
        }
        if (c instanceof LoginCredentials.OAuthCredentials o) {
            return "oauth_" + (o.provider() == null || o.provider().isBlank() ? "unknown" : o.provider());
        }
        return c == null ? "unknown" : c.getClass().getSimpleName();
    }

    /** OAuth 流程（method 以 oauth_ 开头）的凭证类失败落 {@code oauth_failed}，其余沿用通用映射。 */
    private static String failReasonOf(AuthenticationException ex, String method) {
        if (method != null && method.startsWith("oauth_")
                && ex.getCode() == AuthenticationException.Code.BAD_CREDENTIALS) {
            return "oauth_failed";
        }
        return failReasonOf(ex);
    }

    private static String failReasonOf(AuthenticationException ex) {
        return switch (ex.getCode()) {
            case BAD_CREDENTIALS, USER_NOT_FOUND, MISSING_TOKEN, INVALID_TOKEN, EXPIRED, NO_SESSION -> "bad_credentials";
            case DISABLED -> "disabled";
            case LOGIN_LOCKED -> "login_locked";
        };
    }

    private static String requestId() {
        String rid = RequestContext.get();
        return rid != null && !rid.isBlank() ? rid : "system";
    }
}
