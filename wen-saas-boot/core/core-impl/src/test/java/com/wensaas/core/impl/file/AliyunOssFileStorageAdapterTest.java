package com.wensaas.core.impl.file;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.api.file.SignedUploadUrl;
import com.wensaas.core.impl.file.adapter.AliyunOssFileStorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AliyunOssFileStorageAdapterTest {

    private OSS ossClient;
    private AliyunOssFileStorageAdapter adapter;

    @BeforeEach
    void setup() {
        ossClient = mock(OSS.class);
        adapter = new AliyunOssFileStorageAdapter(ossClient, "test-bucket", "oss-cn-hangzhou.aliyuncs.com");
    }

    @Test
    void backendType_isAliyunOss() {
        assertThat(adapter.backendType()).isEqualTo(FileBackendType.ALIYUN_OSS);
    }

    @Test
    void upload_callsPutObject() {
        byte[] payload = "x".getBytes();
        adapter.upload("k1", new ByteArrayInputStream(payload), 1, "text/plain");
        verify(ossClient).putObject(eq("test-bucket"), eq("k1"), any(java.io.InputStream.class), any(ObjectMetadata.class));
    }

    @Test
    void download_callsGetObject() throws Exception {
        OSSObject obj = mock(OSSObject.class);
        when(ossClient.getObject("test-bucket", "k1")).thenReturn(obj);
        when(obj.getObjectContent()).thenReturn(new ByteArrayInputStream("ok".getBytes()));
        assertThat(adapter.download("k1")).isNotNull();
        verify(ossClient).getObject("test-bucket", "k1");
    }

    @Test
    void signedDownloadUrl_callsGeneratePresignedUrl() throws Exception {
        URL fakeUrl = new java.net.URI("https://oss/sign").toURL();
        when(ossClient.generatePresignedUrl(eq("test-bucket"), eq("k1"), any())).thenReturn(fakeUrl);
        String url = adapter.signedDownloadUrl("k1", Duration.ofMinutes(5));
        assertThat(url).isEqualTo("https://oss/sign");
    }

    @Test
    void signedUploadUrl_returnsStructure() throws Exception {
        URL fakeUrl = new java.net.URI("https://oss/upload").toURL();
        when(ossClient.generatePresignedUrl(any(com.aliyun.oss.model.GeneratePresignedUrlRequest.class)))
                .thenReturn(fakeUrl);
        SignedUploadUrl signed = adapter.signedUploadUrl("tenants/1/202605/fABC", "text/plain", 5,
                Duration.ofMinutes(5));
        assertThat(signed.url()).isEqualTo("https://oss/upload");
        assertThat(signed.method()).isEqualTo("PUT");
        assertThat(signed.fileId()).isEqualTo("fABC");
        assertThat(signed.headers()).containsEntry("Content-Type", "text/plain");
    }

    @Test
    void metadata_existing() {
        when(ossClient.doesObjectExist("test-bucket", "k1")).thenReturn(true);
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(42);
        meta.setContentType("text/plain");
        when(ossClient.getObjectMetadata("test-bucket", "k1")).thenReturn(meta);
        FileStorage.BackendObjectInfo info = adapter.metadata("k1");
        assertThat(info.sizeBytes()).isEqualTo(42);
        assertThat(info.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void metadata_missing_null() {
        when(ossClient.doesObjectExist("test-bucket", "k1")).thenReturn(false);
        assertThat(adapter.metadata("k1")).isNull();
    }

    @Test
    void delete_callsDeleteObject() {
        adapter.delete("k1");
        verify(ossClient).deleteObject("test-bucket", "k1");
    }

    @Test
    void putException_wrapsAsFileException() {
        when(ossClient.putObject(any(String.class), any(String.class), any(java.io.InputStream.class),
                any(ObjectMetadata.class)))
                .thenThrow(new OSSException("auth failed"));
        assertThatThrownBy(() -> adapter.upload("k1", new ByteArrayInputStream("x".getBytes()), 1, null))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.BACKEND_IO_ERROR));
    }
}
