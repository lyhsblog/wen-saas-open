package com.wensaas.core.web.customfield;

import com.wensaas.core.api.customfield.CustomFieldException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link CustomFieldException} → {@link ProblemDetail}。在本 advice 之前，自定义字段业务异常一律落到
 * {@code BaseRestExceptionAdvice} 兜底 → 500；这里按 code 映射成正确的 4xx。
 *
 * <p>HTTP 映射：
 * <ul>
 *   <li>404: FIELD_NOT_FOUND</li>
 *   <li>409: DUPLICATE_FIELD_CODE / FIELD_QUOTA_EXCEEDED / INDEX_QUOTA_EXCEEDED</li>
 *   <li>400: 其余（对象未注册 / 类型不可改 / 值非法 / 缺 ref code / 关联对象未注册 / fieldCode 前缀 /
 *        未索引化查询被拒 等）</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class CustomFieldRestExceptionAdvice {

    @ExceptionHandler(CustomFieldException.class)
    public ResponseEntity<ProblemDetail> onCustomFieldException(CustomFieldException e) {
        HttpStatus status = switch (e.code()) {
            case FIELD_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_FIELD_CODE, FIELD_QUOTA_EXCEEDED, INDEX_QUOTA_EXCEEDED
                    -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Custom field operation failed");
        pd.setType(URI.create(
                "https://wen-saas-boot/errors/custom-field/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
