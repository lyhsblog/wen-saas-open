package com.wensaas.core.api.imports;

import java.util.Map;

/**
 * 规范化后的一行数据。
 *
 * <p>{@code cells} 按字段 {@code key} 索引，值为去空白后的原始字符串（未做类型转换）。
 * 业务方 resolver 与底座校验引擎据此判定。
 *
 * @param rowNo    数据行序号（1-based，跳过空行后的累加号；错误定位 / 排序用）
 * @param excelRow Excel 物理行号（1-based，行号槽里看到的数字；跳空行不影响，便于用户回到原表定位）
 * @param cells    字段 key → 原始字符串值
 */
public record NormalizedRow(int rowNo, int excelRow, Map<String, String> cells) {

    public NormalizedRow {
        cells = cells == null ? Map.of() : Map.copyOf(cells);
    }

    /** 兼容旧调用（无 Excel 物理行号场景，如单测）：excelRow 回退为 rowNo。 */
    public NormalizedRow(int rowNo, Map<String, String> cells) {
        this(rowNo, rowNo, cells);
    }

    /** 取某字段值（不存在返回空串）。 */
    public String get(String key) {
        return cells.getOrDefault(key, "");
    }
}
