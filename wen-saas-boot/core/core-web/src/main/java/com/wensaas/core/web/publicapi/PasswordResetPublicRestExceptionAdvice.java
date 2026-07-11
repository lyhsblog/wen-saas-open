package com.wensaas.core.web.publicapi;

import com.wensaas.core.api.auth.login.PasswordResetException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link PasswordResetException} → {@link ProblemDetail}。
 *
 * <p>HTTP 映射：
 * <ul>
 *   <li>404: TOKEN_NOT_FOUND</li>
 *   <li>410 GONE: TOKEN_EXPIRED / TOKEN_USED / TOKEN_INVALIDATED</li>
 *   <li>409 CONFLICT: USERNAME_MISMATCH</li>
 *   <li>422 UNPROCESSABLE_ENTITY: PASSWORD_TOO_WEAK</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class PasswordResetPublicRestExceptionAdvice {

    @ExceptionHandler(PasswordResetException.class)
    public ResponseEntity<ProblemDetail> onPasswordResetException(PasswordResetException e) {
        HttpStatus status = switch (e.code()) {
            case TOKEN_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TOKEN_EXPIRED, TOKEN_USED, TOKEN_INVALIDATED -> HttpStatus.GONE;
            case USERNAME_MISMATCH -> HttpStatus.CONFLICT;
            case PASSWORD_TOO_WEAK -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Password reset failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/public/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
