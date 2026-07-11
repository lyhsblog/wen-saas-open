package com.wensaas.core.impl.openapi.key;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 生成开放 API 凭证（apiKey + 明文 secret）。
 *
 * <p>apiKey 以 {@code ak_} 前缀 + 24 字节 Base64-URL（共 35 字符）；
 * secret 32 字节 Base64-URL（共 43 字符）。明文 secret **仅在创建时一次性**返回调用方，
 * 入库走 {@code CredentialCipher} AES 加密（与 sys_webhook.signing_secret 同模式）。
 */
public class ApiKeyCredentialGenerator {

    private static final String AK_PREFIX = "ak_";
    private static final int AK_RANDOM_BYTES = 24;
    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RNG = new SecureRandom();

    private ApiKeyCredentialGenerator() {
    }

    public static GeneratedCredential generate() {
        return new GeneratedCredential(generateApiKey(), generatePlainSecret());
    }

    public static String generateApiKey() {
        byte[] buf = new byte[AK_RANDOM_BYTES];
        RNG.nextBytes(buf);
        return AK_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public static String generatePlainSecret() {
        byte[] buf = new byte[SECRET_BYTES];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /** 一次性凭证生成结果。{@code plainSecret} 仅展示给用户一次，落库走 AES 加密。 */
    public record GeneratedCredential(String apiKey, String plainSecret) {
    }
}
