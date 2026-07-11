package com.wensaas.core.impl.webhook.dispatch;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Webhook HMAC-SHA256 签名。
 *
 * <pre>
 *   string_to_sign = event + "\n" + timestamp + "\n" + body
 *   X-Wen-Signature = Base64(HMAC-SHA256(signing_secret, string_to_sign))
 * </pre>
 */
public class WebhookSignatureSigner {

    private static final String HMAC_ALGO = "HmacSHA256";

    public String sign(String signingSecret, String event, long timestamp, String body) {
        String stringToSign = event + "\n" + timestamp + "\n" + (body == null ? "" : body);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] sig = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig);
        } catch (Exception ex) {
            throw new IllegalStateException("Webhook HMAC 计算失败", ex);
        }
    }
}
