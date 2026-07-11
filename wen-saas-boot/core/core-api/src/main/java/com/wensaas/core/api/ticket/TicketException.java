package com.wensaas.core.api.ticket;

/**
 * 工单领域异常。Web 层 {@code TicketRestExceptionAdvice} 映射为 ProblemDetail。
 */
public class TicketException extends RuntimeException {

    public enum Code {
        /** 工单不存在 / 不在当前可见范围。 */
        TICKET_NOT_FOUND,
        /** 无权操作该工单。 */
        FORBIDDEN,
        /** 当前状态不允许该操作（状态机约束）。 */
        ILLEGAL_STATE,
        /** 单附件超过 10MB。 */
        ATTACHMENT_TOO_LARGE,
        /** 单工单累计附件超过 50MB。 */
        ATTACHMENT_QUOTA_EXCEEDED
    }

    private final Code code;

    public TicketException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
