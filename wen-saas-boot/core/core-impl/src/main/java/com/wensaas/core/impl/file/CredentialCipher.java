package com.wensaas.core.impl.file;

import com.wensaas.core.api.file.FileException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AES-256-GCM 凭证加解密。
 *
 * <p>多版本密钥支持运维平滑轮转：
 * <ul>
 *   <li>密钥来自 {@link FileProperties#resolveKeys()}（每个 value 必须 Base64 32 字节）</li>
 *   <li>加密使用 {@link FileProperties#resolveActiveKeyId()} 指定的 keyId</li>
 *   <li>密文格式 {@code <keyId>:<Base64(IV[12] || ciphertext || tag[16])>}</li>
 *   <li>解密按密文前缀的 keyId 路由</li>
 * </ul>
 *
 * <p>解密失败（密钥变更 / 未知 keyId / 缺少 keyId 前缀 / 篡改 / 损坏）抛
 * {@link FileException} code = {@link FileException.Code#CREDENTIAL_DECRYPT_ERROR}。
 */
public class CredentialCipher {

    private static final Logger log = LoggerFactory.getLogger(CredentialCipher.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final char KEY_ID_SEPARATOR = ':';

    private final SecureRandom random = new SecureRandom();
    private final Map<String, SecretKeySpec> keys;
    private final String activeKeyId;

    public CredentialCipher(FileProperties props) {
        Map<String, String> raw = props.resolveKeys();
        if (raw.isEmpty()) {
            throw new IllegalStateException(
                    "wen-saas-boot.file.encryption-keys 未配置；prod 必须由 env 注入");
        }
        Map<String, SecretKeySpec> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String keyId = entry.getKey();
            if (keyId == null || keyId.isBlank()) {
                throw new IllegalStateException("encryption-keys 中存在空 keyId");
            }
            if (keyId.indexOf(KEY_ID_SEPARATOR) >= 0) {
                throw new IllegalStateException("keyId 不允许包含 ':' 字符：" + keyId);
            }
            parsed.put(keyId, decodeKey(keyId, entry.getValue()));
        }
        String active = props.resolveActiveKeyId();
        if (!parsed.containsKey(active)) {
            throw new IllegalStateException(
                    "wen-saas-boot.file.active-key-id=" + active + " 不在 encryption-keys 集合中："
                            + parsed.keySet());
        }
        this.keys = Map.copyOf(parsed);
        this.activeKeyId = active;
    }

    private SecretKeySpec decodeKey(String keyId, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("encryption-keys[" + keyId + "] 为空");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "encryption-keys[" + keyId + "] 不是合法 Base64", ex);
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "encryption-keys[" + keyId + "] 解码后必须为 " + KEY_LENGTH_BYTES
                            + " 字节，实际 " + decoded.length);
        }
        return new SecretKeySpec(decoded, ALGORITHM);
    }

    @PostConstruct
    public void init() {
        log.info("[FileCipher] AES-256-GCM ready; keyIds={}, activeKeyId={}",
                keys.keySet(), activeKeyId);
    }

    /** 当前 active keyId。供 Factory / 测试观察。 */
    public String activeKeyId() {
        return activeKeyId;
    }

    /** 加密字符串；返回 {@code <activeKeyId>:Base64(IV || ciphertext || tag)}。null 输入直接返回 null。 */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherTextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherTextWithTag.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherTextWithTag, 0, combined, iv.length, cipherTextWithTag.length);
            return activeKeyId + KEY_ID_SEPARATOR + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new FileException(FileException.Code.CREDENTIAL_DECRYPT_ERROR, "加密失败", ex);
        }
    }

    /**
     * 解密。失败抛 {@link FileException}。null 输入直接返回 null。
     *
     * <p>密文格式：{@code <keyId>:<base64>}。
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        int sep = ciphertext.indexOf(KEY_ID_SEPARATOR);
        if (sep < 0) {
            throw new FileException(FileException.Code.CREDENTIAL_DECRYPT_ERROR,
                    "密文缺少 keyId 前缀");
        }
        String keyId = ciphertext.substring(0, sep);
        String body = ciphertext.substring(sep + 1);
        SecretKeySpec key = keys.get(keyId);
        if (key == null) {
            throw new FileException(FileException.Code.CREDENTIAL_DECRYPT_ERROR,
                    "未知 keyId=" + keyId + "（密文使用的密钥未在 encryption-keys 中配置）");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(body);
            if (combined.length < IV_LENGTH + 16) {
                throw new FileException(FileException.Code.CREDENTIAL_DECRYPT_ERROR,
                        "密文长度异常: " + combined.length);
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherTextWithTag = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherTextWithTag, 0, cipherTextWithTag.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(cipherTextWithTag);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (FileException fe) {
            throw fe;
        } catch (Exception ex) {
            throw new FileException(FileException.Code.CREDENTIAL_DECRYPT_ERROR,
                    "解密失败（密钥变更 / 密文损坏 / 篡改）keyId=" + keyId, ex);
        }
    }
}
