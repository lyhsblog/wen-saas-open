package com.wensaas.core.api.customfield;

/**
 * 自定义字段框架业务异常。HTTP 适配（400/409 等）由 web 层 ExceptionAdvice 完成。
 */
public class CustomFieldException extends RuntimeException {

    public enum Code {
        /** 业务对象未注册（业务模块未声明 CustomFieldObjectDef）。 */
        OBJECT_NOT_REGISTERED,
        /** 字段不存在或已被软删。 */
        FIELD_NOT_FOUND,
        /** 同 (tenant, object, code) 已存在未软删字段。 */
        DUPLICATE_FIELD_CODE,
        /** 字段额度耗尽。 */
        FIELD_QUOTA_EXCEEDED,
        /** 索引额度耗尽。 */
        INDEX_QUOTA_EXCEEDED,
        /** 改字段类型被禁止（类型一经创建不可修改）。 */
        FIELD_TYPE_IMMUTABLE,
        /** 字段值不符合类型 / 必填 / 长度约束。 */
        INVALID_VALUE,
        /** ENUM 字段值不在引用字典分组中。 */
        ENUM_VALUE_NOT_IN_DICT,
        /** ENUM 字段引用的字典分组不存在。 */
        DICT_GROUP_NOT_FOUND,
        /** ENUM 类型缺 refDictCode / REF 类型缺 refObjectCode。 */
        MISSING_REF_CODE,
        /** v2: fieldCode 不符合 {@code cf_} 前缀强制规则。 */
        INVALID_FIELD_CODE_PREFIX,
        /** v2: 查询拦截器拒绝未索引化自定义字段的 WHERE / ORDER BY / GROUP BY / HAVING 引用。 */
        UNINDEXED_QUERY_NOT_ALLOWED
    }

    private final Code code;

    public CustomFieldException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public CustomFieldException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
