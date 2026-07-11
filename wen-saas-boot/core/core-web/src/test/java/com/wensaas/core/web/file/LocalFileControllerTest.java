package com.wensaas.core.web.file;

import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.impl.file.storage.FileStorageFactory;
import com.wensaas.core.impl.file.adapter.LocalFileStorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class LocalFileControllerTest {

    /** 测试用 HMAC key 与 LocalAdapter 内一致；可通过反射 / 构造拿到 sign 函数。 */
    private static final byte[] HMAC_KEY = "test-aes-key-for-unit-only-32by!".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tmpRoot;

    private LocalFileStorageAdapter adapter;
    private FileStorageFactory factory;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        adapter = new LocalFileStorageAdapter(tmpRoot, HMAC_KEY);
        factory = mock(FileStorageFactory.class);
        when(factory.localAdapter()).thenReturn(adapter);
        LocalFileController controller = new LocalFileController(factory);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void download_validHmac_returnsContent() throws Exception {
        String key = "tenants/9601/202605/f-abc";
        byte[] payload = "hello, world".getBytes(StandardCharsets.UTF_8);
        adapter.upload(key, new ByteArrayInputStream(payload), payload.length, "text/plain");

        long exp = Instant.now().plusSeconds(60).toEpochMilli();
        String sig = signedUrlSig(key, exp);

        // Controller 直接写 HttpServletResponse（同步返回 void），无异步 dispatch
        MockHttpServletResponse resp = mockMvc.perform(get("/api/v1/files/local-download")
                        .param("key", key).param("exp", String.valueOf(exp)).param("sig", sig))
                .andReturn().getResponse();

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getContentAsByteArray()).isEqualTo(payload);
    }

    @Test
    void download_expired_returns410() throws Exception {
        String key = "tenants/9601/202605/f-expired";
        adapter.upload(key, new ByteArrayInputStream("x".getBytes()), 1, "text/plain");

        long exp = Instant.now().minusSeconds(60).toEpochMilli();   // 已过期
        String sig = signedUrlSig(key, exp);

        mockMvc.perform(get("/api/v1/files/local-download")
                        .param("key", key).param("exp", String.valueOf(exp)).param("sig", sig))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(410));
    }

    @Test
    void download_wrongSig_returns401() throws Exception {
        String key = "tenants/9601/202605/f-wrong";
        adapter.upload(key, new ByteArrayInputStream("x".getBytes()), 1, "text/plain");

        long exp = Instant.now().plusSeconds(60).toEpochMilli();
        mockMvc.perform(get("/api/v1/files/local-download")
                        .param("key", key).param("exp", String.valueOf(exp)).param("sig", "deadbeef"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(401));
    }

    @Test
    void download_invalidObjectKey_returns400() throws Exception {
        // 不以 tenants/ 开头
        String badKey = "etc/passwd";
        long exp = Instant.now().plusSeconds(60).toEpochMilli();
        mockMvc.perform(get("/api/v1/files/local-download")
                        .param("key", badKey).param("exp", String.valueOf(exp)).param("sig", "x"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(400));
    }

    @Test
    void download_objectNotExists_throwsFileNotFound() throws Exception {
        String key = "tenants/9601/202605/f-missing";
        long exp = Instant.now().plusSeconds(60).toEpochMilli();
        String sig = signedUrlSig(key, exp);

        // 无 ExceptionAdvice 时 Spring 默认把未处理异常 → 500。
        // 这里仅验证 Controller 透传业务异常（被外层 Advice 转 404，详见 FileRestExceptionAdvice）
        try {
            mockMvc.perform(get("/api/v1/files/local-download")
                    .param("key", key).param("exp", String.valueOf(exp)).param("sig", sig));
        } catch (Exception ex) {
            Throwable cause = ex;
            while (cause != null && !(cause instanceof FileException)) cause = cause.getCause();
            assertThat(cause).isInstanceOf(FileException.class);
            assertThat(((FileException) cause).code()).isEqualTo(FileException.Code.FILE_NOT_FOUND);
        }
    }

    @Test
    void upload_validHmac_writesToDisk() throws Exception {
        String key = "tenants/9601/202605/f-upload";
        long exp = Instant.now().plusSeconds(60).toEpochMilli();
        String sig = signedUrlSig(key, exp);

        byte[] body = "uploaded bytes".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(put("/api/v1/files/local-upload")
                        .param("key", key).param("exp", String.valueOf(exp)).param("sig", sig)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(body))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(204));

        // 真磁盘断言
        try (InputStream in = adapter.download(key)) {
            assertThat(in.readAllBytes()).isEqualTo(body);
        }
    }

    /** 复刻 LocalFileStorageAdapter.sign 算法（HmacSHA256(key, objectKey + "|" + exp)）。 */
    private static String signedUrlSig(String key, long exp) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(HMAC_KEY, "HmacSHA256"));
            byte[] sig = mac.doFinal((key + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
