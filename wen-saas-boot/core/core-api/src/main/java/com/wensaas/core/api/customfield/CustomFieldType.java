package com.wensaas.core.api.customfield;

/**
 * 自定义字段类型。
 *
 * <p>持久化到 {@code sys_custom_field_def.field_type} 时使用 {@link #code()} 字符串（snake_case）。
 *
 * <p>类型一经创建禁止修改。
 */
public enum CustomFieldType {
    TEXT("text"),
    LONG_TEXT("long_text"),
    NUMBER("number"),
    DATE("date"),
    BOOL("bool"),
    /** 关联底座字典分组；定义时必须填 {@code refDictCode}。 */
    ENUM("enum"),
    /** 关联另一业务对象 ID（Long）；定义时必须填 {@code refObjectCode}；v1 不校存在性。 */
    REF("ref");

    private final String code;

    CustomFieldType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static CustomFieldType fromCode(String code) {
        for (CustomFieldType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知 CustomFieldType code: " + code);
    }
}
