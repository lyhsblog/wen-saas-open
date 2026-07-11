package com.wensaas.core.impl.job;

import com.wensaas.core.api.spi.ScheduledJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 每月 25 日凌晨为 6 张分区表（{@code sys_operation_log} / {@code sys_login_log} /
 * {@code sys_api_call_log} / {@code sys_webhook_log} / {@code sys_data_change_log} /
 * {@code sys_module_access_log}）创建下下个月分区。
 * 提前一个月预留，防月初零点边界。
 *
 * <p>默认 cron：{@code 0 30 0 25 * ?}（每月 25 日 00:30）。
 *
 * <p>幂等：{@code CREATE TABLE IF NOT EXISTS}。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class MonthlyPartitionCreatorJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(MonthlyPartitionCreatorJob.class);
    private static final DateTimeFormatter PARTITION_SUFFIX_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter PG_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 仅允许 6 张表的 _pYYYYMM 形态。 */
    private static final Pattern PARTITION_NAME_PATTERN = Pattern.compile(
            "^sys_(operation_log|login_log|api_call_log|webhook_log|data_change_log|module_access_log)_p\\d{6}$");

    /**
     * 允许创建分区的 parent 表白名单。
     *
     * <p>{@code sys_data_change_log} 由 PG 触发器写入、主键取自序列，分区维护与触发器无关；
     * {@code sys_module_access_log} 由内存缓冲批量 INSERT。两者建分区都是纯
     * {@code CREATE TABLE ... PARTITION OF}，与其余 4 张表建法一致。
     *
     * <p>DEFAULT 分区并存约束：这 6 张表都带 {@code *_default} 兜底分区，而 PG 规定
     * DEFAULT 分区里已存在落入目标区间的行时，不允许再创建该区间的分区（直接报错）。
     * 本任务每月 25 日建"下下月"分区、提前逾一个月，且各表 {@code created_at} 都是写入时的
     * 当前时间、不会出现未来值，所以正常调度下目标月份不可能已有行落进 DEFAULT，不会相撞。
     * 只有任务连续漏跑、时间跨入了没有对应分区的月份后，新行才会落 DEFAULT——此时补建该月
     * 分区会失败，需先把 DEFAULT 中该区间的行迁出再建，不能只靠重跑本任务恢复。
     */
    private static final Set<String> ALLOWED_PARENTS = Set.of(
            "sys_operation_log", "sys_login_log",
            "sys_api_call_log", "sys_webhook_log",
            "sys_data_change_log", "sys_module_access_log");

    private final JdbcTemplate jdbc;

    public MonthlyPartitionCreatorJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String jobKey() {
        return "monthly-partition-creator";
    }

    @Override
    public String cron() {
        return "0 30 0 25 * ?";
    }

    /** 执行体（锁由调度框架统一加）；单测可直接调用。 */
    @Override
    public void execute() {
        YearMonth target = YearMonth.from(LocalDate.now()).plusMonths(2);
        for (String parent : ALLOWED_PARENTS) {
            createPartition(parent, target);
        }
    }

    /** 暴露给单测；不经调度触发。 */
    public void createPartition(String parentTable, YearMonth target) {
        if (!ALLOWED_PARENTS.contains(parentTable)) {
            throw new IllegalArgumentException("非法 parent 表: " + parentTable);
        }
        String partitionName = parentTable + "_p" + target.format(PARTITION_SUFFIX_FMT);
        if (!PARTITION_NAME_PATTERN.matcher(partitionName).matches()) {
            throw new IllegalArgumentException("非法分区名: " + partitionName);
        }
        LocalDate from = target.atDay(1);
        LocalDate to = target.plusMonths(1).atDay(1);
        String sql = "CREATE TABLE IF NOT EXISTS " + partitionName
                + " PARTITION OF " + parentTable
                + " FOR VALUES FROM ('" + from.atStartOfDay().format(PG_DATE_FMT) + "')"
                + " TO ('" + to.atStartOfDay().format(PG_DATE_FMT) + "')";
        log.info("[Partition] 创建: {}", sql);
        jdbc.execute(sql);
    }
}
