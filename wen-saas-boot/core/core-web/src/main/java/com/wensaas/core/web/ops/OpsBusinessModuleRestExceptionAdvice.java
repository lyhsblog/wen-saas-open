package com.wensaas.core.web.ops;

import com.wensaas.core.api.ops.OpsBusinessModuleException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link OpsBusinessModuleException} → {@link ProblemDetail}。
 *
 * <p>HTTP 映射：404 NOT_FOUND（MODULE_NOT_FOUND）。
 */
@RestControllerAdvice
@Order(0)
public class OpsBusinessModuleRestExceptionAdvice {

    @ExceptionHandler(OpsBusinessModuleException.class)
    public ResponseEntity<ProblemDetail> onException(OpsBusinessModuleException e) {
        HttpStatus status = switch (e.code()) {
            case MODULE_NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Ops business module failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/ops/business-module/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
