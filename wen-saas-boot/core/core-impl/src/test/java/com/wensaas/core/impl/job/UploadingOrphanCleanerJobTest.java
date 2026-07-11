package com.wensaas.core.impl.job;

import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.impl.file.storage.StorageBackendResolver;
import com.wensaas.core.impl.file.SysFile;
import com.wensaas.core.impl.file.SysFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadingOrphanCleanerJobTest {

    private SysFileMapper mapper;
    private StorageBackendResolver resolver;
    private FileStorage storage;
    private UploadingOrphanCleanerJob job;

    @BeforeEach
    void setup() {
        mapper = mock(SysFileMapper.class);
        resolver = mock(StorageBackendResolver.class);
        storage = mock(FileStorage.class);
        when(resolver.resolveByConfigId(any())).thenReturn(storage);
        job = new UploadingOrphanCleanerJob(mapper, resolver);
    }

    @Test
    void cleansOrphans() {
        SysFile a = newOrphan(1L, "k1");
        when(mapper.selectUploadingOrphans(any(), anyInt())).thenReturn(List.of(a), List.of());
        job.execute();
        verify(mapper).physicalDelete(1L);
        verify(storage).delete("k1");
    }

    @Test
    void backendFailure_swallowed() {
        SysFile a = newOrphan(1L, "k1");
        when(mapper.selectUploadingOrphans(any(), anyInt())).thenReturn(List.of(a), List.of());
        doThrow(new FileException(FileException.Code.BACKEND_IO_ERROR, "boom"))
                .when(storage).delete("k1");
        job.execute();
        verify(mapper).physicalDelete(1L);
    }

    @Test
    void emptyBatch_noOp() {
        when(mapper.selectUploadingOrphans(any(), anyInt())).thenReturn(List.of());
        job.execute();
        verify(mapper, never()).physicalDelete(any());
    }

    private SysFile newOrphan(long id, String key) {
        SysFile f = new SysFile();
        f.setId(id);
        f.setTenantId(1L);
        f.setFileId("f" + id);
        f.setBackendType("local");
        f.setBackendObjectKey(key);
        f.setUploadState("uploading");
        f.setUploadedAt(LocalDateTime.now().minusHours(25));
        return f;
    }
}
