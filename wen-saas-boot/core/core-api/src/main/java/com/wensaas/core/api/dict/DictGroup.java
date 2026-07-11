package com.wensaas.core.api.dict;

import com.wensaas.core.api.spi.def.DictExtraFieldDef;

import java.util.List;

/**
 * 字典分组对外查询契约。
 *
 * @param code             分组编码（租户内唯一）
 * @param name             显示名
 * @param description      描述
 * @param source           来源（SYSTEM / MODULE / CUSTOM）
 * @param supportHierarchy 是否支持层级
 * @param extraSchema      扩展字段 schema；空集合表示无扩展
 */
public record DictGroup(String code,
                         String name,
                         String description,
                         DictSource source,
                         boolean supportHierarchy,
                         List<DictExtraFieldDef> extraSchema) {

    public DictGroup {
        if (extraSchema == null) {
            extraSchema = List.of();
        }
    }
}
