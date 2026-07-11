package com.wensaas.core.web.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.share.FileShareConsumeResult;
import com.wensaas.core.api.file.share.FileShareInfo;
import com.wensaas.core.api.file.share.FileShareService;
import com.wensaas.core.web.file.dto.ConsumeShareRequestDto;
import com.wensaas.core.web.file.dto.CreateShareRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class FileShareControllerTest {

    private FileShareService service;
    private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() {
        service = mock(FileShareService.class);
        FileShareController controller = new FileShareController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new FileRestExceptionAdvice())
                .build();
    }

    @Test
    void create_happyPath() throws Exception {
        FileShareInfo info = stubInfo("tok-1", false, 5, 5);
        when(service.createShare(any())).thenReturn(info);

        CreateShareRequestDto dto = new CreateShareRequestDto(7 * 86400L, null, 5);
        mockMvc.perform(post("/api/v1/files/f1/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(dto)))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> assertThat(r.getResponse().getContentAsString()).contains("tok-1"));
    }

    @Test
    void create_invalidPassword_returns400() throws Exception {
        // 密码 1 位 < 4 → @Size 拦截
        String body = "{\"password\":\"1\"}";
        mockMvc.perform(post("/api/v1/files/f1/shares")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(400));
    }

    @Test
    void list_returnsArray() throws Exception {
        when(service.listShares("f1")).thenReturn(List.of(stubInfo("tok-1", false, null, null)));
        mockMvc.perform(get("/api/v1/files/f1/shares"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> assertThat(r.getResponse().getContentAsString()).contains("tok-1"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(service.getShare("tok-x")).thenThrow(
                new FileException(FileException.Code.SHARE_NOT_FOUND, "x"));
        mockMvc.perform(get("/api/v1/files/shares/tok-x"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(404));
    }

    @Test
    void get_expired_returns410() throws Exception {
        when(service.getShare("tok-e")).thenThrow(
                new FileException(FileException.Code.SHARE_EXPIRED, "x"));
        mockMvc.perform(get("/api/v1/files/shares/tok-e"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(410));
    }

    @Test
    void consume_happyPath_returnsSignedUrl() throws Exception {
        when(service.consumeShare(eq("tok-1"), any())).thenReturn(
                new FileShareConsumeResult("https://download/", Instant.now().plusSeconds(300)));
        mockMvc.perform(post("/api/v1/files/shares/tok-1/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ConsumeShareRequestDto("1234"))))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
    }

    @Test
    void consume_passwordRequired_returns401() throws Exception {
        when(service.consumeShare(eq("tok-1"), any()))
                .thenThrow(new FileException(FileException.Code.SHARE_PASSWORD_REQUIRED, "x"));
        mockMvc.perform(post("/api/v1/files/shares/tok-1/download")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(401));
    }

    @Test
    void consume_passwordWrong_returns403() throws Exception {
        when(service.consumeShare(eq("tok-1"), any()))
                .thenThrow(new FileException(FileException.Code.SHARE_PASSWORD_WRONG, "x"));
        mockMvc.perform(post("/api/v1/files/shares/tok-1/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(403));
    }

    @Test
    void consume_downloadLimitExceeded_returns429() throws Exception {
        when(service.consumeShare(eq("tok-1"), any()))
                .thenThrow(new FileException(FileException.Code.SHARE_DOWNLOAD_LIMIT_EXCEEDED, "x"));
        mockMvc.perform(post("/api/v1/files/shares/tok-1/download")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(429));
    }

    @Test
    void revoke_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/files/shares/tok-1"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(204));
        verify(service).revokeShare("tok-1");
    }

    private FileShareInfo stubInfo(String token, boolean hasPassword,
                                    Integer max, Integer remaining) {
        return new FileShareInfo(token, "f1", "hello.txt", "text/plain", 11L,
                LocalDateTime.now().plusDays(7), hasPassword, max, 0, remaining,
                "active", LocalDateTime.now(), 1L);
    }
}
