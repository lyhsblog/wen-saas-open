package com.wensaas.core.web.member;

import com.wensaas.core.api.auth.permission.MemberAdminException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link MemberAdminException} → {@link ProblemDetail}。
 */
@RestControllerAdvice
@Order(0)
public class MemberAdminRestExceptionAdvice {

    @ExceptionHandler(MemberAdminException.class)
    public ResponseEntity<ProblemDetail> onMemberAdminException(MemberAdminException e) {
        HttpStatus status = switch (e.getCode()) {
            case MEMBER_NOT_FOUND, DEPT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USERNAME_CONFLICT, EMAIL_CONFLICT, MOBILE_CONFLICT -> HttpStatus.CONFLICT;
            case OWNER_PROTECTED, CANNOT_DISABLE_SELF, LAST_DEPT_PROTECTED, MAIN_DEPT_REQUIRED ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Member admin failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/member/" + e.getCode().name().toLowerCase()));
        pd.setProperty("code", e.getCode().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
