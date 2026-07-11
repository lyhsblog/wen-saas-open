package com.wensaas.core.impl.auth.password;

import java.time.Instant;

/**
 * 密码重置令牌服务。负责签发、消费、作废 sys_password_reset_token 记录。
 */
public interface PasswordResetTokenService {

    record IssuedToken(String token, Instant expiresAt) {}

    /**
     * 签发新令牌。同时将同一 userId 已有的未使用未过期 token 标为 invalidated_at=now()。
     */
    IssuedToken issue(Long userId, Long tenantId, String tenantCode, String username, String purpose);

    /**
     * 查询令牌（不校验状态）。token 不存在时返回 null。
     */
    SysPasswordResetToken findByToken(String token);

    /**
     * 标记 token 已使用（used_at = now()）。
     */
    void markUsed(Long tokenId);
}
