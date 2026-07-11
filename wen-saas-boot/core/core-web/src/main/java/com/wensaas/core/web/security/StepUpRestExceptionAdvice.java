package com.wensaas.core.web.security;

import com.wensaas.core.api.auth.stepup.StepUpException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * step-up 业务异常 → ProblemDetail。
 */
@RestControllerAdvice
@Order(0)
public class StepUpRestExceptionAdvice {

    @ExceptionHandler(StepUpException.class)
    public ResponseEntity<ProblemDetail> onStepUpException(StepUpException e) {
        HttpStatus status = switch (e.code()) {
            case TICKET_INVALID -> HttpStatus.UNAUTHORIZED;
            case CODE_INVALID -> HttpStatus.UNAUTHORIZED;
            case METHOD_REQUIRED, CHANNEL_UNAVAILABLE -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Step-up verification failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/stepup/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
