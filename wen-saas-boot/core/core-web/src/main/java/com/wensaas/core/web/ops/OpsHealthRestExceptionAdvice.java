package com.wensaas.core.web.ops;

import com.wensaas.core.api.ops.health.OpsHealthException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link OpsHealthException} → {@link ProblemDetail}。
 */
@RestControllerAdvice
@Order(0)
public class OpsHealthRestExceptionAdvice {

    @ExceptionHandler(OpsHealthException.class)
    public ResponseEntity<ProblemDetail> onException(OpsHealthException e) {
        HttpStatus status = switch (e.code()) {
            case TENANT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Ops health failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/ops/health/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
