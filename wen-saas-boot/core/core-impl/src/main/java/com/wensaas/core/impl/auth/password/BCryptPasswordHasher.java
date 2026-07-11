package com.wensaas.core.impl.auth.password;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码哈希实现，基于 {@link BCryptPasswordEncoder}（spring-security-crypto）。
 *
 * <p>选型理由：spring-security-crypto 由 Spring 团队维护、
 * 仅依赖 spring-core，体积约 200KB；未来加 Argon2 / SCrypt 直接换成对应 PasswordEncoder 即可。
 */
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(CharSequence rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword 不能为空");
        }
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean verify(CharSequence rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        try {
            return encoder.matches(rawPassword, storedHash);
        } catch (IllegalArgumentException e) {
            // 存储的 hash 格式非法（异常迁移数据），按校验失败处理
            return false;
        }
    }
}
