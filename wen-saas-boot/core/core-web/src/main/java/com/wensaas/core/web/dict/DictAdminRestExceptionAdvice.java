package com.wensaas.core.web.dict;

import com.wensaas.core.api.dict.DictAdminException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link DictAdminException} → {@link ProblemDetail}。
 *
 * <p>HTTP 映射：
 * <ul>
 *   <li>404: GROUP_NOT_FOUND / ITEM_NOT_FOUND</li>
 *   <li>409: GROUP_CODE_DUPLICATE / ITEM_CODE_DUPLICATE / GROUP_HAS_ITEMS / ITEM_HAS_CHILDREN</li>
 *   <li>400: GROUP_SYSTEM_READONLY / GROUP_MODULE_READONLY / ITEM_SYSTEM_READONLY /
 *        EXTRA_FIELD_VALIDATION_FAILED / ITEM_PARENT_INVALID / ITEM_DEPTH_EXCEEDED</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class DictAdminRestExceptionAdvice {

    @ExceptionHandler(DictAdminException.class)
    public ResponseEntity<ProblemDetail> onDictAdminException(DictAdminException e) {
        HttpStatus status = switch (e.code()) {
            case GROUP_NOT_FOUND, ITEM_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case GROUP_CODE_DUPLICATE, ITEM_CODE_DUPLICATE, GROUP_HAS_ITEMS, ITEM_HAS_CHILDREN
                    -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Dict admin failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/dict/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
