package com.wensaas.core.web.task;

import com.wensaas.core.api.task.TaskException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link TaskException} → {@link ProblemDetail}（与 {@code FileRestExceptionAdvice} 同模式）。
 *
 * <p>Code → HTTP 映射：
 * <ul>
 *   <li>404: TASK_NOT_FOUND（不存在或跨租户）</li>
 *   <li>409: CANCEL_NOT_ALLOWED / DUPLICATE_RUNNING_TASK / INVALID_STATE（状态冲突）</li>
 *   <li>400: HANDLER_NOT_FOUND（提交了未注册的 handler）</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class TaskRestExceptionAdvice {

    @ExceptionHandler(TaskException.class)
    public ResponseEntity<ProblemDetail> onTaskException(TaskException e) {
        HttpStatus status = switch (e.code()) {
            case TASK_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CANCEL_NOT_ALLOWED, DUPLICATE_RUNNING_TASK, INVALID_STATE -> HttpStatus.CONFLICT;
            case HANDLER_NOT_FOUND -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Task operation failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/task/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
