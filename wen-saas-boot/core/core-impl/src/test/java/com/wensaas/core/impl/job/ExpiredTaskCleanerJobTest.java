package com.wensaas.core.impl.job;

import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.task.SysTask;
import com.wensaas.core.impl.task.SysTaskMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpiredTaskCleanerJobTest {

    private final SysTaskMapper mapper = mock(SysTaskMapper.class);
    private final FileService fileService = mock(FileService.class);
    private final ExpiredTaskCleanerJob job =
            new ExpiredTaskCleanerJob(mapper, fileService);

    private static SysTask task(long id, long tenantId, Map<String, Object> resultData) {
        SysTask t = new SysTask();
        t.setId(id);
        t.setTenantId(tenantId);
        t.setResultData(resultData);
        return t;
    }

    @Test
    void execute_partialBatch_stopsAfterOneIteration() {
        when(mapper.selectExpired(1000)).thenReturn(List.of(
                task(1L, 10L, null),
                task(2L, 10L, Map.of("rows", 3))));

        job.execute();

        verify(mapper, times(1)).selectExpired(1000);
        verify(mapper, times(1)).deleteByIds(List.of(1L, 2L));
        verify(fileService, never()).delete(any());
    }

    @Test
    void execute_fullBatch_loopsUntilDrained() {
        List<SysTask> full = java.util.stream.LongStream.rangeClosed(1, 1000)
                .mapToObj(i -> task(i, 10L, null))
                .toList();
        when(mapper.selectExpired(1000))
                .thenReturn(full)
                .thenReturn(List.of(task(2000L, 10L, null)));

        job.execute();

        verify(mapper, times(2)).selectExpired(1000);
        verify(mapper, times(2)).deleteByIds(any());
    }

    @Test
    void execute_resultFileId_softDeletedWithTenantContext() {
        when(mapper.selectExpired(1000)).thenReturn(List.of(
                task(1L, 77L, Map.of("fileId", "abc123", "rows", 5))));
        AtomicReference<Long> tenantSeen = new AtomicReference<>();
        doAnswer(inv -> {
            tenantSeen.set(TenantContext.require());
            return null;
        }).when(fileService).delete("abc123");

        job.execute();

        verify(fileService, times(1)).delete("abc123");
        assertThat(tenantSeen.get()).isEqualTo(77L);
        verify(mapper, times(1)).deleteByIds(List.of(1L));
    }

    @Test
    void execute_fileDeleteFails_taskRowsStillCleaned() {
        when(mapper.selectExpired(1000)).thenReturn(List.of(
                task(1L, 10L, Map.of("fileId", "gone")),
                task(2L, 10L, Map.of("fileId", "ok"))));
        doThrow(new FileException(FileException.Code.BACKEND_IO_ERROR, "boom"))
                .when(fileService).delete("gone");

        job.execute();

        // 单个文件软删失败不阻断：后续文件照删，任务行整批照删
        verify(fileService, times(1)).delete("ok");
        verify(mapper, times(1)).deleteByIds(List.of(1L, 2L));
    }

    @Test
    void execute_fileAlreadyDeleted_notCountedAsFailure() {
        when(mapper.selectExpired(1000)).thenReturn(List.of(
                task(1L, 10L, Map.of("fileId", "gone"))));
        doThrow(new FileException(FileException.Code.FILE_NOT_FOUND, "不存在"))
                .when(fileService).delete("gone");

        job.execute();

        verify(mapper, times(1)).deleteByIds(List.of(1L));
    }
}
