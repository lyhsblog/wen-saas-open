package com.wensaas.core.web.auth;

import com.wensaas.core.api.auth.oauth.OAuthBindConflictException;
import com.wensaas.core.api.auth.oauth.OAuthExchangeException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * 第三方绑定业务异常 → ProblemDetail。
 *
 * <p>注意：登录路径下换码失败已在 {@code OAuthLoginIdentityProvider} 内转为 {@code AuthenticationException}
 * 由 {@code AuthRestExceptionAdvice} 处理；此处仅处理「已登录态主动绑定」直接冒泡的 OAuth 异常。
 */
@RestControllerAdvice
@Order(0)
public class OAuthRestExceptionAdvice {

    @ExceptionHandler(OAuthBindConflictException.class)
    public ResponseEntity<ProblemDetail> onConflict(OAuthBindConflictException e) {
        return build(HttpStatus.CONFLICT, "OAUTH_ALREADY_BOUND", "OAuth binding conflict", e.getMessage());
    }

    @ExceptionHandler(OAuthExchangeException.class)
    public ResponseEntity<ProblemDetail> onExchangeFailed(OAuthExchangeException e) {
        return build(HttpStatus.UNAUTHORIZED, "OAUTH_EXCHANGE_FAILED", "OAuth exchange failed", e.getMessage());
    }

    private static ResponseEntity<ProblemDetail> build(HttpStatus status, String code, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("https://wen-saas-boot/errors/oauth/" + code.toLowerCase()));
        pd.setProperty("code", code);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
