package com.wensaas.core.api.openapi;

/**
 * 开放 API 鉴权失败统一异常。
 *
 * <p>由 {@code AppKeyAuthenticationFilter} 抛出，{@code OpenApiRestExceptionAdvice} 转 401/413/429
 * ProblemDetail。客户端可见的响应体不暴露具体 Code（防探测），仅记 server 日志。
 */
public class OpenApiAuthenticationException extends RuntimeException {

    public enum Code {
        /** 缺少 X-Wen-API-Key / X-Wen-Signature / X-Wen-Timestamp 等必需 Header。 */
        MISSING_HEADER,
        /** api_key 在 sys_api_key 不存在 / 已删除 / 已撤销。 */
        INVALID_API_KEY,
        /** sys_api_key.status != 'active'。 */
        KEY_DISABLED,
        /** sys_api_key.expires_at < now()。 */
        KEY_EXPIRED,
        /** HMAC 签名不匹配。 */
        INVALID_SIGNATURE,
        /** X-Wen-Timestamp 与服务端时钟偏差超过阈值（默认 5min）。 */
        EXPIRED_TIMESTAMP,
        /** X-Wen-Nonce 缺失、或在时间戳窗口内已被使用过（重放）。 */
        REPLAYED_NONCE,
        /** 客户端 IP 不在 sys_api_key.ip_whitelist 列出的 CIDR / 单 IP 范围。 */
        IP_REJECTED,
        /** 租户级 / 凭证级限流超出配额。 */
        RATE_LIMIT_EXCEEDED,
        /** body 超过 wen-saas-boot.openapi.max-body-bytes（默认 1MB）。 */
        PAYLOAD_TOO_LARGE
    }

    private final Code code;

    public OpenApiAuthenticationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public OpenApiAuthenticationException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
