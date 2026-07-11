package com.wensaas.core.web.role;

import com.wensaas.core.api.auth.permission.RoleAdminException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link RoleAdminException} → {@link ProblemDetail}。
 */
@RestControllerAdvice
@Order(0)
public class RoleAdminRestExceptionAdvice {

    @ExceptionHandler(RoleAdminException.class)
    public ResponseEntity<ProblemDetail> onRoleAdminException(RoleAdminException e) {
        HttpStatus status = switch (e.code()) {
            case ROLE_NOT_FOUND, USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ROLE_CODE_DUPLICATE, ROLE_NAME_DUPLICATE, ROLE_HAS_MEMBERS -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Role admin failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/role/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
