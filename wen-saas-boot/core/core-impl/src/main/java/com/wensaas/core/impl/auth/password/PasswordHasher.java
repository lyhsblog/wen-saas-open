package com.wensaas.core.impl.auth.password;

/**
 * 密码哈希接口。v1 实现 {@link BCryptPasswordHasher}。
 *
 * <p>预留扩展：Argon2 / 迁移期双算法兼容。
 */
public interface PasswordHasher {

    /** 对明文密码生成哈希。 */
    String hash(CharSequence rawPassword);

    /** 校验明文密码与已存哈希是否匹配。 */
    boolean verify(CharSequence rawPassword, String storedHash);
}
