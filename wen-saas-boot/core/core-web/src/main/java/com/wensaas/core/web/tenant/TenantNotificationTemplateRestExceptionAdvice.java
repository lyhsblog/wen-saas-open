package com.wensaas.core.web.tenant;

import com.wensaas.core.api.notification.template.TenantNotificationTemplateException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link TenantNotificationTemplateException} → {@link ProblemDetail}。
 *
 * <p>HTTP 映射：
 * <ul>
 *   <li>404 NOT_FOUND：TEMPLATE_NOT_FOUND</li>
 *   <li>422 UNPROCESSABLE_ENTITY：SYS_TEMPLATE_PROTECTED / INVALID_CHANNEL</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class TenantNotificationTemplateRestExceptionAdvice {

    @ExceptionHandler(TenantNotificationTemplateException.class)
    public ResponseEntity<ProblemDetail> onException(TenantNotificationTemplateException e) {
        HttpStatus status = switch (e.code()) {
            case TEMPLATE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SYS_TEMPLATE_PROTECTED, INVALID_CHANNEL -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Tenant notification template failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/tenant/notification-template/"
                + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
