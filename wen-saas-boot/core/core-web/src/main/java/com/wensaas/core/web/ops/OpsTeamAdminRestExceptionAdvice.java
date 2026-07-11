package com.wensaas.core.web.ops;

import com.wensaas.core.api.ops.team.OpsTeamAdminException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link OpsTeamAdminException} → {@link ProblemDetail}。
 *
 * <p>HTTP 映射：
 * <ul>
 *   <li>403: CANNOT_OPERATE_SUPER_ADMIN</li>
 *   <li>404: MEMBER_NOT_FOUND / ROLE_NOT_FOUND</li>
 *   <li>409: USERNAME_CONFLICT / EMAIL_CONFLICT / MOBILE_CONFLICT / ROLE_CODE_CONFLICT / ROLE_NAME_CONFLICT</li>
 *   <li>422: CANNOT_DISABLE_SELF / CANNOT_DELETE_PRESET_ROLE / CANNOT_RENAME_PRESET_ROLE_CODE / SUPER_ADMIN_PERMISSIONS_READONLY / ROLE_HAS_MEMBERS / INVALID_DATA_SCOPE / INVALID_PERMISSION_CODE / WEAK_PASSWORD</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class OpsTeamAdminRestExceptionAdvice {

    @ExceptionHandler(OpsTeamAdminException.class)
    public ResponseEntity<ProblemDetail> onException(OpsTeamAdminException e) {
        HttpStatus status = switch (e.code()) {
            case CANNOT_OPERATE_SUPER_ADMIN -> HttpStatus.FORBIDDEN;
            case MEMBER_NOT_FOUND, ROLE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USERNAME_CONFLICT, EMAIL_CONFLICT, MOBILE_CONFLICT,
                 ROLE_CODE_CONFLICT, ROLE_NAME_CONFLICT -> HttpStatus.CONFLICT;
            case CANNOT_DISABLE_SELF, CANNOT_DELETE_PRESET_ROLE,
                 CANNOT_RENAME_PRESET_ROLE_CODE, SUPER_ADMIN_PERMISSIONS_READONLY,
                 ROLE_HAS_MEMBERS, INVALID_DATA_SCOPE, INVALID_PERMISSION_CODE,
                 WEAK_PASSWORD -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Ops team admin failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/ops/team/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
