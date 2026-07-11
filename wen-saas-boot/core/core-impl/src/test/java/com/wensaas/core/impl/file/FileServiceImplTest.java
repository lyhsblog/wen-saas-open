package com.wensaas.core.impl.file;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileMetadata;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.file.SignedUploadRequest;
import com.wensaas.core.api.file.SignedUploadUrl;
import com.wensaas.core.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.file.storage.StorageBackendResolver;

class FileServiceImplTest {

    private static final long TENANT = 9901L;
    private static final long USER_ID = 11L;

    private SysFileMapper fileMapper;
    private StorageBackendResolver resolver;
    private FileDownloadUrlCache urlCache;
    private FilePermissionEvaluator permissionEvaluator;
    private FileProperties props;
    private FileStorage storage;
    private FileService service;

    @BeforeEach
    void setup() {
        fileMapper = mock(SysFileMapper.class);
        resolver = mock(StorageBackendResolver.class);
        urlCache = mock(FileDownloadUrlCache.class);
        storage = mock(FileStorage.class);
        when(storage.backendType()).thenReturn(FileBackendType.LOCAL);
        props = new FileProperties();
        permissionEvaluator = new FilePermissionEvaluator(List.of());
        service = new FileServiceImpl(fileMapper, resolver, urlCache, permissionEvaluator, props);
        TenantContext.set(TENANT);
        UserContext.set(new UserPrincipal(USER_ID, TENANT, "alice", "sess-1", false, null, false, null));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        UserContext.clear();
    }

    private void mockActiveResolve() {
        when(resolver.resolveActive(TENANT)).thenReturn(
                new StorageBackendResolver.ResolveResult(storage, null));
    }

    @Test
    void uploadDirect_success() {
        mockActiveResolve();
        byte[] payload = "hello".getBytes();
        FileUploadResult res = service.uploadDirect(new FileUploadRequest(
                "a.txt", "text/plain", payload.length, new ByteArrayInputStream(payload),
                FileVisibility.FILE_CENTER, null, null, null, "/my"));
        assertThat(res.fileId()).isNotBlank();
        assertThat(res.backendType()).isEqualTo(FileBackendType.LOCAL);
        verify(storage).upload(anyString(), any(), eq((long) payload.length), eq("text/plain"));
        ArgumentCaptor<SysFile> cap = ArgumentCaptor.forClass(SysFile.class);
        verify(fileMapper).insert(cap.capture());
        assertThat(cap.getValue().getUploadState()).isEqualTo("active");
        assertThat(cap.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(cap.getValue().getUploadedBy()).isEqualTo(USER_ID);
    }

    @Test
    void uploadDirect_sizeExceeded_throws() {
        props.setMaxSize(org.springframework.util.unit.DataSize.ofBytes(10));
        mockActiveResolve();
        assertThatThrownBy(() -> service.uploadDirect(new FileUploadRequest(
                "a.txt", "text/plain", 99, new ByteArrayInputStream(new byte[1]),
                FileVisibility.FILE_CENTER, null, null, null, null)))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SIZE_EXCEEDED));
    }

    @Test
    void requestSignedUpload_writesUploadingAndReturnsUrl() {
        mockActiveResolve();
        SignedUploadUrl signed = new SignedUploadUrl("fX", "https://upload",
                "PUT", Map.of(), Instant.now().plusSeconds(60));
        when(storage.signedUploadUrl(anyString(), eq("text/plain"), eq(5L), any())).thenReturn(signed);

        SignedUploadUrl result = service.requestSignedUpload(new SignedUploadRequest(
                "a.txt", "text/plain", 5, FileVisibility.FILE_CENTER, null, null, null, null));

        assertThat(result.url()).isEqualTo("https://upload");
        ArgumentCaptor<SysFile> cap = ArgumentCaptor.forClass(SysFile.class);
        verify(fileMapper).insert(cap.capture());
        assertThat(cap.getValue().getUploadState()).isEqualTo("uploading");
    }

    @Test
    void confirmUpload_success_updatesToActive() {
        SysFile uploading = newSysFile("f1", "uploading", null);
        when(fileMapper.selectByFileId("f1")).thenReturn(uploading);
        when(resolver.resolveByConfigId(any())).thenReturn(storage);
        when(storage.metadata(any())).thenReturn(new FileStorage.BackendObjectInfo(123L, "image/png"));

        FileUploadResult res = service.confirmUpload("f1");
        assertThat(res.sizeBytes()).isEqualTo(123L);
        assertThat(uploading.getUploadState()).isEqualTo("active");
        verify(fileMapper).updateById(uploading);
    }

    @Test
    void confirmUpload_alreadyActive_throws() {
        SysFile alreadyActive = newSysFile("f1", "active", "text/plain");
        when(fileMapper.selectByFileId("f1")).thenReturn(alreadyActive);
        assertThatThrownBy(() -> service.confirmUpload("f1"))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.UPLOAD_NOT_CONFIRMABLE));
    }

    @Test
    void confirmUpload_objectMissing_throws() {
        SysFile uploading = newSysFile("f1", "uploading", null);
        when(fileMapper.selectByFileId("f1")).thenReturn(uploading);
        when(resolver.resolveByConfigId(any())).thenReturn(storage);
        when(storage.metadata(any())).thenReturn(null);
        assertThatThrownBy(() -> service.confirmUpload("f1"))
                .isInstanceOf(FileException.class);
    }

    @Test
    void confirmUpload_fileNotFound_throws() {
        when(fileMapper.selectByFileId("f1")).thenReturn(null);
        assertThatThrownBy(() -> service.confirmUpload("f1"))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.FILE_NOT_FOUND));
    }

    @Test
    void signedDownloadUrl_cacheHit_skipsBackend() {
        when(urlCache.get("f1")).thenReturn(Optional.of("https://cached"));
        String url = service.signedDownloadUrl("f1", null);
        assertThat(url).isEqualTo("https://cached");
        verify(fileMapper, never()).selectByFileId(any());
    }

    @Test
    void signedDownloadUrl_cacheMiss_invokesBackend() {
        when(urlCache.get("f1")).thenReturn(Optional.empty());
        SysFile entity = newSysFile("f1", "active", "text/plain");
        when(fileMapper.selectByFileId("f1")).thenReturn(entity);
        when(resolver.resolveByConfigId(any())).thenReturn(storage);
        when(storage.signedDownloadUrl(any(), any())).thenReturn("https://signed");
        String url = service.signedDownloadUrl("f1", Duration.ofMinutes(5));
        assertThat(url).isEqualTo("https://signed");
        verify(urlCache).put("f1", "https://signed");
    }

    @Test
    void signedDownloadUrl_businessOnly_checkerDenies() {
        permissionEvaluator = new FilePermissionEvaluator(List.of(
                new com.wensaas.core.api.file.FilePermissionChecker() {
                    @Override public String ownerModule() { return "crm"; }
                    @Override public boolean canDownload(FileMetadata file, Long userId) { return false; }
                }));
        service = new FileServiceImpl(fileMapper, resolver, urlCache, permissionEvaluator, props);
        when(urlCache.get("f1")).thenReturn(Optional.empty());
        SysFile entity = newSysFile("f1", "active", "text/plain");
        entity.setVisibility("business-only");
        entity.setOwnerModule("crm");
        entity.setOwnerRefType("客户");
        entity.setOwnerRefId(123L);
        when(fileMapper.selectByFileId("f1")).thenReturn(entity);
        assertThatThrownBy(() -> service.signedDownloadUrl("f1", null))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.DOWNLOAD_FORBIDDEN));
    }

    @Test
    void delete_softDeletes_andEvictsCache() {
        SysFile entity = newSysFile("f1", "active", "text/plain");
        when(fileMapper.selectByFileId("f1")).thenReturn(entity);
        service.delete("f1");
        verify(fileMapper).deleteById(entity.getId());
        verify(urlCache).evict("f1");
    }

    @Test
    void delete_fileNotFound_throws() {
        when(fileMapper.selectByFileId("f1")).thenReturn(null);
        assertThatThrownBy(() -> service.delete("f1"))
                .isInstanceOf(FileException.class);
    }

    @Test
    void metadata_returnsView() {
        SysFile entity = newSysFile("f1", "active", "image/png");
        when(fileMapper.selectByFileId("f1")).thenReturn(entity);
        FileMetadata m = service.metadata("f1");
        assertThat(m.fileId()).isEqualTo("f1");
        assertThat(m.mimeType()).isEqualTo("image/png");
        assertThat(m.tenantId()).isEqualTo(TENANT);
    }

    @Test
    void listByOwner_callsMapper() {
        when(fileMapper.selectByOwner(TENANT, "crm", "客户", 1L))
                .thenReturn(List.of(newSysFile("f1", "active", "text/plain")));
        List<FileMetadata> list = service.listByOwner("crm", "客户", 1L);
        assertThat(list).hasSize(1);
    }

    private SysFile newSysFile(String fileId, String state, String mime) {
        SysFile f = new SysFile();
        f.setId(123L);
        f.setTenantId(TENANT);
        f.setFileId(fileId);
        f.setFileName("a.txt");
        f.setMimeType(mime);
        f.setSizeBytes(5L);
        f.setVisibility("file-center");
        f.setBackendType("local");
        f.setBackendConfigId(null);
        f.setBackendObjectKey("tenants/" + TENANT + "/202605/" + fileId);
        f.setUploadState(state);
        f.setUploadedBy(USER_ID);
        return f;
    }
}
