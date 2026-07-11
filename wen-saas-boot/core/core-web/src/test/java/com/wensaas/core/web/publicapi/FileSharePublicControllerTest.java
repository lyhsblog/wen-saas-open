package com.wensaas.core.web.publicapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.share.FileShareConsumeResult;
import com.wensaas.core.api.file.share.FileShareInfo;
import com.wensaas.core.api.file.share.FileShareService;
import com.wensaas.core.web.file.FileRestExceptionAdvice;
import com.wensaas.core.web.file.dto.ConsumeShareRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class FileSharePublicControllerTest {

    private FileShareService service;
    private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() {
        service = mock(FileShareService.class);
        FileSharePublicController controller = new FileSharePublicController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new FileRestExceptionAdvice())
                .build();
    }

    @Test
    void get_happyPath_returnsTrimmedView() throws Exception {
        when(service.getPublicShare("tok-1")).thenReturn(stubInfo("tok-1", false));
        mockMvc.perform(get("/api/v1/public/shares/tok-1"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("tok-1").contains("hello.txt");
                    // 公开视图不应泄露内部字段
                    assertThat(body).doesNotContain("createdBy").doesNotContain("downloadCount");
                });
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(service.getPublicShare("tok-x"))
                .thenThrow(new FileException(FileException.Code.SHARE_NOT_FOUND, "x"));
        mockMvc.perform(get("/api/v1/public/shares/tok-x"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(404));
    }

    @Test
    void get_expired_returns410() throws Exception {
        when(service.getPublicShare("tok-e"))
                .thenThrow(new FileException(FileException.Code.SHARE_EXPIRED, "x"));
        mockMvc.perform(get("/api/v1/public/shares/tok-e"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(410));
    }

    @Test
    void download_happyPath_returnsSignedUrl() throws Exception {
        when(service.consumePublicShare(eq("tok-1"), any())).thenReturn(
                new FileShareConsumeResult("https://download/", Instant.now().plusSeconds(300)));
        mockMvc.perform(post("/api/v1/public/shares/tok-1/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ConsumeShareRequestDto("1234"))))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> assertThat(r.getResponse().getContentAsString()).contains("download"));
    }

    @Test
    void download_noBody_passesNullPassword() throws Exception {
        when(service.consumePublicShare(eq("tok-1"), any())).thenReturn(
                new FileShareConsumeResult("https://download/", Instant.now().plusSeconds(300)));
        mockMvc.perform(post("/api/v1/public/shares/tok-1/download"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
    }

    @Test
    void download_passwordRequired_returns401() throws Exception {
        when(service.consumePublicShare(eq("tok-1"), any()))
                .thenThrow(new FileException(FileException.Code.SHARE_PASSWORD_REQUIRED, "x"));
        mockMvc.perform(post("/api/v1/public/shares/tok-1/download")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(401));
    }

    @Test
    void download_limitExceeded_returns429() throws Exception {
        when(service.consumePublicShare(eq("tok-1"), any()))
                .thenThrow(new FileException(FileException.Code.SHARE_DOWNLOAD_LIMIT_EXCEEDED, "x"));
        mockMvc.perform(post("/api/v1/public/shares/tok-1/download")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(429));
    }

    private FileShareInfo stubInfo(String token, boolean hasPassword) {
        return new FileShareInfo(token, "f1", "hello.txt", "text/plain", 11L,
                LocalDateTime.now().plusDays(7), hasPassword, 5, 0, 5,
                "active", LocalDateTime.now(), 1L);
    }
}
