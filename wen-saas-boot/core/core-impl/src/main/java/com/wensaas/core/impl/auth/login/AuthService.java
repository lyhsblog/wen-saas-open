package com.wensaas.core.impl.auth.login;

import com.wensaas.core.api.auth.login.LoginCredentials;
import com.wensaas.core.api.auth.login.MfaMethod;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.tenant.CrossTenantQuery;

import java.time.Duration;

/**
 * 认证服务门面：按 {@link LoginCredentials} 子类型路由 Provider 完成登录，并管理 Session。
 */
public interface AuthService {

    /**
     * 登录结果。三种形态：
     * <ul>
     *   <li>已完成（{@link #completed}）：携带 token / TTL / Principal，{@code mfaRequired=false}。</li>
     *   <li>待二步验证（{@link #mfaPending}）：密码通过但需二步验证（用户已开启开关，或运营账号被配置强制），token/principal 为空，
     *       携带 {@code preAuthToken}，前端走 /api/auth/mfa/* 完成后才拿到真正 token。</li>
     *   <li>待绑定（{@link #bindingPending}）：OAuth 授权成功但渠道账号未绑定平台账号，token/principal
     *       为空，携带 {@code bindToken} + 渠道资料，前端走 /api/auth/oauth/{provider}/bind 补账密完成绑定。</li>
     * </ul>
     */
    record LoginResult(String token, Duration expiresIn, UserPrincipal principal,
                       boolean mfaRequired, boolean mfaEnabled, MfaMethod mfaMethod,
                       String preAuthToken,
                       boolean bindingRequired, String bindToken, OAuthProfile bindProfile) {

        public static LoginResult completed(String token, Duration expiresIn, UserPrincipal principal) {
            return new LoginResult(token, expiresIn, principal, false, false, null, null,
                    false, null, null);
        }

        public static LoginResult mfaPending(boolean enabled, MfaMethod method, String preAuthToken) {
            return new LoginResult(null, null, null, true, enabled, method, preAuthToken,
                    false, null, null);
        }

        /** OAuth 授权成功但未绑定平台账号：返回一次性 bindToken + 渠道资料，前端引导补账密绑定。 */
        public static LoginResult bindingPending(String bindToken, OAuthProfile profile) {
            return new LoginResult(null, null, null, false, false, null, null,
                    true, bindToken, profile);
        }
    }

    /** OAuth 绑定流向前端展示的渠道资料快照。 */
    record OAuthProfile(String provider, String nickname, String avatarUrl) {
    }

    /**
     * 登录。{@link CrossTenantQuery} 注解放在接口上是因为 Spring AOP 默认通过 JDK 动态代理调用，
     * {@code @annotation} 切点查的是接口方法的注解，不是实现类方法的注解。
     */
    @CrossTenantQuery
    LoginResult login(LoginCredentials credentials);

    /**
     * 完成 OAuth 绑定并登录：校验 {@code bindToken} 暂存的渠道身份 + 账号密码，写入绑定关系后签发会话 token。
     * 账密错误抛 {@code AuthenticationException}；渠道账号已绑他人抛 {@code OAuthBindConflictException}。
     */
    @CrossTenantQuery
    LoginResult completeOAuthBinding(String bindToken, LoginCredentials.PasswordCredentials credentials);

    /**
     * 退出登录：从 SessionStore 中删除会话，使所有持有同 sessionId 的 token 立即失效。
     */
    void logout(String sessionId);
}
