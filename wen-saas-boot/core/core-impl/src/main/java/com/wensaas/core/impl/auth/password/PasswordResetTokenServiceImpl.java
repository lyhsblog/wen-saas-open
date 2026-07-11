package com.wensaas.core.impl.auth.password;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.impl.auth.AuthProperties;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * 密码重置令牌服务实现。
 * 令牌为 URL-safe Base64URL，随机 32 字节（≈43 字符），存储 64 字节 varchar 绰绰有余。
 */
public class PasswordResetTokenServiceImpl implements PasswordResetTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SysPasswordResetTokenMapper tokenMapper;
    private final AuthProperties authProperties;

    public PasswordResetTokenServiceImpl(SysPasswordResetTokenMapper tokenMapper,
                                         AuthProperties authProperties) {
        this.tokenMapper = tokenMapper;
        this.authProperties = authProperties;
    }

    @Override
    public IssuedToken issue(Long userId, Long tenantId, String tenantCode,
                             String username, String purpose) {
        // 作废同一用户已有的未使用未过期 token
        LambdaUpdateWrapper<SysPasswordResetToken> invalidate = Wrappers.<SysPasswordResetToken>lambdaUpdate()
                .eq(SysPasswordResetToken::getUserId, userId)
                .isNull(SysPasswordResetToken::getUsedAt)
                .isNull(SysPasswordResetToken::getInvalidatedAt)
                .gt(SysPasswordResetToken::getExpiresAt, Instant.now())
                .set(SysPasswordResetToken::getInvalidatedAt, Instant.now());
        tokenMapper.update(invalidate);

        String rawToken = generateToken();
        Instant expiresAt = Instant.now().plus(authProperties.getResetTokenTtl());

        SysPasswordResetToken entity = new SysPasswordResetToken();
        entity.setToken(rawToken);
        entity.setUserId(userId);
        entity.setTenantId(tenantId);
        entity.setTenantCode(tenantCode);
        entity.setUsernameMask(maskUsername(username));
        entity.setPurpose(purpose);
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(Instant.now());
        entity.setCreatedBy(0L); // system-generated; 调用方负责审计日志
        tokenMapper.insert(entity);

        return new IssuedToken(rawToken, expiresAt);
    }

    @Override
    public SysPasswordResetToken findByToken(String token) {
        return tokenMapper.selectOne(
                Wrappers.<SysPasswordResetToken>lambdaQuery()
                        .eq(SysPasswordResetToken::getToken, token));
    }

    @Override
    public void markUsed(Long tokenId) {
        LambdaUpdateWrapper<SysPasswordResetToken> update = Wrappers.<SysPasswordResetToken>lambdaUpdate()
                .eq(SysPasswordResetToken::getId, tokenId)
                .set(SysPasswordResetToken::getUsedAt, Instant.now());
        tokenMapper.update(update);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 账号掩码：长度 ≥ 5 → 首 1 + "***" + 末 2；长度 = 4 → 首 1 + "***" + 末 1；其余保留首字符。
     */
    static String maskUsername(String username) {
        if (username == null || username.length() <= 1) return username;
        if (username.length() >= 5) {
            return username.charAt(0) + "***" + username.substring(username.length() - 2);
        }
        if (username.length() == 4) {
            return username.charAt(0) + "***" + username.charAt(username.length() - 1);
        }
        // length 2 or 3
        return username.charAt(0) + "***";
    }
}
