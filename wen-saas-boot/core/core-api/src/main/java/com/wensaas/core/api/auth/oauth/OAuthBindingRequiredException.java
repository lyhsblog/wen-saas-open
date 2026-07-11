package com.wensaas.core.api.auth.oauth;

import com.wensaas.core.api.auth.login.AuthenticationException;
/**
 * OAuth 授权成功但该渠道账号尚未绑定平台账号 —— 需进入绑定流（非登录失败）。
 *
 * <p>由 {@code OAuthLoginIdentityProvider.authenticate} 抛出，{@code AuthServiceImpl.login} 单独捕获，
 * 暂存渠道身份并返回 {@code LoginResult.bindingPending}（HTTP 200 业务态，不写失败日志）。
 * 不继承 {@code AuthenticationException}：未绑定不是"已认证"也不是"认证失败"，避免污染其 Code 与 401 advice。
 */
public class OAuthBindingRequiredException extends RuntimeException {

    private final String provider;
    private final String externalId;
    private final String unionId;
    private final String nickname;
    private final String avatarUrl;

    public OAuthBindingRequiredException(String provider, String externalId, String unionId,
                                         String nickname, String avatarUrl) {
        super("第三方账号未绑定平台账号：" + provider);
        this.provider = provider;
        this.externalId = externalId;
        this.unionId = unionId;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
    }

    public String getProvider() { return provider; }
    public String getExternalId() { return externalId; }
    public String getUnionId() { return unionId; }
    public String getNickname() { return nickname; }
    public String getAvatarUrl() { return avatarUrl; }
}
