package com.wensaas.core.web.role;

import com.wensaas.core.api.auth.permission.TenantRecommendedRoleException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link TenantRecommendedRoleException} → {@link ProblemDetail}。
 */
@RestControllerAdvice
@Order(0)
public class TenantRecommendedRoleRestExceptionAdvice {

    @ExceptionHandler(TenantRecommendedRoleException.class)
    public ResponseEntity<ProblemDetail> onException(TenantRecommendedRoleException e) {
        HttpStatus status = switch (e.getCode()) {
            case TEMPLATE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ROLE_NAME_DUPLICATE -> HttpStatus.CONFLICT;
            case TEMPLATE_NOT_PUBLISHED, PACKAGE_NOT_MATCHED -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Recommended role activation failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/recommended-role/" + e.getCode().name().toLowerCase()));
        pd.setProperty("code", e.getCode().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
