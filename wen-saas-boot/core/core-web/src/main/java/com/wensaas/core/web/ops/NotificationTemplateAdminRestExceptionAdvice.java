package com.wensaas.core.web.ops;

import com.wensaas.core.api.notification.template.NotificationTemplateAdminException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link NotificationTemplateAdminException} → {@link ProblemDetail}。
 *
 * <p>HTTP 映射：
 * <ul>
 *   <li>404 NOT_FOUND：TEMPLATE_NOT_FOUND</li>
 *   <li>409 CONFLICT：CODE_CONFLICT</li>
 *   <li>422 UNPROCESSABLE_ENTITY：SYS_TEMPLATE_PROTECTED / INVALID_CHANNEL / EMPTY_CONTENT</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class NotificationTemplateAdminRestExceptionAdvice {

    @ExceptionHandler(NotificationTemplateAdminException.class)
    public ResponseEntity<ProblemDetail> onException(NotificationTemplateAdminException e) {
        HttpStatus status = switch (e.code()) {
            case TEMPLATE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CODE_CONFLICT -> HttpStatus.CONFLICT;
            case SYS_TEMPLATE_PROTECTED, INVALID_CHANNEL, EMPTY_CONTENT -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Notification template admin failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/ops/notification-template/"
                + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
