package com.wensaas.core.api.spi.def;

import java.util.Objects;

/**
 * 字典分组的扩展字段 schema 项。
 *
 * @param fieldCode   分组内唯一字段编码，如 "code" / "level"
 * @param fieldName   显示名，如 "行政编码"
 * @param dataType    数据类型
 * @param required    字典项创建时是否强制填值
 * @param description 编辑界面辅助说明（可空）
 */
public record DictExtraFieldDef(String fieldCode,
                                 String fieldName,
                                 DictExtraDataType dataType,
                                 boolean required,
                                 String description) {

    public DictExtraFieldDef {
        requireNonBlank(fieldCode, "fieldCode");
        requireNonBlank(fieldName, "fieldName");
        Objects.requireNonNull(dataType, "dataType");
    }

    private static void requireNonBlank(String v, String name) {
        Objects.requireNonNull(v, name);
        if (v.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空白");
        }
    }
}
