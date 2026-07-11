package com.wensaas.core.web.common;

import com.wensaas.core.web.common.BaseRestExceptionAdvice.FieldError;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.ArrayList;
import java.util.List;

/**
 * 框架级请求异常 → {@link ProblemDetail}：
 * <ul>
 *   <li>{@link MethodArgumentNotValidException}（@Valid body 校验失败）→ 422 + {@code errors[]}</li>
 *   <li>{@link ConstraintViolationException}（@Validated path/query 校验失败）→ 422 + {@code errors[]}</li>
 *   <li>{@link HttpMessageNotReadableException}（请求 body 不可解析）→ 400</li>
 *   <li>{@link MaxUploadSizeExceededException} → 413</li>
 * </ul>
 *
 * <p><b>必须以高于 Spring 内置 {@code ProblemDetailsExceptionHandler} 的优先级注册。</b>
 * 该内置 Advice（{@code spring.mvc.problemdetails.enabled=true} 时激活，位于
 * {@code LOWEST_PRECEDENCE}）同样处理 {@code MethodArgumentNotValid / HttpMessageNotReadable /
 * MaxUploadSizeExceeded}，平级时会抢先返回默认 {@code 400 "Invalid request content."}，丢掉
 * {@code errors[]}（前端表单字段级高亮失效）。本类用 {@code @Order(0)} 与各模块专用 Advice 同级：
 * 这些都是框架异常类型、模块 Advice 不处理，互不冲突；且本类不设 {@code Exception} 兜底，故域异常
 * 仍下沉到模块 Advice / {@link BaseRestExceptionAdvice}。
 */
@RestControllerAdvice
@Order(0)
public class ValidationRestExceptionAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> onValidation(MethodArgumentNotValidException e) {
        List<FieldError> errors = new ArrayList<>();
        e.getBindingResult().getFieldErrors().forEach(fe ->
                errors.add(new FieldError(fe.getField(), fe.getDefaultMessage(), fe.getCode())));
        return BaseRestExceptionAdvice.problem(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED",
                "Validation failed", "请求字段校验失败", "validation", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> onConstraint(ConstraintViolationException e) {
        List<FieldError> errors = new ArrayList<>();
        e.getConstraintViolations().forEach(cv -> {
            String path = cv.getPropertyPath().toString();
            errors.add(new FieldError(path, cv.getMessage(),
                    cv.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()));
        });
        return BaseRestExceptionAdvice.problem(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED",
                "Validation failed", "请求参数校验失败", "validation", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> onBadBody(HttpMessageNotReadableException e) {
        return BaseRestExceptionAdvice.problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY",
                "Malformed request body", "请求体格式错误或不可读", "bad-request", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> onUploadSize(MaxUploadSizeExceededException e) {
        return BaseRestExceptionAdvice.problem(HttpStatus.PAYLOAD_TOO_LARGE, "SIZE_EXCEEDED",
                "Upload too large", e.getMessage(), "size", null);
    }
}
