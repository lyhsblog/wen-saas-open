package com.wensaas.core.web.tenant;

import com.wensaas.core.api.tenant.selfservice.TenantProfileException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link TenantProfileException} → {@link ProblemDetail}。
 *
 * <p>HTTP 映射：
 * <ul>
 *   <li>404 NOT_FOUND: USER_NOT_FOUND / SESSION_NOT_FOUND</li>
 *   <li>409 CONFLICT: CANNOT_REVOKE_CURRENT_SESSION / MOBILE_CHANGE_ILLEGAL_STATE</li>
 *   <li>422 UNPROCESSABLE_ENTITY: OLD_PASSWORD_MISMATCH / WEAK_PASSWORD / INVALID_AVATAR /
 *       MOBILE_CHANGE_CODE_INVALID / MOBILE_CHANGE_INVALID_NEW</li>
 *   <li>400 BAD_REQUEST: MOBILE_CHANGE_PREREQ_MISSING</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class TenantProfileRestExceptionAdvice {

    @ExceptionHandler(TenantProfileException.class)
    public ResponseEntity<ProblemDetail> onTenantProfileException(TenantProfileException e) {
        HttpStatus status = switch (e.code()) {
            case USER_NOT_FOUND, SESSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CANNOT_REVOKE_CURRENT_SESSION, MOBILE_CHANGE_ILLEGAL_STATE,
                 EMAIL_CHANGE_ILLEGAL_STATE, EMAIL_CONFLICT -> HttpStatus.CONFLICT;
            case OLD_PASSWORD_MISMATCH, WEAK_PASSWORD, INVALID_AVATAR, MOBILE_CHANGE_CODE_INVALID,
                 MOBILE_CHANGE_INVALID_NEW, EMAIL_CHANGE_CODE_INVALID,
                 EMAIL_CHANGE_INVALID_NEW -> HttpStatus.UNPROCESSABLE_ENTITY;
            case MOBILE_CHANGE_PREREQ_MISSING -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Tenant profile operation failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/tenant-profile/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
