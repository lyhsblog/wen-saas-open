package com.wensaas.core.impl.security;

import com.wensaas.core.impl.auth.AuthProperties;
import com.wensaas.core.impl.auth.mfa.MfaProperties;
import com.wensaas.core.impl.file.FileProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdSecretGuardTest {

    private static FileProperties fileWithKey(String defaultKey) {
        FileProperties fp = new FileProperties();
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("default", defaultKey);
        fp.setEncryptionKeys(keys);
        return fp;
    }

    @Test
    void prodProfile_withDevDefaults_blocksStartup() {
        MockEnvironment env = new MockEnvironment().withProperty("x", "y");
        env.setActiveProfiles("prod");
        ProdSecretGuard guard = new ProdSecretGuard(env,
                new AuthProperties(),  // jwtSecret 默认 dev 值
                new MfaProperties(),   // secretKey / secretSalt 默认 dev 值
                fileWithKey(ProdSecretGuard.DEV_FILE_AES_KEY));

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEN_SAAS_BOOT_JWT_SECRET")
                .hasMessageContaining("WEN_SAAS_BOOT_MFA_KEY")
                .hasMessageContaining("WEN_SAAS_BOOT_FILE_AES_KEY");
    }

    @Test
    void prodProfile_withRealSecrets_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        AuthProperties auth = new AuthProperties();
        auth.setJwtSecret("a-real-strong-production-jwt-secret-32bytes-min!!");
        MfaProperties mfa = new MfaProperties();
        mfa.setSecretKey("real-prod-mfa-key");
        mfa.setSecretSalt("0011223344556677");
        ProdSecretGuard guard = new ProdSecretGuard(env, auth, mfa,
                fileWithKey("cmVhbC1wcm9kLWFlcy1rZXktMzJieXRlcy1sb25nLW9rIQ=="));

        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void nonProdProfile_withDevDefaults_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        ProdSecretGuard guard = new ProdSecretGuard(env,
                new AuthProperties(), new MfaProperties(),
                fileWithKey(ProdSecretGuard.DEV_FILE_AES_KEY));

        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void devDefaultConstants_matchPropertiesDefaults() {
        // 守卫的常量必须与各 *Properties 内置默认值一致，否则校验形同虚设
        assertThat(new AuthProperties().getJwtSecret()).isEqualTo(ProdSecretGuard.DEV_JWT_SECRET);
        assertThat(new MfaProperties().getSecretKey()).isEqualTo(ProdSecretGuard.DEV_MFA_KEY);
        assertThat(new MfaProperties().getSecretSalt()).isEqualTo(ProdSecretGuard.DEV_MFA_SALT);
    }
}
