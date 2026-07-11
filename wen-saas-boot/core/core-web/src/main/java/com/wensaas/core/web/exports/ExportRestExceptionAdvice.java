package com.wensaas.core.web.exports;

import com.wensaas.core.api.exports.ExportException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link ExportException} → {@link ProblemDetail}（与 {@code ImportRestExceptionAdvice} 同模式）。
 *
 * <p>Code → HTTP 映射：
 * <ul>
 *   <li>403: FORBIDDEN（缺该对象的发起权限）</li>
 *   <li>422: OBJECT_UNKNOWN / REASON_REQUIRED</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class ExportRestExceptionAdvice {

    @ExceptionHandler(ExportException.class)
    public ResponseEntity<ProblemDetail> onExportException(ExportException e) {
        HttpStatus status = switch (e.code()) {
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case OBJECT_UNKNOWN, REASON_REQUIRED -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Export operation failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/export/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
