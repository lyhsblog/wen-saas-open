package com.wensaas.core.api.auth.oauth;

/**
 * 绑定冲突：该渠道账号已绑定其他平台账号（或本账号同渠道已有活跃绑定）。命中 DB 唯一索引时抛出，
 * Web 层映射为 409 {@code OAUTH_ALREADY_BOUND}。不是登录失败，不写登录失败日志。
 */
public class OAuthBindConflictException extends RuntimeException {

    public OAuthBindConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
