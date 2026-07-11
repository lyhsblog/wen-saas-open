package com.wensaas.core.web.ops;

import com.wensaas.core.api.ops.role.RecommendedRoleAdminException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link RecommendedRoleAdminException} → {@link ProblemDetail}。
 *
 * <p>HTTP 映射：
 * <ul>
 *   <li>404 NOT_FOUND：TEMPLATE_NOT_FOUND</li>
 *   <li>409 CONFLICT：NAME_CONFLICT</li>
 *   <li>422 UNPROCESSABLE_ENTITY：INVALID_STATUS_TRANSITION / PERMISSION_REMOVE_FORBIDDEN /
 *       PACKAGE_REMOVE_FORBIDDEN / TEMPLATE_IN_USE / INVALID_PERMISSION_CODE / PACKAGE_NOT_FOUND /
 *       INVALID_DATA_SCOPE / NO_PACKAGE_ASSOCIATED</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class RecommendedRoleAdminRestExceptionAdvice {

    @ExceptionHandler(RecommendedRoleAdminException.class)
    public ResponseEntity<ProblemDetail> onRecommendedRoleAdminException(RecommendedRoleAdminException e) {
        HttpStatus status = switch (e.code()) {
            case TEMPLATE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NAME_CONFLICT -> HttpStatus.CONFLICT;
            case INVALID_STATUS_TRANSITION, PERMISSION_REMOVE_FORBIDDEN, PACKAGE_REMOVE_FORBIDDEN,
                 TEMPLATE_IN_USE, INVALID_PERMISSION_CODE, PACKAGE_NOT_FOUND,
                 INVALID_DATA_SCOPE, NO_PACKAGE_ASSOCIATED -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Recommended role admin failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/ops/recommended-role/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
