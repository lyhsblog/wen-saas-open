package com.wensaas.core.api.file.center;

/**
 * 文件中心业务异常。HTTP 适配由 web 层 {@code FileCenterRestExceptionAdvice} 完成。
 */
public class FileCenterException extends RuntimeException {

    public enum Code {
        /** 文件夹层级超过 5 层。 */
        FOLDER_DEPTH_EXCEEDED,
        /** 同级目录下文件夹重名。 */
        FOLDER_NAME_DUP,
        /** 移动文件夹形成环（目标父在自身子树内 / 等于自身）。 */
        FOLDER_MOVE_CYCLE,
        /** 文件夹不存在 / 已删除 / 跨租户。 */
        FOLDER_NOT_FOUND,
        /** 文件不存在 / 已删除。 */
        FILE_NOT_FOUND,
        /** 超出租户容量配额。 */
        QUOTA_EXCEEDED,
        /** 还原 / 彻底删目标不在回收站。 */
        NOT_RECYCLED,
        /** 入参非法（如未知 space）。 */
        INVALID_ARGUMENT
    }

    private final Code code;

    public FileCenterException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
