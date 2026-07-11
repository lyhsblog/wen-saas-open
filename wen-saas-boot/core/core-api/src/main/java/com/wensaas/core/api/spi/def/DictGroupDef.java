package com.wensaas.core.api.spi.def;

import java.util.List;

/**
 * 字典分组贡献项。
 *
 * <p>v1 起 record 扩为 6 字段；老调用方可继续用 {@link #of(String, String)} 工厂。
 *
 * @param code             字典分组编码，按 {@code {module}.{dict_name}} 命名（如 "crm.customer_source"），全局唯一
 * @param name             显示名
 * @param description      描述（可空）
 * @param supportHierarchy 是否支持层级（创建后不可改）
 * @param extraSchema      扩展字段 schema；null/空集合表示无扩展字段
 * @param presetItems      业务模块预置项；分组首次创建时一并 INSERT
 */
public record DictGroupDef(String code,
                            String name,
                            String description,
                            boolean supportHierarchy,
                            List<DictExtraFieldDef> extraSchema,
                            List<DictItemDef> presetItems) {

    public DictGroupDef {
        if (extraSchema == null) {
            extraSchema = List.of();
        }
        if (presetItems == null) {
            presetItems = List.of();
        }
    }

    /** 简单分组工厂：无 schema、无预置项、不支持层级。保留作为 SPI v1 兼容入口。 */
    public static DictGroupDef of(String code, String name) {
        return new DictGroupDef(code, name, null, false, List.of(), List.of());
    }
}
