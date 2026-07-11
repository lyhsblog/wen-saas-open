package com.wensaas.core.impl.security;

import com.wensaas.core.impl.file.FileProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 敏感「业务字段」确定性加解密（手机号 / 邮箱落库加密）。
 *
 * <p>与 {@link com.wensaas.core.impl.file.CredentialCipher} 的区别：凭证用随机 IV
 * （不可逆向比对），而手机号 / 邮箱需支持「唯一性校验」等值查询，故本类采用
 * <b>确定性</b> AES-256-GCM —— IV = {@code HMAC-SHA256(key, 明文)[0..12]}，同明文恒得同密文，
 * DB 等值索引与唯一性判断仍然成立。代价：泄漏「两条记录是否相等」（搜索型加密的固有取舍），
 * 但不泄漏明文本身。密钥复用 {@link FileProperties}（与凭证同一套多版本密钥 / activeKeyId）。
 *
 * <p>密文格式 {@code <keyId>:<Base64(IV[12] || ciphertext || tag[16])>}，与 CredentialCipher 一致。
 *
 * <p>解密<b>容错</b>：入参不是本类产出的合法密文（无 keyId 前缀 / 未知 keyId / Base64 非法 /
 * GCM 校验失败）时<b>原样返回</b>——用于平滑兼容历史明文行（0-1 阶段不做存量回填，旧明文按
 * 原值读出，新写入自动加密）。
 */
public class SensitiveFieldCipher {

    private static final Logger log = LoggerFactory.getLogger(SensitiveFieldCipher.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String MAC_ALGORITHM = "HmacSHA256";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final char KEY_ID_SEPARATOR = ':';

    private final Map<String, SecretKeySpec> keys;
    private final String activeKeyId;

    public SensitiveFieldCipher(FileProperties props) {
        Map<String, String> raw = props.resolveKeys();
        if (raw.isEmpty()) {
            throw new IllegalStateException(
                    "wen-saas-boot.file.encryption-keys 未配置；敏感字段加密无法初始化（prod 必须由 env 注入）");
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
            throw new IllegalStateException("encryption-keys[" + keyId + "] 不是合法 Base64", ex);
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
        SensitiveFieldCipherHolder.set(this);
        log.info("[SensitiveFieldCipher] deterministic AES-256-GCM ready; keyIds={}, activeKeyId={}",
                keys.keySet(), activeKeyId);
    }

    /** 是否已就绪（至少一把密钥）。供透明面端点观察。 */
    public boolean keyConfigured() {
        return !keys.isEmpty();
    }

    /** 当前 active keyId。供透明面端点 / 测试观察。 */
    public String activeKeyId() {
        return activeKeyId;
    }

    /**
     * 加密。{@code null} 直接返回 {@code null}。同明文恒得同密文（确定性）。
     * 已是本类合法密文的入参直接返回（幂等，避免二次加密）。
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (isCiphertext(plaintext)) {
            return plaintext;
        }
        try {
            SecretKeySpec key = keys.get(activeKeyId);
            byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] iv = deterministicIv(key, plainBytes);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherTextWithTag = cipher.doFinal(plainBytes);
            byte[] combined = new byte[iv.length + cipherTextWithTag.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherTextWithTag, 0, combined, iv.length, cipherTextWithTag.length);
            return activeKeyId + KEY_ID_SEPARATOR + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("敏感字段加密失败", ex);
        }
    }

    /**
     * 解密。{@code null} 返回 {@code null}；非本类合法密文（含历史明文）<b>原样返回</b>。
     */
    public String decrypt(String value) {
        if (value == null) {
            return null;
        }
        int sep = value.indexOf(KEY_ID_SEPARATOR);
        if (sep < 0) {
            return value;
        }
        SecretKeySpec key = keys.get(value.substring(0, sep));
        if (key == null) {
            return value;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(value.substring(sep + 1));
            if (combined.length < IV_LENGTH + 16) {
                return value;
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] cipherTextWithTag = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherTextWithTag), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            // 非本类产出的密文（历史明文恰好含 ':'，或损坏）→ 容错原样返回
            return value;
        }
    }

    /** 是否为本类可解出的合法密文（keyId 已知且 GCM 校验通过）。 */
    public boolean isCiphertext(String value) {
        if (value == null) {
            return false;
        }
        int sep = value.indexOf(KEY_ID_SEPARATOR);
        if (sep < 0 || keys.get(value.substring(0, sep)) == null) {
            return false;
        }
        // 与 decrypt 同源：能解出即为合法密文（decrypt 容错返回原值，故用 !equals 判定）
        return !value.equals(decrypt(value));
    }

    private byte[] deterministicIv(SecretKeySpec key, byte[] plainBytes) throws Exception {
        Mac mac = Mac.getInstance(MAC_ALGORITHM);
        mac.init(new SecretKeySpec(key.getEncoded(), MAC_ALGORITHM));
        byte[] full = mac.doFinal(plainBytes);
        return Arrays.copyOfRange(full, 0, IV_LENGTH);
    }
}
