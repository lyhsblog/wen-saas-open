package com.wensaas.core.impl.audit.pipeline;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumer 端批量缓冲：100 条 / 5 秒窗口。
 *
 * <p>队列接收 → 后台 tick 每 500ms 检查（队列 ≥ {@code batchSize} 或距上次 flush
 * &gt; {@code batchFlushInterval}）→ 触发批量 INSERT。{@link #flush(List)} 由子类实现。
 *
 * @param <T> 事件类型（{@code AuditEvent} / {@code LoginAuditEvent}）
 */
public abstract class AuditBatchBuffer<T> {

    private static final Logger log = LoggerFactory.getLogger(AuditBatchBuffer.class);
    private static final long TICK_MS = 500L;

    private final String name;
    private final int batchSize;
    private final Duration flushInterval;
    private final int maxQueueSize;
    private final LinkedBlockingQueue<T> queue;
    private final AtomicLong lastFlushAt = new AtomicLong(System.currentTimeMillis());
    /** 因队列满 / flush 失败回队列失败而丢弃的累计事件数（可观测 + 告警，架构审查 M14）。 */
    private final AtomicLong droppedCount = new AtomicLong();

    private ScheduledExecutorService scheduler;

    protected AuditBatchBuffer(String name, int batchSize, Duration flushInterval) {
        // 默认队列上限：给足缓冲又不至于无界 OOM。下游 DB 持续慢于消费时，超限事件会被丢弃并计数告警，
        // 而非无限堆积撑爆堆内存。需要更大缓冲时用 4 参构造显式指定。
        this(name, batchSize, flushInterval, Math.max(batchSize * 500, 50_000));
    }

    protected AuditBatchBuffer(String name, int batchSize, Duration flushInterval, int maxQueueSize) {
        this.name = name;
        this.batchSize = batchSize;
        this.flushInterval = flushInterval;
        this.maxQueueSize = Math.max(maxQueueSize, batchSize);
        this.queue = new LinkedBlockingQueue<>(this.maxQueueSize);
    }

    public void offer(T event) {
        if (event == null) {
            return;
        }
        // 有界队列：满时丢弃新事件并计数告警（背压优先保护内存，避免阻塞 MQ 消费线程导致堆积）。
        if (!queue.offer(event)) {
            long n = droppedCount.incrementAndGet();
            if (n == 1 || n % 1000 == 0) {
                log.error("AuditBatchBuffer[{}] 队列已满(cap={})，丢弃审计事件（累计丢弃 {}）——下游 DB 写入跟不上消费",
                        name, maxQueueSize, n);
            }
        }
    }

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "audit-buffer-" + name);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        log.info("AuditBatchBuffer[{}] started, batchSize={}, flushInterval={}", name, batchSize, flushInterval);
    }

    @PreDestroy
    public void close() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // 残留事件强制 flush
        drainAndFlush();
        log.info("AuditBatchBuffer[{}] closed, queueRemaining={}", name, queue.size());
    }

    private void tick() {
        try {
            if (queue.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            boolean sizeReached = queue.size() >= batchSize;
            boolean timeReached = now - lastFlushAt.get() >= flushInterval.toMillis();
            if (sizeReached || timeReached) {
                drainAndFlush();
            }
        } catch (RuntimeException e) {
            log.warn("AuditBatchBuffer[{}] tick failure: {}", name, e.getMessage());
        }
    }

    private void drainAndFlush() {
        List<T> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        if (batch.isEmpty()) {
            return;
        }
        try {
            flush(batch);
            lastFlushAt.set(System.currentTimeMillis());
        } catch (RuntimeException e) {
            // flush 失败不静默丢（架构审查 M14）：尽量回队列下次重试；队列已满放不回的才计为丢弃并告警。
            // 典型 DB 抖动可在恢复后重放；极端持续失败时由有界队列兜底防 OOM。
            int requeued = 0;
            int dropped = 0;
            for (T ev : batch) {
                if (queue.offer(ev)) {
                    requeued++;
                } else {
                    dropped++;
                }
            }
            if (dropped > 0) {
                droppedCount.addAndGet(dropped);
            }
            log.error("AuditBatchBuffer[{}] flush 失败，回队列重试 {} 条 / 丢弃 {} 条（累计丢弃 {}）: {}",
                    name, requeued, dropped, droppedCount.get(), e.getMessage(), e);
        }
    }

    /** 子类实现：把 batch 批量 INSERT 到 DB。 */
    protected abstract void flush(List<T> batch);

    /** 测试用：当前队列长度。 */
    int queueSize() {
        return queue.size();
    }

    /** 因队列满 / flush 回队列失败累计丢弃的事件数（可接入监控告警）。 */
    public long droppedCount() {
        return droppedCount.get();
    }

    /** 立即触发一次 flush（测试 / 优雅停机用）。 */
    public void flushNow() {
        drainAndFlush();
    }
}
