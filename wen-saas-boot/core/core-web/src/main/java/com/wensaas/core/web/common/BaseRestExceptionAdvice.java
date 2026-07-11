package com.wensaas.core.web.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 全局兜底异常 → {@link ProblemDetail}。
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE)} 让各模块的专用 Advice（{@code @Order(0)}）
 * 与框架请求异常 Advice（{@link ValidationRestExceptionAdvice}）优先匹配，本类只兜底
 * 未被任何 Advice 处理的 {@link Exception} → 500（仅记 stack，不暴露给客户端）。
 *
 * <p>框架级请求异常（@Valid 校验失败 / 坏 body / 超限）已上移到
 * {@link ValidationRestExceptionAdvice}，以高于 Spring 内置 {@code ProblemDetailsExceptionHandler}
 * 的优先级注册，避免被其抢先处理成默认 400 "Invalid request content."。
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class BaseRestExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(BaseRestExceptionAdvice.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> onFallback(Exception e) {
        log.error("[BaseAdvice] unhandled exception", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Internal server error", "服务器内部错误，请稍后重试", "internal", null);
    }

    /** 构造统一 {@link ProblemDetail}（带 {@code code} 与可选 {@code errors[]}）；供本包各 Advice 复用。 */
    static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String title,
                                                 String detail, String type, List<FieldError> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("https://wen-saas-boot/errors/" + type));
        pd.setProperty("code", code);
        if (errors != null) {
            pd.setProperty("errors", errors);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class FieldError {
        private String field;
        private String message;
        private String code;
    }
}
