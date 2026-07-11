package com.wensaas.core.api.notification.inbox;

/**
 * 站内信收件箱业务异常。HTTP 适配由 web 层 {@code MessageInboxRestExceptionAdvice} 完成。
 */
public class MessageInboxException extends RuntimeException {

    public enum Code {
        /** 消息不存在 / 非本人（行级权限拒绝，统一不暴露存在性）。 */
        MESSAGE_NOT_FOUND
    }

    private final Code code;

    public MessageInboxException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
