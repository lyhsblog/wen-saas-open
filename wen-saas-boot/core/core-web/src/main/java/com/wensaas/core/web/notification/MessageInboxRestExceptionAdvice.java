package com.wensaas.core.web.notification;

import com.wensaas.core.api.notification.inbox.MessageInboxException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link MessageInboxException} → {@link ProblemDetail}（与 {@code FileRestExceptionAdvice} 同模式）。
 */
@RestControllerAdvice
@Order(0)
public class MessageInboxRestExceptionAdvice {

    @ExceptionHandler(MessageInboxException.class)
    public ResponseEntity<ProblemDetail> onInboxException(MessageInboxException e) {
        HttpStatus status = switch (e.code()) {
            case MESSAGE_NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Message inbox operation failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/message-inbox/"
                + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
