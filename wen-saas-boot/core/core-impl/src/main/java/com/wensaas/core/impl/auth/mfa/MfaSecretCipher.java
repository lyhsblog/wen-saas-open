package com.wensaas.core.impl.auth.mfa;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * TOTP 密钥的可逆加密（AES-256-GCM）。密钥需可逆（校验时要拿回明文 secret 算码），故加密而非哈希。
 */
public class MfaSecretCipher {

    private final TextEncryptor encryptor;

    public MfaSecretCipher(String password, String saltHex) {
        this.encryptor = Encryptors.delux(password, saltHex);
    }

    public String encrypt(String plain) {
        return encryptor.encrypt(plain);
    }

    public String decrypt(String cipher) {
        return encryptor.decrypt(cipher);
    }
}
