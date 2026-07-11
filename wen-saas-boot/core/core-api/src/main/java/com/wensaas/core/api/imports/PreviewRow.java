package com.wensaas.core.api.imports;

import java.util.List;
import java.util.Map;

/**
 * 预览行。
 *
 * @param rowNo    数据行序号（1-based，跳过空行后的累加号；错误定位 / 排序用）
 * @param excelRow Excel 物理行号（1-based，便于用户回到原表定位；跳空行不影响）
 * @param cells    字段 key → 原始值
 * @param ok       是否通过（无任何 {@link #errors}）
 * @param errors   行内错误列表（通过时为空）
 */
public record PreviewRow(int rowNo, int excelRow, Map<String, String> cells, boolean ok, List<RowError> errors) {

    public PreviewRow {
        cells = cells == null ? Map.of() : Map.copyOf(cells);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** 兼容旧调用（无 Excel 物理行号场景，如单测）：excelRow 回退为 rowNo。 */
    public PreviewRow(int rowNo, Map<String, String> cells, boolean ok, List<RowError> errors) {
        this(rowNo, rowNo, cells, ok, errors);
    }
}
