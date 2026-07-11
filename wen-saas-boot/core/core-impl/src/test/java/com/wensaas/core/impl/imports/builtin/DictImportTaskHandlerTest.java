package com.wensaas.core.impl.imports.builtin;

import com.wensaas.core.api.dict.DictAdminException;
import com.wensaas.core.api.dict.DictAdminService;
import com.wensaas.core.api.dict.DictGroup;
import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.api.task.TaskContext;
import com.wensaas.core.impl.imports.ImportValidationEngine;
import com.wensaas.core.impl.spi.registry.ImportEntityRegistry;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictImportTaskHandlerTest {

    private DictAdminService dictService;
    private FileService fileService;
    private DictImportTaskHandler handler;

    @BeforeEach
    void setUp() {
        ImportEntityRegistry registry = new ImportEntityRegistry();
        registry.register("base", SysImportEntities.dict());
        dictService = mock(DictAdminService.class);
        fileService = mock(FileService.class);
        DictGroup group = mock(DictGroup.class);
        when(group.code()).thenReturn("user_status");
        when(dictService.listGroups()).thenReturn(List.of(group));
        ImportValidationResolver resolver = new DictImportValidationResolver(dictService);
        handler = new DictImportTaskHandler(registry, new ImportValidationEngine(), fileService,
                List.of(resolver), null, dictService);
    }

    private static byte[] xlsx(List<List<String>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet();
            Row h = s.createRow(0);
            List<String> headers = List.of("分组编码", "项编码", "显示名", "排序");
            for (int i = 0; i < headers.size(); i++) {
                h.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = s.createRow(r + 1);
                List<String> cols = rows.get(r);
                for (int c = 0; c < cols.size(); c++) {
                    row.createCell(c).setCellValue(cols.get(c));
                }
            }
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    void execute_writesOkRows_skipsUnknownGroup_collectsFailures() throws Exception {
        byte[] file = xlsx(List.of(
                List.of("user_status", "active", "启用", "1"),     // ok → createItem
                List.of("user_status", "locked", "锁定", "2"),     // ok → createItem 抛重复 → 失败
                List.of("missing_grp", "x", "y", "3")));            // 分组不存在 → 预校验失败，不执行
        when(fileService.readContent("f1")).thenReturn(file);
        when(fileService.uploadDirect(any())).thenReturn(
                new FileUploadResult("f-result", "r.xlsx", 1L, "x", FileBackendType.LOCAL));
        when(dictService.createItem(eq("user_status"), argThat(r -> "locked".equals(r.code()))))
                .thenThrow(new DictAdminException(DictAdminException.Code.ITEM_CODE_DUPLICATE, "项编码已存在"));

        FakeContext ctx = new FakeContext(Map.of("fileId", "f1"));
        handler.execute(ctx);

        // 只有 2 个 ok 行进入执行（第三行分组不存在被预校验拦下）
        verify(dictService, times(2)).createItem(eq("user_status"), any());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) ctx.result.get();
        assertThat(result).containsEntry("total", 2).containsEntry("success", 1).containsEntry("failed", 1);
        assertThat(result).containsEntry("fileId", "f-result");
        assertThat(ctx.success.get()).isEqualTo(1);
        assertThat(ctx.failed.get()).isEqualTo(1);
        assertThat(ctx.total.get()).isEqualTo(2);  // 回写总条数（仅 ok 行）→ sys_task.total_count / 进度分母
        verify(fileService).uploadDirect(any());   // 失败明细落文件中心
    }

    @Test
    void execute_allValid_noResultFile() throws Exception {
        byte[] file = xlsx(List.of(List.of("user_status", "active", "启用", "1")));
        when(fileService.readContent("f1")).thenReturn(file);

        FakeContext ctx = new FakeContext(Map.of("fileId", "f1"));
        handler.execute(ctx);

        verify(dictService).createItem(eq("user_status"), any());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) ctx.result.get();
        assertThat(result).containsEntry("total", 1).containsEntry("success", 1).containsEntry("failed", 0);
        assertThat(result).doesNotContainKey("fileId");
        assertThat(ctx.total.get()).isEqualTo(1);  // 回写总条数 → sys_task.total_count / 进度分母
    }

    /** 最小 TaskContext 桩，捕获计数与结果。 */
    private static final class FakeContext implements TaskContext {
        private final Map<String, Object> params;
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final AtomicInteger total = new AtomicInteger(-1);
        final AtomicReference<Object> result = new AtomicReference<>();

        FakeContext(Map<String, Object> params) {
            this.params = params;
        }

        @Override public Long taskId() { return 1L; }
        @Override public Long tenantId() { return 9L; }
        @Override public Map<String, Object> params() { return params; }
        @Override public void setTotal(int total) { this.total.set(total); }
        @Override public void updateProgress(int percent, int processed) { }
        @Override public void recordSuccess(int delta) { success.addAndGet(delta); }
        @Override public void recordFailure(int delta) { failed.addAndGet(delta); }
        @Override public boolean isCancelled() { return false; }
        @Override public void setResult(Object resultData) { result.set(resultData); }
    }
}
