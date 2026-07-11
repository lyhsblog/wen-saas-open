package com.wensaas.core.api.auth.oauth;

/**
 * 第三方 OAuth 渠道客户端：用授权码换取渠道用户信息。每个渠道（钉钉 / 微信 / 企微）实现一个 Bean，
 * {@code OAuthLoginIdentityProvider} 按 {@link #provider()} 路由。
 *
 * <p>暂无真实开放平台凭据时由 mock 实现装配（默认）；真实实现通过 {@code @ConditionalOnProperty}
 * 互斥切换，详见 {@code AuthConfiguration}。
 */
public interface OAuthClient {

    /** 该 client 处理的渠道标识，如 {@code "dingtalk"}。 */
    String provider();

    /**
     * 用授权码换取渠道用户信息。失败必须抛 {@link OAuthExchangeException}，禁止返回 null。
     */
    OAuthUserInfo exchange(String authCode);
}
