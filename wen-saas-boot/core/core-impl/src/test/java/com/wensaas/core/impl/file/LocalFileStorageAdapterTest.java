package com.wensaas.core.impl.file;

import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.api.file.SignedUploadUrl;
import com.wensaas.core.impl.file.adapter.LocalFileStorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageAdapterTest {

    @TempDir Path tempRoot;

    private LocalFileStorageAdapter adapter;

    @BeforeEach
    void setup() {
        byte[] hmacKey = new byte[32];
        for (int i = 0; i < 32; i++) hmacKey[i] = (byte) i;
        adapter = new LocalFileStorageAdapter(tempRoot, hmacKey);
    }

    @Test
    void backendType_isLocal() {
        assertThat(adapter.backendType()).isEqualTo(FileBackendType.LOCAL);
    }

    @Test
    void uploadAndDownload_roundTrip() throws Exception {
        byte[] payload = "hello local storage".getBytes(StandardCharsets.UTF_8);
        adapter.upload("tenants/1/202605/f1", new ByteArrayInputStream(payload), payload.length, "text/plain");
        try (InputStream in = adapter.download("tenants/1/202605/f1")) {
            assertThat(in.readAllBytes()).isEqualTo(payload);
        }
        assertThat(Files.exists(tempRoot.resolve("tenants/1/202605/f1"))).isTrue();
    }

    @Test
    void metadata_existingObject() {
        byte[] payload = "abc".getBytes();
        adapter.upload("k1", new ByteArrayInputStream(payload), payload.length, "text/plain");
        FileStorage.BackendObjectInfo info = adapter.metadata("k1");
        assertThat(info).isNotNull();
        assertThat(info.sizeBytes()).isEqualTo(3);
    }

    @Test
    void metadata_missingObject_null() {
        assertThat(adapter.metadata("no-such")).isNull();
    }

    @Test
    void delete_idempotent() {
        byte[] payload = "abc".getBytes();
        adapter.upload("k2", new ByteArrayInputStream(payload), payload.length, null);
        adapter.delete("k2");
        adapter.delete("k2"); // 不应抛错
        assertThat(adapter.metadata("k2")).isNull();
    }

    @Test
    void download_missing_throws() {
        assertThatThrownBy(() -> adapter.download("no-such"))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.FILE_NOT_FOUND));
    }

    @Test
    void pathTraversal_rejected() {
        byte[] payload = "x".getBytes();
        assertThatThrownBy(() -> adapter.upload("../../etc/passwd",
                new ByteArrayInputStream(payload), 1, null))
                .isInstanceOf(FileException.class);
        assertThatThrownBy(() -> adapter.upload("/abs/path",
                new ByteArrayInputStream(payload), 1, null))
                .isInstanceOf(FileException.class);
    }

    @Test
    void signedDownloadUrl_containsToken_andVerifies() {
        String url = adapter.signedDownloadUrl("tenants/1/k1", Duration.ofMinutes(5));
        assertThat(url).contains("/api/v1/files/local-download")
                .contains("key=tenants").contains("sig=");
        // 解析 query
        String[] parts = url.substring(url.indexOf('?') + 1).split("&");
        String key = null;
        long exp = 0;
        String sig = null;
        for (String p : parts) {
            int eq = p.indexOf('=');
            String k = p.substring(0, eq);
            String v = p.substring(eq + 1);
            if (k.equals("key")) key = java.net.URLDecoder.decode(v, StandardCharsets.UTF_8);
            else if (k.equals("exp")) exp = Long.parseLong(v);
            else if (k.equals("sig")) sig = v;
        }
        assertThat(adapter.verifyToken(key, exp, sig)).isTrue();
        assertThat(adapter.verifyToken(key, exp, "wrong-sig")).isFalse();
    }

    @Test
    void signedUploadUrl_returnsStructure() {
        SignedUploadUrl signed = adapter.signedUploadUrl("tenants/1/202605/fABC",
                "text/plain", 5, Duration.ofMinutes(5));
        assertThat(signed.fileId()).isEqualTo("fABC");
        assertThat(signed.method()).isEqualTo("PUT");
        assertThat(signed.url()).contains("/api/v1/files/local-upload");
        assertThat(signed.headers()).containsEntry("Content-Type", "text/plain");
    }
}
