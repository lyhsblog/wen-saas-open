package com.wensaas.core.web.tenant;

import com.wensaas.core.api.tenant.TenantActivityException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link TenantActivityException} → HTTP 403 ProblemDetail。
 *
 * <p>注意：{@link com.wensaas.core.impl.tenant.web.TenantActivityWebFilter} 是 Servlet 过滤器，
 * 在 DispatcherServlet 之前运行，MVC ExceptionHandler 不介入那一层；
 * 本 Advice 仅处理 Controller 层（含 AOP）抛出的 {@link TenantActivityException}。
 */
@RestControllerAdvice
@Order(0)
public class TenantActivityRestExceptionAdvice {

    @ExceptionHandler(TenantActivityException.class)
    public ResponseEntity<ProblemDetail> onTenantActivity(TenantActivityException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        pd.setTitle("Tenant inactive");
        pd.setType(URI.create("https://wen-saas-boot/errors/tenant/" + e.getCode().name().toLowerCase()));
        pd.setProperty("code", e.getCode().name());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
