package com.wensaas.core.impl.imports.builtin;

import com.wensaas.core.api.auth.org.DeptAdminService;
import com.wensaas.core.api.auth.org.DeptAdminService.DeptView;
import com.wensaas.core.api.auth.permission.MemberAdminService;
import com.wensaas.core.api.auth.permission.MemberAdminService.CreateMemberCommand;
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

class MemberImportTaskHandlerTest {

    private MemberAdminService memberService;
    private DeptAdminService deptService;
    private FileService fileService;
    private MemberImportTaskHandler handler;

    @BeforeEach
    void setUp() {
        ImportEntityRegistry registry = new ImportEntityRegistry();
        registry.register("base", SysImportEntities.member());
        memberService = mock(MemberAdminService.class);
        deptService = mock(DeptAdminService.class);
        fileService = mock(FileService.class);
        when(deptService.listTree()).thenReturn(List.of(
                new DeptView(20L, 0L, "销售部", "", 0, 1, 0, List.of())));
        ImportValidationResolver resolver = new MemberImportValidationResolver(deptService);
        handler = new MemberImportTaskHandler(registry, new ImportValidationEngine(), fileService,
                List.of(resolver), null, memberService);
    }

    private static byte[] xlsx(List<List<String>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet();
            Row h = s.createRow(0);
            List<String> headers = List.of("账号", "姓名", "手机", "邮箱", "部门");
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
    void execute_resolvesDeptByName_optionalBlanksToNull_skipsUnknownDept() throws Exception {
        byte[] file = xlsx(List.of(
                List.of("zhangsan", "张三", "13800138000", "z@a.com", "销售部"),  // 全字段
                List.of("lisi", "李四", "", "", "销售部"),                          // 手机/邮箱可空
                List.of("wangwu", "王五", "13900139000", "", "未知部门")));         // 部门不存在 → 跳过
        when(fileService.readContent("f1")).thenReturn(file);

        FakeTaskContext ctx = new FakeTaskContext(Map.of("fileId", "f1"));
        handler.execute(ctx);

        ArgumentCaptor<CreateMemberCommand> cap = ArgumentCaptor.forClass(CreateMemberCommand.class);
        verify(memberService, times(2)).createMember(cap.capture());
        assertThat(cap.getAllValues()).extracting(CreateMemberCommand::username)
                .containsExactlyInAnyOrder("zhangsan", "lisi");
        CreateMemberCommand zhangsan = cap.getAllValues().stream()
                .filter(c -> c.username().equals("zhangsan")).findFirst().orElseThrow();
        assertThat(zhangsan.mainDeptId()).isEqualTo(20L);
        assertThat(zhangsan.mobile()).isEqualTo("13800138000");
        assertThat(zhangsan.email()).isEqualTo("z@a.com");
        CreateMemberCommand lisi = cap.getAllValues().stream()
                .filter(c -> c.username().equals("lisi")).findFirst().orElseThrow();
        assertThat(lisi.mobile()).isNull();
        assertThat(lisi.email()).isNull();
        assertThat(ctx.result()).containsEntry("total", 2).containsEntry("success", 2);
    }
}
