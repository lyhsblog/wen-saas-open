package com.wensaas.core.api.spi;

/**
 * 定时任务 SPI：实现本接口并注册为 Spring Bean，即挂入内嵌 Quartz 调度
 * （JDBC 集群存储，表在 PG 独立 quartz schema，无外部调度中间件），
 * 业务模块与 core 内建任务同一路径。
 *
 * <p>调度框架统一负责：
 * <ul>
 *   <li>按 {@link #cron()} 触发（可用 {@code wen-saas-boot.jobs.cron.<jobKey>} 覆盖默认值）；</li>
 *   <li>多实例单跑：Quartz 集群保证单节点触发，执行体外层另套 Redis 分布式锁
 *       （锁键 {@code job:<jobKey>}）兜底，实现内<b>不需要</b>再自己加锁；</li>
 *   <li>停机漏跑（misfire）恢复后补跑一次；异常兜底记日志，不中断后续调度。</li>
 * </ul>
 *
 * <p>实现类装配建议与调度同门禁（{@code wen-saas-boot.jobs.enabled}，默认开；
 * 用 {@code @ConditionalOnProperty(matchIfMissing = true)}），关闭后既不装配也不调度；
 * 单测直接 new / 直调 {@link #execute()} 验证。
 */
public interface ScheduledJob {

    /**
     * 任务唯一标识（kebab-case，如 {@code tenant-expire}）；同时用作 Quartz JobKey
     * 与分布式锁键后缀，全局不可重复，重复启动即失败。
     */
    String jobKey();

    /** 默认 cron（Quartz 六/七段式，如 {@code 0 5 0 * * ?}）；部署可按 jobKey 覆盖。 */
    String cron();

    /** 任务执行体。无需处理并发锁；抛出异常由调度框架记日志。 */
    void execute();
}
