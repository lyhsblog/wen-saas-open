package com.wensaas.core.impl.compliance;

import com.wensaas.core.api.compliance.retention.FileRecycleBinStats;
import com.wensaas.core.api.compliance.retention.RetentionOverview;
import com.wensaas.core.api.compliance.retention.RetentionPolicyItem;
import com.wensaas.core.api.compliance.retention.RetentionService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.file.RecycleBinAgg;
import com.wensaas.core.impl.file.SysFileMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据留存与软删除策略服务实现。
 *
 * <p>策略矩阵为声明式常量，即各类数据留存策略的权威定义；文件回收站统计读当前租户 {@code sys_file}
 * 软删记录（租户线由拦截器自动过滤）。真删（彻底销户）不在此提供，走运营线下流程。
 */
public class RetentionServiceImpl implements RetentionService {

    /** 文件回收站保留天数，与 {@code SoftDeletedFileCleanerJob.RETENTION_DAYS} 对齐。 */
    static final int FILE_RETENTION_DAYS = 30;
    /** 「即将清理」预警窗口：删除时间已落在最后 7 天的回收站文件。 */
    static final int EXPIRING_WINDOW_DAYS = 7;
    static final String PURGE_SCHEDULE = "每天 05:00 自动清理";

    /** 软删除 / 留存策略矩阵（声明式，全租户一致）。 */
    private static final List<RetentionPolicyItem> POLICIES = List.of(
            new RetentionPolicyItem("成员账号", "状态置为「已停用」，账号 / 姓名 / 关联数据保留快照",
                    false, "账号不可被新成员复用"),
            new RetentionPolicyItem("部门", "不允许删除有成员的部门，须先迁移成员",
                    false, null),
            new RetentionPolicyItem("业务数据（客户、订单等）", "由业务模块自定义，建议软删（标 deleted_at）",
                    false, "底座默认不物理删除"),
            new RetentionPolicyItem("文件", "进回收站，超 " + FILE_RETENTION_DAYS + " 天自动彻底清理",
                    true, "唯一会物理硬删的对象"),
            new RetentionPolicyItem("操作日志", "永久保留，不可删除",
                    false, "合规审计留痕"),
            new RetentionPolicyItem("字典项", "仅自定义项可硬删（须无引用）；系统 / 业务预置仅可停用",
                    true, null),
            new RetentionPolicyItem("租户", "不做产品级删除，仅可暂停",
                    false, "彻底销户走运营线下流程"));

    private final SysFileMapper fileMapper;

    public RetentionServiceImpl(SysFileMapper fileMapper) {
        this.fileMapper = fileMapper;
    }

    @Override
    public RetentionOverview overview() {
        TenantContext.require();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime overdueThreshold = now.minusDays(FILE_RETENTION_DAYS);
        LocalDateTime expiringThreshold = now.minusDays(FILE_RETENTION_DAYS - EXPIRING_WINDOW_DAYS);

        RecycleBinAgg agg = fileMapper.selectRecycleBinAgg(expiringThreshold, overdueThreshold);
        FileRecycleBinStats recycleBin = agg == null
                ? FileRecycleBinStats.empty(EXPIRING_WINDOW_DAYS)
                : new FileRecycleBinStats(agg.getFileCount(), agg.getTotalBytes(),
                        agg.getExpiringSoonCount(), agg.getOverdueCount(), EXPIRING_WINDOW_DAYS);

        return new RetentionOverview(POLICIES, recycleBin, FILE_RETENTION_DAYS, PURGE_SCHEDULE);
    }
}
