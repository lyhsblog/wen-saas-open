package com.wensaas.core.impl.job;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.spi.ScheduledJob;
import com.wensaas.core.impl.file.storage.StorageBackendResolver;
import com.wensaas.core.impl.file.SysFile;
import com.wensaas.core.impl.file.SysFileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 每小时清理 uploading 状态超过 24 小时的孤儿元数据。
 *
 * <p>客户端申请签名 URL 后未调 confirmUpload（弃单 / 网络中断 / 客户端崩溃）→
 * sys_file 留下 state=uploading 的脏记录；本 Job 清理。
 *
 * <p>默认 cron：{@code 0 0 * * * ?}（每小时）。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class UploadingOrphanCleanerJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(UploadingOrphanCleanerJob.class);
    private static final int BATCH_SIZE = 500;
    private static final int MAX_ITERATIONS = 50;
    private static final long ORPHAN_HOURS = 24;
    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();

    private final SysFileMapper fileMapper;
    private final StorageBackendResolver resolver;

    public UploadingOrphanCleanerJob(SysFileMapper fileMapper, StorageBackendResolver resolver) {
        this.fileMapper = fileMapper;
        this.resolver = resolver;
    }

    @Override
    public String jobKey() {
        return "uploading-orphan-cleaner";
    }

    @Override
    public String cron() {
        return "0 0 * * * ?";
    }

    /** 执行体（锁由调度框架统一加）；单测可直接调用。 */
    @Override
    public void execute() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(ORPHAN_HOURS);
        int totalDeleted = 0;

        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                List<SysFile> batch = fileMapper.selectUploadingOrphans(threshold, BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                for (SysFile f : batch) {
                    // 客户端可能根本没真正 PUT 上去；best-effort 删后端
                    try {
                        var storage = resolver.resolveByConfigId(f.getBackendConfigId());
                        storage.delete(f.getBackendObjectKey());
                    } catch (RuntimeException ex) {
                        log.debug("[UploadingOrphanCleaner] 后端 delete 失败（继续删元数据）file_id={}, err={}",
                                f.getFileId(), ex.toString());
                    }
                    fileMapper.physicalDelete(f.getId());
                    totalDeleted++;
                }
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
            }
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
        if (totalDeleted > 0) {
            log.info("[UploadingOrphanCleaner] 清理 uploading 孤儿 {} 条", totalDeleted);
        }
    }
}
