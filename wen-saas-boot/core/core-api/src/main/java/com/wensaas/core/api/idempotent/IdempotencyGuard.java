package com.wensaas.core.api.idempotent;

import java.time.Duration;

/**
 * 幂等去重护栏（底座共性基础能力）。
 *
 * <p>用于「至少一次」语义下的副作用去重：MQ 消费幂等、回调重投、openapi 窗口内重放等。
 * 以原子「首次声明」（SETNX）为核心原语，配合失败回滚，让重复触发的副作用只生效一次。
 *
 * <p>Key 由实现统一加命名空间前缀（{@code wen-saas-boot:dedup:}），调用方只传业务维度的
 * {@code scope}（去重域，如 {@code "webhook-send"}）与 {@code id}（去重键，如 eventId）。
 *
 * <p>正确性说明：{@link #firstSeen} 在「执行前」原子声明，因此并发重复只有一个能继续；
 * 但若声明后处理失败，应调用 {@link #release} 回滚声明，使后续重投能重新处理（否则该 id 在 TTL 内
 * 被永久判为「已处理」而丢失）。{@link #runOnce} 已封装了这一「成功保留 / 失败回滚」的模板。
 */
public interface IdempotencyGuard {

    /**
     * 原子声明某 id 在 {@code scope} 下首次处理。
     *
     * @param scope 去重域
     * @param id    去重键
     * @param ttl   声明保留时长（应覆盖业务可能的重投窗口）
     * @return {@code true}=首次（调用方可继续处理）；{@code false}=重复（调用方应跳过）
     */
    boolean firstSeen(String scope, String id, Duration ttl);

    /**
     * 释放（回滚）一次 {@link #firstSeen} 声明。用于处理失败的补偿，使后续重投可重新处理。
     */
    void release(String scope, String id);

    /**
     * 模板方法：首次则执行 {@code action}；执行抛异常时自动 {@link #release} 并重抛（允许重投重试）；
     * 重复则跳过、不执行。
     *
     * @return {@code true}=本次执行了 action；{@code false}=判为重复、已跳过
     */
    boolean runOnce(String scope, String id, Duration ttl, Runnable action);
}
