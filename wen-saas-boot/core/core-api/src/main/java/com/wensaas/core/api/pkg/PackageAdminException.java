package com.wensaas.core.api.pkg;

/**
 * 套餐管理业务异常。
 *
 * <p>Web 层映射：{@code PackageAdminRestExceptionAdvice}。
 */
public class PackageAdminException extends RuntimeException {

    public enum Code {
        /** 套餐编码不存在（PUT/DELETE/状态流转/详情请求时）。 */
        PACKAGE_NOT_FOUND,
        /** 创建时编码已存在。 */
        CODE_CONFLICT,
        /** 操作被基础包保护规则拒绝（改 code / 下架 / 归档 / 删除）。 */
        BASIC_PACKAGE_PROTECTED,
        /** 状态机不允许的迁移（如 archived → active、active → archived 等）。 */
        INVALID_STATUS_TRANSITION,
        /** 上架后试图移除已包含的模块。 */
        MODULE_REMOVE_FORBIDDEN,
        /** 业务模块声明的预置套餐不可物理删除（重启会被 SPI 同步器重新插入），仅可下架/归档。 */
        DECLARED_PACKAGE_PROTECTED,
        /** 勾选的模块编码不存在或未上线。 */
        MODULE_NOT_FOUND
    }

    private final Code code;

    public PackageAdminException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
