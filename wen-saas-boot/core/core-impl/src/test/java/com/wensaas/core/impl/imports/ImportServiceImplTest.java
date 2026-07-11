package com.wensaas.core.impl.imports;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.imports.ImportErrorCode;
import com.wensaas.core.api.imports.ImportException;
import com.wensaas.core.api.imports.ParseResult;
import com.wensaas.core.api.imports.PreviewFilter;
import com.wensaas.core.api.imports.PreviewRow;
import com.wensaas.core.api.imports.RowError;
import com.wensaas.core.api.spi.def.FieldType;
import com.wensaas.core.api.spi.def.ImportEntityDef;
import com.wensaas.core.api.spi.def.ImportFieldDef;
import com.wensaas.core.api.spi.def.UniqueScope;
import com.wensaas.core.api.task.SubmitRequest;
import com.wensaas.core.api.task.TaskService;
import com.wensaas.core.api.task.TaskType;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.spi.registry.ImportEntityRegistry;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportServiceImplTest {

    private static final String ENTITY = "t.member";
    private static final String HANDLER = "t.task.import.member";

    private ImportEntityRegistry registry;
    private ImportSessionStore store;
    private TaskService taskService;
    private FileService fileService;
    private ImportServiceImpl svc;

    @BeforeEach
    void setUp() {
        registry = new ImportEntityRegistry();
        registry.register("t", ImportEntityDef.of(ENTITY, "成员", HANDLER, List.of(
                new ImportFieldDef("姓名", "name", FieldType.STRING, true, null, UniqueScope.NONE, null, null),
                new ImportFieldDef("账号", "acct", FieldType.STRING, true, null, UniqueScope.IN_FILE, null, null))));
        store = mock(ImportSessionStore.class);
        taskService = mock(TaskService.class);
        fileService = mock(FileService.class);
        when(fileService.uploadDirect(any())).thenReturn(
                new FileUploadResult("f-src", "客户.xlsx", 10L, "x", FileBackendType.LOCAL));
        svc = new ImportServiceImpl(registry, new ImportValidationEngine(), store, taskService, fileService, List.of());
        TenantContext.set(9L);
        UserContext.set(new UserPrincipal(7L, 9L, "alice", "s", false, 11L, false, null));
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        UserContext.clear();
    }

    private static byte[] xlsx(List<String> headers, List<List<String>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet();
            Row h = s.createRow(0);
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

    private ParseResult parse(byte[] content) {
        return svc.parse(ENTITY, "客户.xlsx", new ByteArrayInputStream(content));
    }

    @Test
    void parse_allValid_uploadsAndSavesSplitPreview() throws Exception {
        byte[] file = xlsx(List.of("姓名", "账号"), List.of(List.of("张三", "a1"), List.of("李四", "a2")));

        ParseResult r = parse(file);

        assertThat(r.total()).isEqualTo(2);
        assertThat(r.failed()).isZero();
        assertThat(r.importable()).isTrue();
        verify(fileService).uploadDirect(any());
        ArgumentCaptor<ImportSession> cap = ArgumentCaptor.forClass(ImportSession.class);
        verify(store).save(cap.capture());
        assertThat(cap.getValue().fileId()).isEqualTo("f-src");
        assertThat(cap.getValue().passedPreview()).hasSize(2);
        assertThat(cap.getValue().errorPreview()).isEmpty();
    }

    @Test
    void parse_withRequiredMissing_marksFailed() throws Exception {
        byte[] file = xlsx(List.of("姓名", "账号"), List.of(List.of("张三", "a1"), List.of("", "a2")));
        ParseResult r = parse(file);
        assertThat(r.total()).isEqualTo(2);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.importable()).isFalse();
        ArgumentCaptor<ImportSession> cap = ArgumentCaptor.forClass(ImportSession.class);
        verify(store).save(cap.capture());
        assertThat(cap.getValue().errorPreview()).hasSize(1);
        assertThat(cap.getValue().passedPreview()).hasSize(1);
    }

    @Test
    void parse_unknownEntity_throws() {
        assertThatThrownBy(() -> svc.parse("x.unknown", "f.xlsx", new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(ImportException.class)
                .matches(e -> ((ImportException) e).code() == ImportException.Code.ENTITY_UNKNOWN);
    }

    @Test
    void parse_templateMismatch_throwsWithoutUploading() throws Exception {
        byte[] file = xlsx(List.of("姓名"), List.of(List.of("张三")));   // 缺「账号」列
        assertThatThrownBy(() -> parse(file))
                .isInstanceOf(ImportException.class)
                .matches(e -> ((ImportException) e).code() == ImportException.Code.TEMPLATE_MISMATCH);
        verify(fileService, never()).uploadDirect(any());   // 结构性错误不留孤儿文件
    }

    @Test
    void parse_emptyData_throws() throws Exception {
        byte[] file = xlsx(List.of("姓名", "账号"), List.of());
        assertThatThrownBy(() -> parse(file))
                .isInstanceOf(ImportException.class)
                .matches(e -> ((ImportException) e).code() == ImportException.Code.EMPTY_FILE);
    }

    @Test
    void getSession_errorFilter_returnsOnlyErrorPreview() {
        PreviewRow ok = new PreviewRow(1, Map.of("name", "张三"), true, List.of());
        PreviewRow bad = new PreviewRow(2, Map.of("name", ""), false,
                List.of(new RowError(2, "name", ImportErrorCode.REQUIRED_MISSING, "必填")));
        when(store.load("sid")).thenReturn(Optional.of(session("f-src", 2, 1, 1, List.of(bad), List.of(ok))));

        ParseResult r = svc.getSession("sid", PreviewFilter.ERROR);
        assertThat(r.preview()).extracting(PreviewRow::rowNo).containsExactly(2);
    }

    @Test
    void generateErrorReport_reParsesFileForAllErrors() throws Exception {
        byte[] file = xlsx(List.of("姓名", "账号"), List.of(List.of("", "a1")));  // 1 错误行
        when(store.load("sid")).thenReturn(Optional.of(session("f-src", 1, 0, 1, List.of(), List.of())));
        when(fileService.readContent("f-src")).thenReturn(file);

        byte[] report = svc.generateErrorReport("sid");
        assertThat(report).isNotEmpty();
        verify(fileService).readContent("f-src");
    }

    @Test
    void confirm_allPassed_submitsTaskWithFileId() {
        when(store.load("sid")).thenReturn(Optional.of(session("f-src", 2, 2, 0, List.of(), List.of())));
        when(taskService.submit(any(SubmitRequest.class))).thenReturn(77L);

        Long taskId = svc.confirm("sid");

        assertThat(taskId).isEqualTo(77L);
        ArgumentCaptor<SubmitRequest> cap = ArgumentCaptor.forClass(SubmitRequest.class);
        verify(taskService).submit(cap.capture());
        SubmitRequest req = cap.getValue();
        assertThat(req.type()).isEqualTo(TaskType.IMPORT);
        assertThat(req.handlerCode()).isEqualTo(HANDLER);
        assertThat(req.params())
                .containsEntry("fileId", "f-src")
                .containsEntry("sessionId", "sid")
                .containsEntry("entityCode", ENTITY);
    }

    @Test
    void confirm_hasErrors_throwsAndDoesNotSubmit() {
        when(store.load("sid")).thenReturn(Optional.of(session("f-src", 2, 1, 1, List.of(), List.of())));
        assertThatThrownBy(() -> svc.confirm("sid"))
                .isInstanceOf(ImportException.class)
                .matches(e -> ((ImportException) e).code() == ImportException.Code.HAS_ERRORS);
        verify(taskService, never()).submit(any());
    }

    @Test
    void getSession_missing_throwsNotFound() {
        when(store.load("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.getSession("nope", PreviewFilter.ALL))
                .isInstanceOf(ImportException.class)
                .matches(e -> ((ImportException) e).code() == ImportException.Code.SESSION_NOT_FOUND);
    }

    @Test
    void getSession_crossTenant_throwsNotFound() {
        ImportSession other = new ImportSession("sid", 999L, 7L, ENTITY, "成员", HANDLER, "f.xlsx",
                "f-src", 1, 1, 0, List.of(), List.of());
        when(store.load("sid")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> svc.getSession("sid", PreviewFilter.ALL))
                .isInstanceOf(ImportException.class)
                .matches(e -> ((ImportException) e).code() == ImportException.Code.SESSION_NOT_FOUND);
    }

    @Test
    void importSession_fastjson2RoundTrips() {
        PreviewRow bad = new PreviewRow(2, Map.of("name", ""), false,
                List.of(new RowError(2, "name", ImportErrorCode.REQUIRED_MISSING, "必填项不能为空")));
        ImportSession s = session("f-src", 2, 1, 1, List.of(bad),
                List.of(new PreviewRow(1, Map.of("name", "张三"), true, List.of())));

        ImportSession back = JSON.parseObject(JSON.toJSONString(s), ImportSession.class);

        assertThat(back.fileId()).isEqualTo("f-src");
        assertThat(back.errorPreview().get(0).errors().get(0).code())
                .isEqualTo(ImportErrorCode.REQUIRED_MISSING);
        assertThat(back.failed()).isEqualTo(1);
    }

    private static ImportSession session(String fileId, int total, int passed, int failed,
                                         List<PreviewRow> errorPreview, List<PreviewRow> passedPreview) {
        return new ImportSession("sid", 9L, 7L, ENTITY, "成员", HANDLER, "客户.xlsx", fileId,
                total, passed, failed, errorPreview, passedPreview);
    }
}
