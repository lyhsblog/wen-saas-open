package com.wensaas.core.web.file;

import com.wensaas.core.api.file.center.FileCenterException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link FileCenterException} → {@link ProblemDetail}（与 {@code FileRestExceptionAdvice} 同模式）。
 *
 * <ul>
 *   <li>404: FOLDER_NOT_FOUND / FILE_NOT_FOUND</li>
 *   <li>409: FOLDER_NAME_DUP / FOLDER_MOVE_CYCLE / NOT_RECYCLED</li>
 *   <li>422: FOLDER_DEPTH_EXCEEDED / QUOTA_EXCEEDED / INVALID_ARGUMENT</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class FileCenterRestExceptionAdvice {

    @ExceptionHandler(FileCenterException.class)
    public ResponseEntity<ProblemDetail> onFileCenterException(FileCenterException e) {
        HttpStatus status = switch (e.code()) {
            case FOLDER_NOT_FOUND, FILE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FOLDER_NAME_DUP, FOLDER_MOVE_CYCLE, NOT_RECYCLED -> HttpStatus.CONFLICT;
            case FOLDER_DEPTH_EXCEEDED, QUOTA_EXCEEDED, INVALID_ARGUMENT -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("File center operation failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/file-center/"
                + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
