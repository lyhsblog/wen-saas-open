package com.wensaas.core.api.task;

/**
 * 任务中心业务异常。HTTP 适配（400/404/409 等）由 web 层 ExceptionAdvice 完成。
 */
public class TaskException extends RuntimeException {

    public enum Code {
        /** SPI 未注册该 handlerCode。 */
        HANDLER_NOT_FOUND,
        /** 同租户同 handler 已有 RUNNING 任务。 */
        DUPLICATE_RUNNING_TASK,
        /** 任务不存在或不属于当前租户。 */
        TASK_NOT_FOUND,
        /** 当前状态不允许取消（仅 RUNNING 可取消）。 */
        CANCEL_NOT_ALLOWED,
        /** 任务状态不允许该操作（如 PENDING 已被消费）。 */
        INVALID_STATE
    }

    private final Code code;

    public TaskException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public TaskException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
