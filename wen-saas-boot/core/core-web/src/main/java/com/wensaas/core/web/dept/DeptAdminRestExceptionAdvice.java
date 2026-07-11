package com.wensaas.core.web.dept;

import com.wensaas.core.api.auth.org.DeptAdminException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link DeptAdminException} → {@link ProblemDetail}。
 */
@RestControllerAdvice
@Order(0)
public class DeptAdminRestExceptionAdvice {

    @ExceptionHandler(DeptAdminException.class)
    public ResponseEntity<ProblemDetail> onDeptAdminException(DeptAdminException e) {
        HttpStatus status = switch (e.code()) {
            case DEPT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DEPT_NAME_DUPLICATE, DEPT_HAS_CHILDREN, DEPT_HAS_MEMBERS -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Dept admin failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/dept/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
