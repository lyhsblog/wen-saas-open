package com.wensaas.core.impl.job;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.spi.ScheduledJob;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.task.SysTask;
import com.wensaas.core.impl.task.SysTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 每天 03:00 清理过期任务（{@code expires_at &lt; now()}）。
 *
 * <p>流程：按批捞过期行（含 {@code result_data}），先对 {@code result_data.fileId}
 * （导出产物 / 导入失败明细等经 {@link FileService} 上传的结果文件，键名与任务中心
 * {@code /download} 端点读的一致）逐个调 {@link FileService#delete(String)} 软删——
 * 后端物理清理由 {@code SoftDeletedFileCleanerJob} 既有链路在 30 天后收尾；随后物理删除任务行。
 * 结果文件软删失败仅 warn 并继续，不阻断任务行清理（FILE_NOT_FOUND 视为已删、目标态达成）。
 *
 * <p>租户上下文：{@code sys_task} / {@code sys_file} 均为租户表而 Job 无租户上下文，
 * 执行期整体旁路 TenantLine（fileId 全局唯一，按 fileId 软删不会误及他租户）；
 * 软删调用仍包在行内 tenantId 的 {@link TenantContext#runAs}
 * 下执行，与异步链路显式注入上下文的惯例一致。
 *
 * <p>一次最多删 {@link #BATCH_SIZE} 条，多次执行直到不足一批防超长事务。
 * 默认 cron：{@code 0 0 3 * * ?}。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ExpiredTaskCleanerJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredTaskCleanerJob.class);
    private static final int BATCH_SIZE = 1000;
    private static final int MAX_ITERATIONS = 100;
    /** 任务结果文件在 result_data 里的键名（导入 / 导出 handler 与任务中心下载端点共用）。 */
    private static final String RESULT_FILE_ID_KEY = "fileId";
    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();

    private final SysTaskMapper mapper;
    private final FileService fileService;

    public ExpiredTaskCleanerJob(SysTaskMapper mapper, FileService fileService) {
        this.mapper = mapper;
        this.fileService = fileService;
    }

    @Override
    public String jobKey() {
        return "expired-task-cleaner";
    }

    @Override
    public String cron() {
        return "0 0 3 * * ?";
    }

    /** 执行体（锁由调度框架统一加）；单测可直接调用。 */
    @Override
    public void execute() {
        int totalTasks = 0;
        int totalFiles = 0;

        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                List<SysTask> batch = mapper.selectExpired(BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                for (SysTask t : batch) {
                    if (deleteResultFile(t)) {
                        totalFiles++;
                    }
                }
                mapper.deleteByIds(batch.stream().map(SysTask::getId).toList());
                totalTasks += batch.size();
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
            }
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
        log.info("[ExpiredTaskCleaner] 删除过期任务 {} 条，软删关联结果文件 {} 个", totalTasks, totalFiles);
    }

    /**
     * 软删任务结果文件（存在才删）。失败仅记日志不上抛——任务行清理不因文件问题阻断，
     * 未删成的文件留待人工按 warn 日志处理。
     *
     * @return 是否实际软删了一个文件
     */
    private boolean deleteResultFile(SysTask task) {
        Map<String, Object> result = task.getResultData();
        Object fileId = result == null ? null : result.get(RESULT_FILE_ID_KEY);
        if (fileId == null) {
            return false;
        }
        try {
            TenantContext.runAs(task.getTenantId(), () -> fileService.delete(fileId.toString()));
            return true;
        } catch (FileException ex) {
            if (ex.code() == FileException.Code.FILE_NOT_FOUND) {
                // 已被软删 / 物理清理，目标态达成
                log.debug("[ExpiredTaskCleaner] 结果文件已不存在 taskId={}, fileId={}", task.getId(), fileId);
            } else {
                log.warn("[ExpiredTaskCleaner] 结果文件软删失败（继续清理任务行）taskId={}, fileId={}, err={}",
                        task.getId(), fileId, ex.toString());
            }
            return false;
        } catch (RuntimeException ex) {
            log.warn("[ExpiredTaskCleaner] 结果文件软删失败（继续清理任务行）taskId={}, fileId={}, err={}",
                    task.getId(), fileId, ex.toString());
            return false;
        }
    }
}
