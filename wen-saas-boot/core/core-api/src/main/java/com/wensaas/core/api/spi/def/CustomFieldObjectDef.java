package com.wensaas.core.api.spi.def;

/**
 * 自定义字段宿主对象贡献项。
 *
 * <p>业务模块在 {@code BusinessModuleDescriptor.customFieldObjects()} 中声明哪些业务对象支持自定义字段。
 * 底座据此知道：
 * <ul>
 *   <li>哪些 object_code 可被字段管理 UI 选中</li>
 *   <li>物理 {@code tableName} / {@code extraColumnName} —— 表达式索引 DDL 与未来值注入会用到</li>
 * </ul>
 *
 * <p>对象的<b>中文显示名</b>不再在此声明：统一以同 code 的
 * {@link com.wensaas.core.api.namesource.NameLookup#displayLabel()} 为权威单一来源（type label），
 * 字段管理 UI 的对象选择项由底座按 code 解析填充。避免「类型名」在 CF 声明与 NameLookup 两处重复、漂移。
 *
 * @param code             业务对象编码，按 {@code {module}.{object}} 命名（如 "crm.customer"），全局唯一
 * @param tableName        物理表名（小写、PostgreSQL identifier 合法），用于表达式索引 DDL
 * @param extraColumnName  jsonb 列名（小写，通常为 {@code extra}）
 * @param description      描述（可空）
 */
public record CustomFieldObjectDef(String code,
                                    String tableName,
                                    String extraColumnName,
                                    String description) {

    public CustomFieldObjectDef {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("CustomFieldObjectDef.code 不能为空");
        }
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("CustomFieldObjectDef.tableName 不能为空");
        }
        if (extraColumnName == null || extraColumnName.isBlank()) {
            throw new IllegalArgumentException("CustomFieldObjectDef.extraColumnName 不能为空");
        }
    }

    /** 简单工厂：无描述。 */
    public static CustomFieldObjectDef of(String code, String tableName, String extraColumnName) {
        return new CustomFieldObjectDef(code, tableName, extraColumnName, null);
    }
}
