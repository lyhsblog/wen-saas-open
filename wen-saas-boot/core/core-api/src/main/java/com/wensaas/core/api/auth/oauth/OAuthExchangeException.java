package com.wensaas.core.api.auth.oauth;

import com.wensaas.core.api.auth.login.AuthenticationException;
/**
 * 授权码换取渠道用户信息失败（网络错误 / code 失效 / 渠道返回异常）。
 * 由 {@code OAuthLoginIdentityProvider} 捕获后转 {@code AuthenticationException(BAD_CREDENTIALS)}。
 */
public class OAuthExchangeException extends RuntimeException {

    public OAuthExchangeException(String message) {
        super(message);
    }

    public OAuthExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
