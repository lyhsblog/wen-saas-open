package com.wensaas.core.impl.task;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * 任务中心配置。
 *
 * <pre>
 * wen-saas-boot:
 *   task:
 *     enabled: true
 *     execute-topic: wen_task_execute
 *     execute-consumer-group: wen-saas-boot-task-execute
 *     progress-key-ttl: 1d
 *     progress-update-throttle-ms: 200
 *     expires-ttl: 30d
 *     cancel-flag-ttl: 1h
 *     pending-reap-threshold: 2m
 *     pending-reap-batch-size: 200
 *     pending-reap-max-iterations: 10
 * </pre>
 */
@ConfigurationProperties(prefix = "wen-saas-boot.task")
@Getter
@Setter
public class TaskProperties {

    private boolean enabled = true;
    private String executeTopic = "wen_task_execute";
    private String executeConsumerGroup = "wen-saas-boot-task-execute";
    private Duration progressKeyTtl = Duration.ofDays(1);
    private long progressUpdateThrottleMs = 200L;
    private Duration expiresTtl = Duration.ofDays(30);
    private Duration cancelFlagTtl = Duration.ofHours(1);
    /**
     * 幂等去重声明保留时长。去重维度为 taskId，挡住 MQ at-least-once 对同一条任务执行消息的重复投递
     * （避免重复执行任务副作用）。应大于单任务最长执行时长，否则长任务执行中声明过期、重投不再被判重。
     */
    private Duration dedupTtl = Duration.ofMinutes(30);

    /**
     * 补偿巡检年龄阈值（M8）。只补偿"提交已超过该时长仍 PENDING"的任务，避免与正常 afterCommit 发布 /
     * 消费延迟抢跑（在途消息会被消费侧 taskId 幂等去重判重，无害但徒增 MQ 流量）。
     */
    private Duration pendingReapThreshold = Duration.ofMinutes(2);

    /** 补偿巡检单次扫描/重投的最大条数。 */
    private int pendingReapBatchSize = 200;

    /** 补偿巡检单次执行的最大迭代轮数，防一次跑太久（达上限留待下次调度）。 */
    private int pendingReapMaxIterations = 10;

}
