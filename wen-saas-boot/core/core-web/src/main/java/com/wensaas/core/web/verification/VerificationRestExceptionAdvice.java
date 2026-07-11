package com.wensaas.core.web.verification;

import com.wensaas.core.api.verification.VerificationException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * 验证码业务异常 → ProblemDetail。
 */
@RestControllerAdvice
@Order(0)
public class VerificationRestExceptionAdvice {

    @ExceptionHandler(VerificationException.class)
    public ResponseEntity<ProblemDetail> onVerificationException(VerificationException e) {
        HttpStatus status = switch (e.code()) {
            case RATE_LIMITED, DAILY_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case TARGET_REQUIRED, CHANNEL_UNAVAILABLE -> HttpStatus.BAD_REQUEST;
            case DELIVERY_FAILED -> HttpStatus.BAD_GATEWAY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Verification failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/verification/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        if (e.retryAfterSeconds() != null) {
            pd.setProperty("retryAfterSeconds", e.retryAfterSeconds());
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
