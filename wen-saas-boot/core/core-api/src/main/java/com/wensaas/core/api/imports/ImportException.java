package com.wensaas.core.api.imports;

/**
 * 数据导入框架业务异常。HTTP 适配（404/409/410/422 等）由 web 层 ExceptionAdvice 完成。
 */
public class ImportException extends RuntimeException {

    public enum Code {
        /** 导入实体未注册。 */
        ENTITY_UNKNOWN,
        /** 文件损坏 / 非 xlsx / 不可解析。 */
        FILE_CORRUPT,
        /** 空文件（无数据行）。 */
        EMPTY_FILE,
        /** 表头与模板对不上。 */
        TEMPLATE_MISMATCH,
        /** 行数超过实体上限。 */
        ROW_LIMIT_EXCEEDED,
        /** 预览会话不存在或已过期。 */
        SESSION_NOT_FOUND,
        /** 仍有错误行，不允许确认导入。 */
        HAS_ERRORS
    }

    private final Code code;

    public ImportException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
