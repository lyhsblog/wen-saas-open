package com.wensaas.core.impl.job;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.spi.ScheduledJob;
import com.wensaas.core.impl.file.storage.FileStorageFactory;
import com.wensaas.core.impl.file.storage.StorageBackendResolver;
import com.wensaas.core.impl.file.SysFile;
import com.wensaas.core.impl.file.SysFileFolder;
import com.wensaas.core.impl.file.SysFileFolderMapper;
import com.wensaas.core.impl.file.SysFileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 每天 05:00 物理清理软删超过 30 天的文件。
 *
 * <p>流程：
 * <ol>
 *   <li>查 {@code sys_file} WHERE deleted_at &lt; now() - 30 天（分页 BATCH_SIZE，跨租户）</li>
 *   <li>逐条调对应 {@link com.wensaas.core.api.file.FileStorage}.delete 删后端对象；
 *   失败仅 log.warn，不影响元数据删除（防"配置丢失导致元数据永远清理不掉"）</li>
 *   <li>{@code physicalDelete} 删元数据行</li>
 * </ol>
 *
 * <p>默认 cron：{@code 0 0 5 * * ?}。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SoftDeletedFileCleanerJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(SoftDeletedFileCleanerJob.class);
    private static final int BATCH_SIZE = 500;
    private static final int MAX_ITERATIONS = 100;
    private static final long RETENTION_DAYS = 30;
    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();

    private final SysFileMapper fileMapper;
    private final SysFileFolderMapper folderMapper;
    private final StorageBackendResolver resolver;

    public SoftDeletedFileCleanerJob(SysFileMapper fileMapper,
                                     SysFileFolderMapper folderMapper,
                                     StorageBackendResolver resolver) {
        this.fileMapper = fileMapper;
        this.folderMapper = folderMapper;
        this.resolver = resolver;
    }

    @Override
    public String jobKey() {
        return "soft-deleted-file-cleaner";
    }

    @Override
    public String cron() {
        return "0 0 5 * * ?";
    }

    /** 执行体（锁由调度框架统一加）；单测可直接调用。 */
    @Override
    public void execute() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int totalDeleted = 0;
        int totalBackendFail = 0;

        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                List<SysFile> batch = fileMapper.selectSoftDeletedBefore(threshold, BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                for (SysFile f : batch) {
                    try {
                        var storage = resolver.resolveByConfigId(f.getBackendConfigId());
                        storage.delete(f.getBackendObjectKey());
                    } catch (RuntimeException ex) {
                        totalBackendFail++;
                        log.warn("[SoftDeletedFileCleaner] 后端 delete 失败（继续删元数据）file_id={}, key={}, err={}",
                                f.getFileId(), f.getBackendObjectKey(), ex.toString());
                    }
                    fileMapper.physicalDelete(f.getId());
                    totalDeleted++;
                }
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
            }
            totalDeleted += cleanFolders(threshold);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
        log.info("[SoftDeletedFileCleaner] 物理清理 {} 条，后端失败 {} 条", totalDeleted, totalBackendFail);
    }

    /** 物理清理软删超过 30 天的文件夹元数据（无后端对象，纯删行）。 */
    private int cleanFolders(LocalDateTime threshold) {
        int total = 0;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            List<SysFileFolder> batch = folderMapper.selectSoftDeletedBefore(threshold, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            folderMapper.physicalDeleteByIds(batch.stream().map(SysFileFolder::getId).toList());
            total += batch.size();
            if (batch.size() < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }
}
