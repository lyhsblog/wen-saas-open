package com.wensaas.core.api.exports.spi;

/**
 * 导出列定义（xlsx 表头）。
 *
 * @param key   列标识（与 {@link ExportDataProvider} 写行时的取值顺序对齐，仅作可读性标注）
 * @param label 中文表头
 */
public record ExportColumn(String key, String label) {
}
