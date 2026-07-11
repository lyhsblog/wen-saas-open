package com.wensaas.core.web.publicapi;

import com.wensaas.core.api.publicapi.TenantPublicException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link TenantPublicException} → {@link ProblemDetail}。
 *
 * <p>HTTP 映射：
 * <ul>
 *   <li>404: TENANT_NOT_FOUND</li>
 * </ul>
 *
 * <p>{@code @Order(0)} 必须显式覆盖 {@link com.wensaas.core.web.common.BaseRestExceptionAdvice}
 * 的 {@code LOWEST_PRECEDENCE} 兜底；否则两个 advice 同优先级时 Spring 按 bean 名解析，
 * 可能落到 BaseAdvice 的 {@code @ExceptionHandler(Exception.class)} 兜底→500（端到端 jar 验证踩点）。
 */
@RestControllerAdvice
@Order(0)
public class TenantPublicRestExceptionAdvice {

    @ExceptionHandler(TenantPublicException.class)
    public ResponseEntity<ProblemDetail> onTenantPublicException(TenantPublicException e) {
        HttpStatus status = switch (e.code()) {
            case TENANT_NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Public tenant lookup failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/public/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
