package com.wensaas.core.web.auth;

import com.wensaas.core.api.auth.login.AuthenticationException;
import com.wensaas.core.api.auth.permission.PermissionDeniedException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * 认证 / 权限异常 → ProblemDetail。
 *
 * <p>{@link AuthenticationException}：AuthService.login 抛出（AuthenticationFilter 抛的已在过滤
 * 器内部写出 ProblemDetail，不会走到这里）。
 *
 * <p>{@link PermissionDeniedException}：{@code RequirePermissionAspect} 抛出，401（未认证）/ 403
 * （权限不足）。
 */
@RestControllerAdvice
@Order(0)
public class AuthRestExceptionAdvice {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> onAuthException(AuthenticationException e) {
        HttpStatus status = switch (e.getCode()) {
            case DISABLED -> HttpStatus.FORBIDDEN;
            case LOGIN_LOCKED -> HttpStatus.valueOf(423);
            // USER_NOT_FOUND 对客户端与 BAD_CREDENTIALS 相同，均 401。
            default -> HttpStatus.UNAUTHORIZED;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Authentication failed");
        // 对外暴露 BAD_CREDENTIALS，隐藏内部 USER_NOT_FOUND 区分。
        String clientCode = e.getCode() == AuthenticationException.Code.USER_NOT_FOUND
                ? AuthenticationException.Code.BAD_CREDENTIALS.name()
                : e.getCode().name();
        pd.setType(URI.create("https://wen-saas-boot/errors/auth/" + clientCode.toLowerCase()));
        pd.setProperty("code", clientCode);
        if (e.getAttemptsLeft() != null) {
            pd.setProperty("attemptsLeft", e.getAttemptsLeft());
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ProblemDetail> onPermissionDenied(PermissionDeniedException e) {
        HttpStatus status = switch (e.getCode()) {
            case NOT_AUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Permission denied");
        pd.setType(URI.create("https://wen-saas-boot/errors/permission/" + e.getCode().name().toLowerCase()));
        pd.setProperty("code", e.getCode().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
