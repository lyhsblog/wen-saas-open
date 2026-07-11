package com.wensaas.core.web.auth;

import com.wensaas.core.api.auth.login.ImpersonateException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link ImpersonateException} → {@link ProblemDetail}。
 */
@RestControllerAdvice
@Order(0)
public class ImpersonateRestExceptionAdvice {

    @ExceptionHandler(ImpersonateException.class)
    public ResponseEntity<ProblemDetail> onImpersonateException(ImpersonateException e) {
        HttpStatus status = switch (e.code()) {
            case IMPERSONATE_TARGET_TENANT_NOT_FOUND,
                 IMPERSONATE_TARGET_USER_NOT_FOUND,
                 IMPERSONATE_NONCE_INVALID -> HttpStatus.NOT_FOUND;
            case IMPERSONATE_TARGET_TENANT_INACTIVE,
                 IMPERSONATE_TARGET_USER_DISABLED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case IMPERSONATE_USER_NOT_IN_TENANT,
                 IMPERSONATE_TARGET_IS_OPS,
                 IMPERSONATE_NESTED_NOT_ALLOWED,
                 EXIT_IMPERSONATION_NOT_IMPERSONATING -> HttpStatus.BAD_REQUEST;
            case IMPERSONATE_TENANT_NOT_IN_SCOPE -> HttpStatus.FORBIDDEN;
            case IMPERSONATE_OPERATOR_SESSION_GONE,
                 EXIT_IMPERSONATION_ORIGINAL_GONE -> HttpStatus.valueOf(410);
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Impersonate failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/impersonate/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
