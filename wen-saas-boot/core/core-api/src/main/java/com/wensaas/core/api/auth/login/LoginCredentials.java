package com.wensaas.core.api.auth.login;

import com.wensaas.core.api.auth.oauth.OAuthClient;
/**
 * 登录凭证 sealed 体系。当前支持账号 + 密码、第三方 OAuth；短信验证码留待后续加 permits 子类型。
 */
public sealed interface LoginCredentials
        permits LoginCredentials.PasswordCredentials, LoginCredentials.OAuthCredentials {

    /**
     * 账号 + 密码登录凭证。
     *
     * @param username   登录账号
     * @param password   明文密码（仅在内存中流转，比对后立即释放）
     * @param tenantCode 可选：明确指定租户编码；为 null/空则查 (tenant_id=0, username)
     */
    record PasswordCredentials(String username, String password, String tenantCode)
            implements LoginCredentials {
    }

    /**
     * 第三方 OAuth 登录凭证。{@code authCode} 由前端从渠道回调拿到，后端用
     * {@code OAuthClient} 换取 externalId；命中绑定直登，未命中进入绑定流。
     *
     * @param provider 渠道：dingtalk（后续 wechat / wecom）
     * @param authCode 渠道授权码（一次性）
     * @param state    防 CSRF 的回传态（可空，由调用方校验）
     */
    record OAuthCredentials(String provider, String authCode, String state)
            implements LoginCredentials {
    }
}
