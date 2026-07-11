package com.wensaas.core.web.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileMetadata;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.file.SignedUploadUrl;
import com.wensaas.core.web.file.dto.SignedUploadRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class FileControllerTest {

    private FileService service;
    private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() {
        service = mock(FileService.class);
        FileController controller = new FileController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new FileRestExceptionAdvice())
                .build();
    }

    @Test
    void uploadUrl_happyPath() throws Exception {
        SignedUploadUrl stub = new SignedUploadUrl("f1", "https://oss/put", "PUT",
                Map.of("Content-Type", "text/plain"), Instant.now().plusSeconds(300));
        when(service.requestSignedUpload(any())).thenReturn(stub);

        SignedUploadRequestDto dto = new SignedUploadRequestDto(
                "a.txt", "text/plain", 10L, FileVisibility.FILE_CENTER,
                null, null, null, null);
        String body = json.writeValueAsString(dto);

        mockMvc.perform(post("/api/v1/files/upload-url")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> assertThat(r.getResponse().getContentAsString()).contains("f1"));
    }

    @Test
    void uploadUrl_invalidPayload_returns400() throws Exception {
        // sizeBytes 负数 + visibility null → @Valid 拦截
        String body = "{\"fileName\":\"\",\"sizeBytes\":-1}";
        mockMvc.perform(post("/api/v1/files/upload-url")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(400));
    }

    @Test
    void confirm_happyPath() throws Exception {
        when(service.confirmUpload("f1")).thenReturn(
                new FileUploadResult("f1", "a.txt", 10L, "text/plain", FileBackendType.LOCAL));
        mockMvc.perform(post("/api/v1/files/f1/confirm"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
        verify(service).confirmUpload("f1");
    }

    @Test
    void url_returnsCachedSignedUrl() throws Exception {
        when(service.signedDownloadUrl(eq("f1"), any(Duration.class)))
                .thenReturn("https://oss/get?sig=abc");

        mockMvc.perform(get("/api/v1/files/f1/url"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> assertThat(r.getResponse().getContentAsString())
                        .contains("https://oss/get?sig=abc"));
    }

    @Test
    void url_notFound_returns404() throws Exception {
        when(service.signedDownloadUrl(anyString(), any(Duration.class)))
                .thenThrow(new FileException(FileException.Code.FILE_NOT_FOUND, "缺失"));
        mockMvc.perform(get("/api/v1/files/f-missing/url"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(404));
    }

    @Test
    void url_forbidden_returns403() throws Exception {
        when(service.signedDownloadUrl(anyString(), any(Duration.class)))
                .thenThrow(new FileException(FileException.Code.DOWNLOAD_FORBIDDEN, "禁止"));
        mockMvc.perform(get("/api/v1/files/f-no/url"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(403));
    }

    @Test
    void metadata_happyPath() throws Exception {
        when(service.metadata("f1")).thenReturn(
                new FileMetadata("f1", 9601L, "a.txt", "text/plain", 10L,
                        FileVisibility.FILE_CENTER, null, null, null, null,
                        FileBackendType.LOCAL, null, "tenants/9601/202605/f1",
                        "active", 1L, LocalDateTime.now(), null));
        mockMvc.perform(get("/api/v1/files/f1"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
    }

    @Test
    void delete_returns204() throws Exception {
        // 删除前会取文件名登记审计对象（04a8722：AuditObjects.add 走 metadata().fileName()），需 stub
        when(service.metadata("f1")).thenReturn(
                new FileMetadata("f1", 9601L, "a.txt", "text/plain", 10L,
                        FileVisibility.FILE_CENTER, null, null, null, null,
                        FileBackendType.LOCAL, null, "tenants/9601/202605/f1",
                        "active", 1L, LocalDateTime.now(), null));
        mockMvc.perform(delete("/api/v1/files/f1"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(204));
        verify(service).delete("f1");
    }

    @Test
    void listByOwner_returnsArray() throws Exception {
        when(service.listByOwner("crm", "Customer", 42L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/files")
                        .param("ownerModule", "crm")
                        .param("ownerRefType", "Customer")
                        .param("ownerRefId", "42"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
    }

    @Test
    void sizeExceeded_returns413() throws Exception {
        when(service.confirmUpload("f-big"))
                .thenThrow(new FileException(FileException.Code.SIZE_EXCEEDED, "too big"));
        mockMvc.perform(post("/api/v1/files/f-big/confirm"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(413));
    }
}
