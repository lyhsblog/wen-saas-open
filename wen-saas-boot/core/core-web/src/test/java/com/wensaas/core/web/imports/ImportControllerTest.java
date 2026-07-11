package com.wensaas.core.web.imports;

import com.wensaas.core.api.imports.ImportException;
import com.wensaas.core.api.imports.ParseResult;
import com.wensaas.core.api.imports.PreviewFilter;
import com.wensaas.core.api.imports.PreviewRow;
import com.wensaas.core.api.imports.ImportService;
import com.wensaas.core.api.spi.def.FieldType;
import com.wensaas.core.api.spi.def.ImportEntityDef;
import com.wensaas.core.api.spi.def.ImportFieldDef;
import com.wensaas.core.api.spi.def.UniqueScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ImportControllerTest {

    private ImportService importService;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        importService = mock(ImportService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ImportController(importService))
                .setControllerAdvice(new ImportRestExceptionAdvice())
                .build();
    }

    private static ImportEntityDef entity() {
        return ImportEntityDef.of("t.member", "成员", "t.task.import.member", List.of(
                new ImportFieldDef("姓名", "name", FieldType.STRING, true, null, UniqueScope.NONE, null, "张三")));
    }

    private static ParseResult parseResult() {
        PreviewRow row = new PreviewRow(1, Map.of("name", "张三"), true, List.of());
        return new ParseResult("sid", "t.member", "客户.xlsx", 1, 1, 0, List.of(row));
    }

    @Test
    void entities_returnsList() throws Exception {
        when(importService.listEntities()).thenReturn(List.of(entity()));
        mockMvc.perform(get("/api/v1/imports/entities"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> assertThat(r.getResponse().getContentAsString())
                        .contains("t.member").contains("姓名"));
    }

    @Test
    void template_returnsXlsxAttachment() throws Exception {
        when(importService.generateTemplate("t.member")).thenReturn(new byte[]{1, 2, 3});
        mockMvc.perform(get("/api/v1/imports/t.member/template"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> assertThat(r.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                        .contains("attachment").contains("xlsx"))
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray()).hasSize(3));
    }

    @Test
    void parse_multipart_returnsResult() throws Exception {
        when(importService.parse(eq("t.member"), anyString(), any(InputStream.class)))
                .thenReturn(parseResult());
        MockMultipartFile file = new MockMultipartFile("file", "客户.xlsx",
                "application/octet-stream", new byte[]{9, 9});
        mockMvc.perform(multipart("/api/v1/imports/t.member/parse").file(file))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> assertThat(r.getResponse().getContentAsString())
                        .contains("sid").contains("\"total\":1"));
    }

    @Test
    void parse_fileCorrupt_returns422() throws Exception {
        when(importService.parse(anyString(), anyString(), any(InputStream.class)))
                .thenThrow(new ImportException(ImportException.Code.FILE_CORRUPT, "坏文件"));
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1});
        mockMvc.perform(multipart("/api/v1/imports/t.member/parse").file(file))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(422));
    }

    @Test
    void template_unknownEntity_returns404() throws Exception {
        when(importService.generateTemplate("nope"))
                .thenThrow(new ImportException(ImportException.Code.ENTITY_UNKNOWN, "未注册"));
        mockMvc.perform(get("/api/v1/imports/nope/template"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(404));
    }

    @Test
    void session_withFilter_returnsResult() throws Exception {
        when(importService.getSession("sid", PreviewFilter.ERROR)).thenReturn(parseResult());
        mockMvc.perform(get("/api/v1/imports/sessions/sid").param("filter", "error"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
    }

    @Test
    void session_invalidFilter_returns422() throws Exception {
        mockMvc.perform(get("/api/v1/imports/sessions/sid").param("filter", "bogus"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(422));
    }

    @Test
    void errors_returnsXlsx() throws Exception {
        when(importService.generateErrorReport("sid")).thenReturn(new byte[]{4, 5});
        mockMvc.perform(get("/api/v1/imports/sessions/sid/errors"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray()).hasSize(2));
    }

    @Test
    void confirm_returnsTaskId() throws Exception {
        when(importService.confirm("sid")).thenReturn(88L);
        mockMvc.perform(post("/api/v1/imports/sessions/sid/confirm"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> assertThat(r.getResponse().getContentAsString()).contains("88"));
    }

    @Test
    void confirm_hasErrors_returns409() throws Exception {
        when(importService.confirm("sid"))
                .thenThrow(new ImportException(ImportException.Code.HAS_ERRORS, "有错误行"));
        mockMvc.perform(post("/api/v1/imports/sessions/sid/confirm"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(409));
    }
}
