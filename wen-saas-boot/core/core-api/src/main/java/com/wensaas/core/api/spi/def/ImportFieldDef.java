package com.wensaas.core.api.spi.def;

/**
 * 导入实体的单个字段定义。
 *
 * <p>模板生成、解析、基础校验全部由本声明驱动；严格按表头匹配，不做自动列映射。
 *
 * @param header    模板中文表头（解析时按此严格匹配列）
 * @param key       内部字段名（规范化后的键，handler 取数据用）
 * @param type      数据类型
 * @param required  是否必填
 * @param format    格式规则：内置 token {@code PHONE} / {@code EMAIL}，日期模式（如 {@code yyyy-MM-dd}），
 *                  或自定义正则；{@code null} 表示不校验格式
 * @param unique    唯一性范围（IN_DB/BOTH 由引擎批量回调 resolver 取数口查重）
 * @param reference 关联存在性校验（引擎批量回调 resolver 取数口匹配）；{@code null} 表示无关联
 * @param example   模板预填示例值（帮助客户填写）；可空
 */
public record ImportFieldDef(
        String header,
        String key,
        FieldType type,
        boolean required,
        String format,
        UniqueScope unique,
        ReferenceDef reference,
        String example) {

    public ImportFieldDef {
        if (header == null || header.isBlank()) {
            throw new IllegalArgumentException("ImportFieldDef.header 不可为空");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("ImportFieldDef.key 不可为空");
        }
        if (type == null) {
            type = FieldType.STRING;
        }
        if (unique == null) {
            unique = UniqueScope.NONE;
        }
    }

    /** 仅含表头 + 键 + 类型 + 必填的简易构造。 */
    public static ImportFieldDef of(String header, String key, FieldType type, boolean required) {
        return new ImportFieldDef(header, key, type, required, null, UniqueScope.NONE, null, null);
    }
}
