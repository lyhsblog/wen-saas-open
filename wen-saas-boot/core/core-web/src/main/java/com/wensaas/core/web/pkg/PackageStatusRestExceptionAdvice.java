package com.wensaas.core.web.pkg;

import com.wensaas.core.api.pkg.PackageStatusException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link PackageStatusException} → HTTP 402 / 403 ProblemDetail。
 *
 * <p>注意：{@link com.wensaas.core.impl.pkg.PackageStatusWebFilter} 是 Servlet 过滤器，
 * 在 DispatcherServlet 之前运行，MVC ExceptionHandler 不介入那一层；
 * 本 Advice 仅处理 Controller / AOP 层抛出的 {@link PackageStatusException}。
 */
@RestControllerAdvice
@Order(0)
public class PackageStatusRestExceptionAdvice {

    @ExceptionHandler(PackageStatusException.class)
    public ResponseEntity<ProblemDetail> onPackageStatus(PackageStatusException e) {
        HttpStatus status = e.getCode() == PackageStatusException.Code.PACKAGE_UNAUTHORIZED
                ? HttpStatus.PAYMENT_REQUIRED : HttpStatus.FORBIDDEN;
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Package access denied");
        pd.setType(URI.create("https://wen-saas-boot/errors/package/" + e.getCode().name().toLowerCase()));
        pd.setProperty("code", e.getCode().name());
        pd.setProperty("moduleCode", e.getModuleCode());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
