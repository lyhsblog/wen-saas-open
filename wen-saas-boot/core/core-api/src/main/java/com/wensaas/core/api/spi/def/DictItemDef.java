package com.wensaas.core.api.spi.def;

import java.util.Map;
import java.util.Objects;

/**
 * 业务模块预置字典项声明。仅用于业务模块 {@code DictGroupDef.presetItems} 列表；
 * 启动期由 {@code DictSpiSynchronizer} 在分组首次创建时一并 INSERT，重启不重复写入。
 *
 * @param code       项编码，分组内唯一
 * @param name       显示名
 * @param parentCode 父项编码；仅 {@code supportHierarchy=true} 时可填，null 表示顶级
 * @param sortOrder  同级排序权重，null 视为 0
 * @param remark     备注（可空）
 * @param extra      按分组 {@code extraSchema} 必填项校验
 */
public record DictItemDef(String code,
                           String name,
                           String parentCode,
                           Integer sortOrder,
                           String remark,
                           Map<String, Object> extra) {

    public DictItemDef {
        requireNonBlank(code, "code");
        requireNonBlank(name, "name");
        if (extra == null) {
            extra = Map.of();
        }
    }

    public static DictItemDef of(String code, String name) {
        return new DictItemDef(code, name, null, null, null, Map.of());
    }

    private static void requireNonBlank(String v, String name) {
        Objects.requireNonNull(v, name);
        if (v.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空白");
        }
    }
}
