package com.wensaas.core.impl.imports;

import com.wensaas.core.api.imports.ImportException;
import com.wensaas.core.api.imports.NormalizedRow;
import com.wensaas.core.api.imports.PreviewRow;
import com.wensaas.core.api.imports.RowError;
import com.wensaas.core.api.spi.def.ImportEntityDef;
import com.wensaas.core.api.spi.def.ImportFieldDef;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 导入框架的 xlsx 读写（Apache POI）。
 *
 * <p>三职责：按字段定义生成模板 / 解析上传文件为规范化行 / 生成错误明细。
 */
final class ImportExcelSupport {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private ImportExcelSupport() {
    }

    /** 按字段定义生成模板 xlsx：表头行 + 一行示例。 */
    static byte[] writeTemplate(ImportEntityDef def) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("导入模板");
            Font bold = wb.createFont();
            bold.setBold(true);
            var headerStyle = wb.createCellStyle();
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            Row example = sheet.createRow(1);
            List<ImportFieldDef> fields = def.fields();
            for (int i = 0; i < fields.size(); i++) {
                ImportFieldDef f = fields.get(i);
                Cell hc = header.createCell(i);
                hc.setCellValue(f.required() ? f.header() + "*" : f.header());
                hc.setCellStyle(headerStyle);
                if (f.example() != null && !f.example().isBlank()) {
                    example.createCell(i).setCellValue(f.example());
                }
                sheet.setColumnWidth(i, 20 * 256);
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException("生成导入模板失败", e);
        }
    }

    /**
     * 流式解析上传 xlsx 为规范化行（POI 事件 API / SAX，不展开整张表的对象模型）。表头按
     * {@link ImportFieldDef#header()} 严格匹配（去掉必填 {@code *} 后缀），只读第一张表。
     *
     * <p>相比整文件载入（{@code WorkbookFactory.create} 构建全量 DOM），SAX 逐行回调内存恒定，且行数
     * 超 {@link ImportEntityDef#rowLimit()} 时<b>提前中止</b>解析、不再读完后续行。仅支持 xlsx（OOXML）；
     * 无法以 OOXML 打开（如旧 .xls）按 {@code FILE_CORRUPT} 处理。
     *
     * @throws ImportException FILE_CORRUPT / TEMPLATE_MISMATCH / ROW_LIMIT_EXCEEDED / EMPTY_FILE
     */
    static List<NormalizedRow> readRows(ImportEntityDef def, InputStream in) {
        OPCPackage pkg;
        try {
            pkg = OPCPackage.open(in);
        } catch (Exception e) {
            throw new ImportException(ImportException.Code.FILE_CORRUPT, "文件无法解析，请确认为有效的 xlsx");
        }
        try {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings strings = reader.getSharedStringsTable();
            StylesTable styles = reader.getStylesTable();
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            if (!sheets.hasNext()) {
                throw new ImportException(ImportException.Code.EMPTY_FILE, "文件为空");
            }
            RowCollector collector = new RowCollector(def);
            try (InputStream sheetIn = sheets.next()) { // 只读第一张表
                XMLReader parser = XMLHelper.newXMLReader();
                parser.setContentHandler(new XSSFSheetXMLHandler(styles, strings, collector, FORMATTER, false));
                parser.parse(new InputSource(sheetIn));
            }
            return collector.finish();
        } catch (ImportException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportException(ImportException.Code.FILE_CORRUPT, "文件无法解析，请确认为有效的 xlsx");
        } finally {
            try {
                pkg.close();
            } catch (IOException ignored) {
                // 只读，关闭失败无副作用
            }
        }
    }

    /**
     * SAX 行回调：首行作表头（建 fieldKey→列号映射 + 校验缺列），其余行按字段取值、跳过全空行、
     * 计数超 {@code rowLimit} 即抛 ROW_LIMIT_EXCEEDED 提前中止。语义与原整表读取一致。
     */
    private static final class RowCollector implements SheetContentsHandler {

        private final ImportEntityDef def;
        private final List<NormalizedRow> rows = new ArrayList<>();
        private final TreeMap<Integer, String> current = new TreeMap<>(); // 列号 → 单元格值（按列升序，保 putIfAbsent 首列优先）
        private Map<String, Integer> fieldKeyToCol;
        private boolean headerDone = false;
        private int rowNo = 0;

        RowCollector(ImportEntityDef def) {
            this.def = def;
        }

        @Override
        public void startRow(int rowNum) {
            current.clear();
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (cellReference == null || formattedValue == null) {
                return;
            }
            String v = formattedValue.trim();
            if (!v.isEmpty()) {
                current.put((int) new CellReference(cellReference).getCol(), v); // 空值视同缺列，不放入
            }
        }

        @Override
        public void endRow(int rowNum) {
            if (!headerDone) {
                buildHeader();
                headerDone = true;
                return;
            }
            Map<String, String> cells = new LinkedHashMap<>();
            boolean allBlank = true;
            for (ImportFieldDef f : def.fields()) {
                String v = current.getOrDefault(fieldKeyToCol.get(f.key()), "");
                cells.put(f.key(), v);
                if (!v.isEmpty()) {
                    allBlank = false;
                }
            }
            if (allBlank) {
                return; // 跳过整行空白
            }
            rowNo++;
            if (rowNo > def.rowLimit()) {
                throw new ImportException(ImportException.Code.ROW_LIMIT_EXCEEDED,
                        "数据行数超过上限 " + def.rowLimit());
            }
            // rowNum 是 POI 0-based 物理行下标 → rowNum+1 即 Excel 真实行号（空行不计入 rowNo，物理行号仍准确）
            rows.add(new NormalizedRow(rowNo, rowNum + 1, cells));
        }

        private void buildHeader() {
            Map<String, Integer> headerToCol = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : current.entrySet()) { // 按列升序，重复表头首列优先
                headerToCol.putIfAbsent(stripRequiredMark(e.getValue()), e.getKey());
            }
            fieldKeyToCol = new LinkedHashMap<>();
            List<String> missing = new ArrayList<>();
            for (ImportFieldDef f : def.fields()) {
                Integer col = headerToCol.get(f.header());
                if (col == null) {
                    missing.add(f.header());
                } else {
                    fieldKeyToCol.put(f.key(), col);
                }
            }
            if (!missing.isEmpty()) {
                throw new ImportException(ImportException.Code.TEMPLATE_MISMATCH,
                        "表头与模板不符，缺少列：" + String.join("、", missing));
            }
        }

        List<NormalizedRow> finish() {
            if (!headerDone) {
                throw new ImportException(ImportException.Code.EMPTY_FILE, "文件为空");
            }
            if (rows.isEmpty()) {
                throw new ImportException(ImportException.Code.EMPTY_FILE, "文件无有效数据行");
            }
            return rows;
        }
    }

    /** 生成错误明细 xlsx：仅错误行，原数据列 + 末列「错误说明」。 */
    static byte[] writeErrorReport(ImportEntityDef def, List<PreviewRow> errorRows) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("错误明细");
            List<ImportFieldDef> fields = def.fields();
            Row header = sheet.createRow(0);
            for (int i = 0; i < fields.size(); i++) {
                header.createCell(i).setCellValue(fields.get(i).header());
                sheet.setColumnWidth(i, 20 * 256);
            }
            header.createCell(fields.size()).setCellValue("错误说明");
            sheet.setColumnWidth(fields.size(), 40 * 256);

            int r = 1;
            for (PreviewRow pr : errorRows) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < fields.size(); i++) {
                    row.createCell(i).setCellValue(pr.cells().getOrDefault(fields.get(i).key(), ""));
                }
                String detail = pr.errors().stream()
                        .map(ImportExcelSupport::describe)
                        .collect(Collectors.joining("；"));
                row.createCell(fields.size()).setCellValue(detail);
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException("生成错误明细失败", e);
        }
    }

    private static String describe(RowError e) {
        return e.field() == null ? e.message() : e.field() + ": " + e.message();
    }

    private static String stripRequiredMark(String header) {
        return header.endsWith("*") ? header.substring(0, header.length() - 1).trim() : header;
    }

    private static byte[] toBytes(Workbook wb) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos);
        return bos.toByteArray();
    }
}
