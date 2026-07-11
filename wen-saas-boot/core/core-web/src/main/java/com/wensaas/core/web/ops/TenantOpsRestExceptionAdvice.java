package com.wensaas.core.web.ops;

import com.wensaas.core.api.ops.TenantOpsException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link TenantOpsException} → {@link ProblemDetail}。
 *
 * <p>HTTP 映射：
 * <ul>
 *   <li>409 CONFLICT: DUPLICATE_TENANT_CODE / DUPLICATE_TENANT_NO / DUPLICATE_ADMIN_USERNAME</li>
 *   <li>404 NOT_FOUND: PACKAGE_NOT_FOUND / TENANT_NOT_FOUND</li>
 *   <li>422 UNPROCESSABLE_ENTITY: PACKAGE_NOT_ACTIVE / NO_MAIN_ADMIN</li>
 *   <li>403 FORBIDDEN: FORBIDDEN（OPS_OWNED 写非名下租户）</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class TenantOpsRestExceptionAdvice {

    @ExceptionHandler(TenantOpsException.class)
    public ResponseEntity<ProblemDetail> onTenantOpsException(TenantOpsException e) {
        HttpStatus status = switch (e.code()) {
            case DUPLICATE_TENANT_CODE, DUPLICATE_TENANT_NO, DUPLICATE_ADMIN_USERNAME,
                 PLAN_ALREADY_ACTIVE, MAIN_ADMIN_SAME_AS_CURRENT -> HttpStatus.CONFLICT;
            case PACKAGE_NOT_FOUND, TENANT_NOT_FOUND, MEMBER_NOT_FOUND,
                 PLAN_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PACKAGE_NOT_ACTIVE, NO_MAIN_ADMIN, PLAN_ALREADY_REVOKED,
                 CSM_NOT_FOUND, NEW_ADMIN_NAME_REQUIRED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INVALID_STATUS, OLD_ADMIN_HANDLING_INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Tenant ops failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/ops/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
