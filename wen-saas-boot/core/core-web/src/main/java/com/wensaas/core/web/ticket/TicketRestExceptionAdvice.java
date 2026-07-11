package com.wensaas.core.web.ticket;

import com.wensaas.core.api.ticket.TicketException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link TicketException} → {@link ProblemDetail}。
 */
@RestControllerAdvice
@Order(0)
public class TicketRestExceptionAdvice {

    @ExceptionHandler(TicketException.class)
    public ResponseEntity<ProblemDetail> onException(TicketException e) {
        HttpStatus status = switch (e.code()) {
            case TICKET_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case ILLEGAL_STATE -> HttpStatus.CONFLICT;
            case ATTACHMENT_TOO_LARGE, ATTACHMENT_QUOTA_EXCEEDED -> HttpStatus.PAYLOAD_TOO_LARGE;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Ticket operation failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/ticket/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
