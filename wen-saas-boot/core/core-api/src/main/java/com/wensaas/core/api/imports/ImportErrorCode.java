package com.wensaas.core.api.imports;

/**
 * 行级校验错误类型。
 */
public enum ImportErrorCode {
    /** 必填字段为空。 */
    REQUIRED_MISSING,
    /** 数据类型不匹配（数字列填了文字等）。 */
    TYPE_INVALID,
    /** 格式不符（手机号 / 邮箱 / 正则）。 */
    FORMAT_INVALID,
    /** 文件内自身重复。 */
    DUPLICATE_IN_FILE,
    /** 与已有数据冲突（账号已存在等）。 */
    DUPLICATE_IN_DB,
    /** 关联对象不存在（部门不存在等）。 */
    REFERENCE_NOT_FOUND,
    /** 业务方自定义校验失败。 */
    BUSINESS_INVALID
}
