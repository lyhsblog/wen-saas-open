package com.wensaas.core.impl.openapi.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HMAC-SHA256 签名计算 + 校验工具。
 *
 * <p>客户端：
 * <pre>
 *   string_to_sign = method + "\n" + path + "\n" + body + "\n" + timestamp + "\n" + nonce
 *   signature      = HMAC-SHA256(api_secret, string_to_sign)
 * </pre>
 *
 * <p>{@code nonce} 是客户端为每次请求生成的随机串（一次性），既参与签名（防被替换）、
 * 又由服务端在时间戳窗口内去重（防重放）。
 *
 * <p>服务端：用同一 secret 重算并比对。{@code body} 必须是客户端发出的<b>原始字节流</b>，
 * 不做 JSON 字段排序或序列化规范化（与微信开放平台 / OSS 同惯例）。
 */
public class HmacSignatureVerifier {

    private static final String HMAC_ALGO = "HmacSHA256";

    /** 用 secret 字节重新计算签名，Base64 编码（与客户端约定）。 */
    public String sign(String secret, String method, String path, String body, long timestamp, String nonce) {
        String stringToSign = method + "\n" + path + "\n" + (body == null ? "" : body)
                + "\n" + timestamp + "\n" + (nonce == null ? "" : nonce);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] sig = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC 计算失败", ex);
        }
    }

    /** 常量时间比较签名，防 timing attack。 */
    public boolean verify(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
