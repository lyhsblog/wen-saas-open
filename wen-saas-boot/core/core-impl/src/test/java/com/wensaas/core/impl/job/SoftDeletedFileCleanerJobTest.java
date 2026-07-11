package com.wensaas.core.impl.job;

import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.impl.file.storage.StorageBackendResolver;
import com.wensaas.core.impl.file.SysFile;
import com.wensaas.core.impl.file.SysFileFolderMapper;
import com.wensaas.core.impl.file.SysFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoftDeletedFileCleanerJobTest {

    private SysFileMapper mapper;
    private SysFileFolderMapper folderMapper;
    private StorageBackendResolver resolver;
    private FileStorage storage;
    private SoftDeletedFileCleanerJob job;

    @BeforeEach
    void setup() {
        mapper = mock(SysFileMapper.class);
        folderMapper = mock(SysFileFolderMapper.class);
        resolver = mock(StorageBackendResolver.class);
        storage = mock(FileStorage.class);
        when(resolver.resolveByConfigId(any())).thenReturn(storage);
        job = new SoftDeletedFileCleanerJob(mapper, folderMapper, resolver);
    }

    @Test
    void deletesBackendAndMetadata() {
        SysFile a = newFile(1L, "k1");
        SysFile b = newFile(2L, "k2");
        when(mapper.selectSoftDeletedBefore(any(), anyInt())).thenReturn(List.of(a, b), List.of());
        job.execute();
        verify(storage).delete("k1");
        verify(storage).delete("k2");
        verify(mapper).physicalDelete(1L);
        verify(mapper).physicalDelete(2L);
    }

    @Test
    void backendFails_metadataStillDeleted() {
        SysFile a = newFile(1L, "k1");
        when(mapper.selectSoftDeletedBefore(any(), anyInt())).thenReturn(List.of(a), List.of());
        doThrow(new FileException(FileException.Code.BACKEND_IO_ERROR, "boom"))
                .when(storage).delete("k1");
        job.execute(); // 不应抛
        verify(mapper).physicalDelete(1L);
    }

    @Test
    void emptyBatch_noOp() {
        when(mapper.selectSoftDeletedBefore(any(), anyInt())).thenReturn(List.of());
        job.execute();
        verify(mapper, times(0)).physicalDelete(any());
    }

    private SysFile newFile(long id, String key) {
        SysFile f = new SysFile();
        f.setId(id);
        f.setTenantId(1L);
        f.setFileId("f" + id);
        f.setBackendType("local");
        f.setBackendObjectKey(key);
        f.setDeletedAt(LocalDateTime.now().minusDays(31));
        return f;
    }
}
