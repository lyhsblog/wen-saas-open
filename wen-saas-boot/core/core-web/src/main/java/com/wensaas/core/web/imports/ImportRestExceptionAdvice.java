package com.wensaas.core.web.imports;

import com.wensaas.core.api.imports.ImportException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link ImportException} → {@link ProblemDetail}（与 {@code FileRestExceptionAdvice} 同模式）。
 *
 * <p>Code → HTTP 映射：
 * <ul>
 *   <li>404: ENTITY_UNKNOWN / SESSION_NOT_FOUND</li>
 *   <li>409: HAS_ERRORS（仍有错误行，不允许确认）</li>
 *   <li>422: FILE_CORRUPT / EMPTY_FILE / TEMPLATE_MISMATCH / ROW_LIMIT_EXCEEDED</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class ImportRestExceptionAdvice {

    @ExceptionHandler(ImportException.class)
    public ResponseEntity<ProblemDetail> onImportException(ImportException e) {
        HttpStatus status = switch (e.code()) {
            case ENTITY_UNKNOWN, SESSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case HAS_ERRORS -> HttpStatus.CONFLICT;
            case FILE_CORRUPT, EMPTY_FILE, TEMPLATE_MISMATCH, ROW_LIMIT_EXCEEDED ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Import operation failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/import/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
