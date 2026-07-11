package com.wensaas.core.impl.exports;

import com.wensaas.core.api.exports.spi.ExportColumn;
import com.wensaas.core.api.exports.spi.ExportSink;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 流式 xlsx 写入器（SXSSF，内存窗口 200 行）：实现 {@link ExportSink} 供
 * {@code ExportDataProvider} 逐行推数，行数触达 {@link #maxRows} 或任务被取消时
 * {@link #stopped()} 翻 true、后续行静默丢弃。
 *
 * <p>表头风格与导入模板一致（加粗 + 冻结首行）。非线程安全，单任务单实例。
 */
final class ExportXlsxWriter implements ExportSink, AutoCloseable {

    private final SXSSFWorkbook wb;
    private final Sheet sheet;
    private final BooleanSupplier cancelled;
    private final int maxRows;
    private int nextRow = 1;
    private boolean truncated;

    ExportXlsxWriter(List<ExportColumn> columns, BooleanSupplier cancelled, int maxRows) {
        this.cancelled = cancelled;
        this.maxRows = maxRows;
        this.wb = new SXSSFWorkbook(200);
        this.sheet = wb.createSheet("导出数据");
        Font bold = wb.createFont();
        bold.setBold(true);
        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFont(bold);
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell hc = header.createCell(i);
            hc.setCellValue(columns.get(i).label());
            hc.setCellStyle(headerStyle);
        }
        sheet.createFreezePane(0, 1);
    }

    @Override
    public void row(List<String> cells) {
        if (stopped()) {
            return;
        }
        if (rowsWritten() >= maxRows) {
            truncated = true;
            return;
        }
        Row row = sheet.createRow(nextRow++);
        for (int i = 0; i < cells.size(); i++) {
            String v = cells.get(i);
            if (v != null && !v.isEmpty()) {
                row.createCell(i).setCellValue(v);
            }
        }
    }

    @Override
    public boolean stopped() {
        return truncated || cancelled.getAsBoolean();
    }

    long rowsWritten() {
        return nextRow - 1L;
    }

    boolean truncated() {
        return truncated;
    }

    byte[] finish() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            wb.write(bos);
        } catch (IOException e) {
            throw new UncheckedIOException("写导出 xlsx 失败", e);
        }
        return bos.toByteArray();
    }

    @Override
    public void close() {
        wb.dispose();
        try {
            wb.close();
        } catch (IOException ignored) {
            // SXSSF 关闭失败不影响已生成内容
        }
    }
}
