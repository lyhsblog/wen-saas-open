package com.wensaas.core.impl.auth;

import com.wensaas.core.impl.auth.mfa.MfaSecretCipher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 仅覆盖 AES 加解密往返 + 篡改检测。dev/prod 切换的口令注入由 Spring 装配，集成测试覆盖。
 */
class MfaSecretCipherTest {

    private final MfaSecretCipher cipher = new MfaSecretCipher(
            "test-password-32-bytes-padding!!", "5c0744940b5c369b");

    @Test
    void encryptDecrypt_roundTrip() {
        String plain = "JBSWY3DPEHPK3PXP";
        String cipherText = cipher.encrypt(plain);
        assertThat(cipherText).isNotEqualTo(plain);
        assertThat(cipher.decrypt(cipherText)).isEqualTo(plain);
    }

    @Test
    void encrypt_differentCallsProduceDifferentCipherText() {
        String plain = "JBSWY3DPEHPK3PXP";
        // Encryptors.delux 使用随机 IV，每次密文不同但都能解回原文
        assertThat(cipher.encrypt(plain)).isNotEqualTo(cipher.encrypt(plain));
    }

    @Test
    void decrypt_tamperedCipherText_throws() {
        String cipherText = cipher.encrypt("secret");
        String tampered = cipherText.substring(0, cipherText.length() - 2)
                + (cipherText.endsWith("00") ? "11" : "00");
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(RuntimeException.class);
    }
}
