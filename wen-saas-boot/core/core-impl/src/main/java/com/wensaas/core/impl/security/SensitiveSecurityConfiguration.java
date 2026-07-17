package com.wensaas.core.impl.security;

import com.wensaas.core.impl.auth.AuthProperties;
import com.wensaas.core.impl.auth.mfa.MfaProperties;
import com.wensaas.core.impl.file.FileProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 敏感字段加密底座装配。
 *
 * <p>{@link SensitiveFieldCipher} 复用 {@link FileProperties}（与凭证同一套多版本密钥），其
 * {@code @PostConstruct} 会把自身登记进 {@link SensitiveFieldCipherHolder}，供 MyBatis
 * {@link EncryptedStringTypeHandler} 静态取用。
 */
@Configuration(proxyBeanMethods = false)
public class SensitiveSecurityConfiguration {

    @Bean
    public SensitiveFieldCipher sensitiveFieldCipher(FileProperties props) {
        return new SensitiveFieldCipher(props);
    }

    /** 生产弱密钥兜底：prod profile 下若仍用内置 dev 密钥则阻断启动。 */
    @Bean
    public ProdSecretGuard prodSecretGuard(Environment environment,
                                           AuthProperties authProperties,
                                           MfaProperties mfaProperties,
                                           FileProperties fileProperties) {
        return new ProdSecretGuard(environment, authProperties, mfaProperties, fileProperties);
    }
}
