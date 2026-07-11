package com.wensaas.core.web.compliance;

import com.wensaas.core.api.compliance.ComplianceException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link ComplianceException} → {@link ProblemDetail}。
 */
@RestControllerAdvice
@Order(0)
public class ComplianceRestExceptionAdvice {

    @ExceptionHandler(ComplianceException.class)
    public ResponseEntity<ProblemDetail> onException(ComplianceException e) {
        HttpStatus status = switch (e.code()) {
            case REQUEST_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case ILLEGAL_STATE -> HttpStatus.CONFLICT;
            case EXPORT_NOT_REQUIRED -> HttpStatus.CONFLICT;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Compliance operation failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/compliance/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
