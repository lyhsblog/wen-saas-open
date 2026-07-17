package com.wensaas.core.web.ops;

import com.wensaas.core.api.pkg.PackageAdminException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link PackageAdminException} → {@link ProblemDetail}。
 *
 * <p>HTTP 映射：
 * <ul>
 *   <li>404 NOT_FOUND：PACKAGE_NOT_FOUND / MODULE_NOT_FOUND</li>
 *   <li>409 CONFLICT：CODE_CONFLICT</li>
 *   <li>422 UNPROCESSABLE_ENTITY：BASIC_PACKAGE_PROTECTED / DECLARED_PACKAGE_PROTECTED / INVALID_STATUS_TRANSITION / MODULE_REMOVE_FORBIDDEN</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class PackageAdminRestExceptionAdvice {

    @ExceptionHandler(PackageAdminException.class)
    public ResponseEntity<ProblemDetail> onPackageAdminException(PackageAdminException e) {
        HttpStatus status = switch (e.code()) {
            case PACKAGE_NOT_FOUND, MODULE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CODE_CONFLICT -> HttpStatus.CONFLICT;
            case BASIC_PACKAGE_PROTECTED, DECLARED_PACKAGE_PROTECTED, INVALID_STATUS_TRANSITION,
                 MODULE_REMOVE_FORBIDDEN -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Package admin failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/ops/package/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
