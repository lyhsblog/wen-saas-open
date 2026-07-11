package com.wensaas.core.impl.job;

import com.wensaas.core.api.spi.ScheduledJob;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.task.SysTask;
import com.wensaas.core.impl.task.SysTaskMapper;
import com.wensaas.core.impl.task.execution.TaskExecutionMessage;
import com.wensaas.core.impl.task.execution.TaskExecutionPublisher;
import com.wensaas.core.impl.task.TaskProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PENDING 任务补偿巡检（架构审查 M8，本地消息表语义）。
 *
 * <p><b>解决的问题</b>：{@code TaskServiceImpl.submit} 写完 {@code sys_task=PENDING} 后在
 * {@code afterCommit} 钩子里发 MQ；而 {@code RocketMqTaskExecutionPublisher} 用 {@code asyncSend}
 * 且失败仅 {@code log.error}（发送异常进 {@code onException} 回调）——MQ 不可达时发布失败被吞，
 * 任务永久卡 PENDING 无补偿。本 Job 周期扫描超时仍 PENDING 的任务并重投 MQ 兜底。
 *
 * <p><b>安全性</b>（无需新增去重逻辑，三层叠加）：
 * <ol>
 *   <li>年龄阈值 {@code pending-reap-threshold}：只补偿"提交已超过该时长仍 PENDING"的，避开正常
 *       afterCommit 发布 / 消费延迟抢跑；</li>
 *   <li>在途消息的重投会被消费侧 {@code TaskExecutionConsumer} 按 taskId 幂等去重判重跳过；</li>
 *   <li>最终由 {@code TaskRunner} 的 {@code PENDING->RUNNING} 条件 UPDATE（H1）做 CAS，并发只一条赢。</li>
 * </ol>
 * 不存在毒丸循环：PENDING 仅在"从未被认领"时持续；一旦被消费即迁出 PENDING（RUNNING→FAILED/COMPLETED），
 * 不会被再次扫到。重投本身失败（MQ 仍不可达）则由下次调度重扫重试，MQ 恢复后自愈。
 *
 * <p><b>跨租户</b>：{@code sys_task} 是租户表、Job 无租户上下文，类上 {@code @CrossTenantQuery}
 * 绕过 TenantLine 扫全部租户；重投消息体带 {@code tenantId}，消费侧据此恢复上下文。
 *
 * <p>多实例单跑由 {@link JobLockSupport#runExclusive} 守卫。默认 cron：{@code 0 0/2 * * * ?}，
 * 建议与阈值匹配（默认 2m 阈值配每 1~2 分钟一次）。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@CrossTenantQuery("PendingTaskReaperJob: 运营调度补偿巡检，无租户上下文，跨租户扫 PENDING 任务重投")
public class PendingTaskReaperJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(PendingTaskReaperJob.class);

    private final SysTaskMapper mapper;
    private final TaskExecutionPublisher publisher;
    private final TaskProperties props;

    public PendingTaskReaperJob(SysTaskMapper mapper,
                                TaskExecutionPublisher publisher,
                                TaskProperties props) {
        this.mapper = mapper;
        this.publisher = publisher;
        this.props = props;
    }

    @Override
    public String jobKey() {
        return "pending-task-reaper";
    }

    @Override
    public String cron() {
        return "0 0/2 * * * ?";
    }

    /** 执行体（锁由调度框架统一加）；单测可直接调用。 */
    @Override
    public void execute() {
        int batchSize = props.getPendingReapBatchSize();
        int maxIterations = props.getPendingReapMaxIterations();
        LocalDateTime before = LocalDateTime.now().minus(props.getPendingReapThreshold());

        // keyset 游标：重投不改任务状态（消费异步），须用 id > afterId 推进，否则反复扫到同批行
        Long afterId = null;
        int total = 0;
        for (int i = 0; i < maxIterations; i++) {
            List<SysTask> stale = mapper.selectPendingForRepublish(before, afterId, batchSize);
            if (stale.isEmpty()) {
                break;
            }
            for (SysTask t : stale) {
                // requestId 原值已丢（不入库），传 null —— 仅丢链路追踪，不影响执行
                publisher.publish(new TaskExecutionMessage(t.getId(), t.getTenantId(), null));
                log.warn("task.reap.republish taskId={} tenantId={} createdAt={}",
                        t.getId(), t.getTenantId(), t.getCreatedAt());
            }
            total += stale.size();
            afterId = stale.get(stale.size() - 1).getId();  // 已按 id ASC 排序
            if (stale.size() < batchSize) {
                break;
            }
        }
        if (total > 0) {
            log.warn("[PendingTaskReaper] 补偿重投卡 PENDING 任务 {} 条（阈值 {}）", total, props.getPendingReapThreshold());
        } else {
            log.info("[PendingTaskReaper] 无需补偿");
        }
    }
}
