package com.wensaas.core.web.auth;

import com.wensaas.core.api.auth.login.MfaException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * MFA 业务异常 → ProblemDetail。
 */
@RestControllerAdvice
@Order(0)
public class MfaRestExceptionAdvice {

    @ExceptionHandler(MfaException.class)
    public ResponseEntity<ProblemDetail> onMfaException(MfaException e) {
        HttpStatus status = switch (e.code()) {
            case PREAUTH_INVALID -> HttpStatus.UNAUTHORIZED;
            case CODE_INVALID -> HttpStatus.UNAUTHORIZED;
            case METHOD_REQUIRED, TARGET_MISSING, ILLEGAL_STATE -> HttpStatus.BAD_REQUEST;
            case NOT_CONFIGURED -> HttpStatus.CONFLICT;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("MFA failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/mfa/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
