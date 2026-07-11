package com.wensaas.core.web.tenant;

import com.wensaas.core.api.tenant.selfservice.TenantCompanyException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link TenantCompanyException} → {@link ProblemDetail}。
 *
 * <p>Code → HTTP：
 * <ul>
 *   <li>404: TENANT_NOT_FOUND</li>
 *   <li>409: ROUTE_SLOT_TAKEN / ROUTE_SLOT_CHANGE_TOO_SOON / LOGO_FILE_NOT_FOUND</li>
 *   <li>422: INVALID_ROUTE_SLOT / INVALID_LOGO</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class TenantCompanyRestExceptionAdvice {

    @ExceptionHandler(TenantCompanyException.class)
    public ResponseEntity<ProblemDetail> onCompanyException(TenantCompanyException e) {
        HttpStatus status = switch (e.code()) {
            case TENANT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ROUTE_SLOT_TAKEN, ROUTE_SLOT_CHANGE_TOO_SOON, LOGO_FILE_NOT_FOUND -> HttpStatus.CONFLICT;
            case INVALID_ROUTE_SLOT, INVALID_LOGO -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Company profile operation failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/company/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
