package com.wensaas.core.impl.file;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.api.file.SignedUploadUrl;
import com.wensaas.core.impl.file.adapter.TencentCosFileStorageAdapter;
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

class TencentCosFileStorageAdapterTest {

    private COSClient cosClient;
    private TencentCosFileStorageAdapter adapter;

    @BeforeEach
    void setup() {
        cosClient = mock(COSClient.class);
        adapter = new TencentCosFileStorageAdapter(cosClient, "test-bucket-1250000000", "ap-guangzhou");
    }

    @Test
    void backendType_isTencentCos() {
        assertThat(adapter.backendType()).isEqualTo(FileBackendType.TENCENT_COS);
    }

    @Test
    void upload_callsPutObject() {
        byte[] payload = "x".getBytes();
        adapter.upload("k1", new ByteArrayInputStream(payload), 1, "text/plain");
        verify(cosClient).putObject(eq("test-bucket-1250000000"), eq("k1"),
                any(java.io.InputStream.class), any(ObjectMetadata.class));
    }

    @Test
    void download_callsGetObject() {
        COSObject obj = mock(COSObject.class);
        when(cosClient.getObject("test-bucket-1250000000", "k1")).thenReturn(obj);
        com.qcloud.cos.model.COSObjectInputStream stream = mock(com.qcloud.cos.model.COSObjectInputStream.class);
        when(obj.getObjectContent()).thenReturn(stream);
        assertThat(adapter.download("k1")).isNotNull();
        verify(cosClient).getObject("test-bucket-1250000000", "k1");
    }

    @Test
    void signedDownloadUrl_callsGeneratePresignedUrl() throws Exception {
        URL fakeUrl = new java.net.URI("https://cos/sign").toURL();
        when(cosClient.generatePresignedUrl(eq("test-bucket-1250000000"), eq("k1"), any())).thenReturn(fakeUrl);
        String url = adapter.signedDownloadUrl("k1", Duration.ofMinutes(5));
        assertThat(url).isEqualTo("https://cos/sign");
    }

    @Test
    void signedUploadUrl_returnsStructure() throws Exception {
        URL fakeUrl = new java.net.URI("https://cos/upload").toURL();
        when(cosClient.generatePresignedUrl(any(com.qcloud.cos.model.GeneratePresignedUrlRequest.class)))
                .thenReturn(fakeUrl);
        SignedUploadUrl signed = adapter.signedUploadUrl("tenants/1/202605/fABC", "text/plain", 5,
                Duration.ofMinutes(5));
        assertThat(signed.url()).isEqualTo("https://cos/upload");
        assertThat(signed.method()).isEqualTo("PUT");
        assertThat(signed.fileId()).isEqualTo("fABC");
        assertThat(signed.headers()).containsEntry("Content-Type", "text/plain");
    }

    @Test
    void metadata_existing() {
        when(cosClient.doesObjectExist("test-bucket-1250000000", "k1")).thenReturn(true);
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(42);
        meta.setContentType("text/plain");
        when(cosClient.getObjectMetadata("test-bucket-1250000000", "k1")).thenReturn(meta);
        FileStorage.BackendObjectInfo info = adapter.metadata("k1");
        assertThat(info.sizeBytes()).isEqualTo(42);
        assertThat(info.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void metadata_missing_null() {
        when(cosClient.doesObjectExist("test-bucket-1250000000", "k1")).thenReturn(false);
        assertThat(adapter.metadata("k1")).isNull();
    }

    @Test
    void delete_callsDeleteObject() {
        adapter.delete("k1");
        verify(cosClient).deleteObject("test-bucket-1250000000", "k1");
    }

    @Test
    void putException_wrapsAsFileException() {
        when(cosClient.putObject(any(String.class), any(String.class), any(java.io.InputStream.class),
                any(ObjectMetadata.class)))
                .thenThrow(new CosServiceException("auth failed"));
        assertThatThrownBy(() -> adapter.upload("k1", new ByteArrayInputStream("x".getBytes()), 1, null))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.BACKEND_IO_ERROR));
    }
}
