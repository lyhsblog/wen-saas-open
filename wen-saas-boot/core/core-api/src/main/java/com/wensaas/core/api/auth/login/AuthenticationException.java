package com.wensaas.core.api.auth.login;

/**
 * 认证失败统一异常。
 *
 * <p>由 AuthenticationFilter / AuthService 抛出，Web 层异常 advice 翻译为 401/403/423 ProblemDetail。
 */
public class AuthenticationException extends RuntimeException {

    public enum Code {
        /** 缺少 Authorization header 或非 Bearer 形式。 */
        MISSING_TOKEN,
        /** JWT 验签失败 / 格式错误。 */
        INVALID_TOKEN,
        /** JWT 已过期。 */
        EXPIRED,
        /** JWT 有效但 Redis Session 不存在（已登出 / 被踢 / TTL 到期）。 */
        NO_SESSION,
        /** 账号或密码错误。 */
        BAD_CREDENTIALS,
        /** 账号已停用（status != 1）。 */
        DISABLED,
        /** 连续失败次数达上限，账号临时锁定（HTTP 423）。 */
        LOGIN_LOCKED,
        /** 用户名不存在（内部区分用，客户端与 BAD_CREDENTIALS 同样返回 401）。 */
        USER_NOT_FOUND
    }

    private final Code code;
    /** 剩余可尝试次数，第 3 次及以后失败时非 null，由 Web 层写入 ProblemDetail。 */
    private final Integer attemptsLeft;

    public AuthenticationException(Code code, String message) {
        super(message);
        this.code = code;
        this.attemptsLeft = null;
    }

    public AuthenticationException(Code code, String message, Integer attemptsLeft) {
        super(message);
        this.code = code;
        this.attemptsLeft = attemptsLeft;
    }

    public AuthenticationException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.attemptsLeft = null;
    }

    public Code getCode() {
        return code;
    }

    public Integer getAttemptsLeft() {
        return attemptsLeft;
    }
}
