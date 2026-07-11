package com.wensaas.core.impl.imports.builtin;

import com.wensaas.core.api.auth.org.DeptAdminService;
import com.wensaas.core.api.auth.org.DeptAdminService.CreateDeptRequest;
import com.wensaas.core.api.auth.org.DeptAdminService.DeptView;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.impl.imports.ImportValidationEngine;
import com.wensaas.core.impl.spi.registry.ImportEntityRegistry;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeptImportTaskHandlerTest {

    private DeptAdminService deptService;
    private FileService fileService;
    private DeptImportTaskHandler handler;

    @BeforeEach
    void setUp() {
        ImportEntityRegistry registry = new ImportEntityRegistry();
        registry.register("base", SysImportEntities.dept());
        deptService = mock(DeptAdminService.class);
        fileService = mock(FileService.class);
        when(deptService.listTree()).thenReturn(List.of(
                new DeptView(10L, 0L, "华东大区", "", 0, 1, 0, List.of())));
        // create 返回新建部门（含分配 id），供执行器回填 name→id 解析同批次父子层级。
        java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(100L);
        when(deptService.create(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            CreateDeptRequest r = inv.getArgument(0);
            return new DeptView(seq.incrementAndGet(), r.parentId() == null ? 0L : r.parentId(),
                    r.name(), "", r.sortOrder() == null ? 0 : r.sortOrder(), 1, 0, List.of());
        });
        ImportValidationResolver resolver = new DeptImportValidationResolver(deptService);
        handler = new DeptImportTaskHandler(registry, new ImportValidationEngine(), fileService,
                List.of(resolver), null, deptService);
    }

    private static byte[] xlsx(List<List<String>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet();
            Row h = s.createRow(0);
            List<String> headers = List.of("部门名", "上级部门名", "排序");
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
    void execute_resolvesParentByName_topLevelNull_skipsUnknownParent() throws Exception {
        byte[] file = xlsx(List.of(
                List.of("销售部", "华东大区", "1"),     // 上级存在 → create parentId=10
                List.of("研发部", "", "2"),             // 顶级 → parentId=null
                List.of("孤儿部", "不存在大区", "3")));  // 上级不存在 → 预校验失败，跳过
        when(fileService.readContent("f1")).thenReturn(file);

        FakeTaskContext ctx = new FakeTaskContext(Map.of("fileId", "f1"));
        handler.execute(ctx);

        ArgumentCaptor<CreateDeptRequest> cap = ArgumentCaptor.forClass(CreateDeptRequest.class);
        verify(deptService, times(2)).create(cap.capture());
        assertThat(cap.getAllValues()).extracting(CreateDeptRequest::name)
                .containsExactlyInAnyOrder("销售部", "研发部");
        CreateDeptRequest sales = cap.getAllValues().stream()
                .filter(r -> r.name().equals("销售部")).findFirst().orElseThrow();
        assertThat(sales.parentId()).isEqualTo(10L);
        CreateDeptRequest rd = cap.getAllValues().stream()
                .filter(r -> r.name().equals("研发部")).findFirst().orElseThrow();
        assertThat(rd.parentId()).isNull();
        assertThat(ctx.result()).containsEntry("total", 2).containsEntry("success", 2)
                .containsEntry("failed", 0);
    }

    @Test
    void execute_sameFileParentChild_resolvesBatchParent() throws Exception {
        // 同文件「先建父再建子」（T12-02）：父部门「华北大区」在 DB 不存在，但在本文件更早行声明，
        // 预校验应承认、执行期回填后子部门解析到父 id。
        byte[] file = xlsx(List.of(
                List.of("华北大区", "", "1"),       // 顶级，create 返回 id=101
                List.of("北京分部", "华北大区", "2"))); // 上级=同批次先建的 华北大区
        when(fileService.readContent("f2")).thenReturn(file);

        FakeTaskContext ctx = new FakeTaskContext(Map.of("fileId", "f2"));
        handler.execute(ctx);

        ArgumentCaptor<CreateDeptRequest> cap = ArgumentCaptor.forClass(CreateDeptRequest.class);
        verify(deptService, times(2)).create(cap.capture());
        CreateDeptRequest parent = cap.getAllValues().stream()
                .filter(r -> r.name().equals("华北大区")).findFirst().orElseThrow();
        assertThat(parent.parentId()).isNull();
        CreateDeptRequest child = cap.getAllValues().stream()
                .filter(r -> r.name().equals("北京分部")).findFirst().orElseThrow();
        // 父在前一行创建并回填，子应解析到父返回的 id（非 null、非根）。
        assertThat(child.parentId()).isNotNull();
        assertThat(ctx.result()).containsEntry("total", 2).containsEntry("success", 2)
                .containsEntry("failed", 0);
    }
}
