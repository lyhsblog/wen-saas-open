package com.wensaas.core.impl.audit.pipeline;

import com.wensaas.core.api.audit.AuditEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import com.wensaas.core.impl.audit.AuditProperties;

class AuditBatchBufferTest {

    @Test
    void flushesWhenBatchSizeReached() {
        AuditProperties props = new AuditProperties();
        props.setBatchSize(5);
        props.setBatchFlushInterval(Duration.ofSeconds(60));  // 防止时间触发
        RecordingBuffer buffer = new RecordingBuffer(props);
        buffer.start();
        try {
            for (int i = 0; i < 5; i++) {
                buffer.offer(sample(i));
            }
            await().atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertThat(buffer.flushedCount.get()).isEqualTo(5));
        } finally {
            buffer.close();
        }
    }

    @Test
    void flushesAfterTimeWindow() {
        AuditProperties props = new AuditProperties();
        props.setBatchSize(100);
        props.setBatchFlushInterval(Duration.ofMillis(800));
        RecordingBuffer buffer = new RecordingBuffer(props);
        buffer.start();
        try {
            buffer.offer(sample(1));
            buffer.offer(sample(2));
            await().atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertThat(buffer.flushedCount.get()).isEqualTo(2));
        } finally {
            buffer.close();
        }
    }

    @Test
    void close_drainsRemaining() {
        AuditProperties props = new AuditProperties();
        props.setBatchSize(100);
        props.setBatchFlushInterval(Duration.ofMinutes(10));
        RecordingBuffer buffer = new RecordingBuffer(props);
        buffer.start();
        buffer.offer(sample(1));
        buffer.offer(sample(2));
        buffer.close();
        assertThat(buffer.flushedCount.get()).isEqualTo(2);
    }

    @Test
    void flushFailure_doesNotStopFurtherBatches() {
        AuditProperties props = new AuditProperties();
        props.setBatchSize(2);
        props.setBatchFlushInterval(Duration.ofMinutes(10));
        AtomicInteger calls = new AtomicInteger();
        AuditBatchBuffer<AuditEvent> buffer = new AuditBatchBuffer<>(
                "test", props.getBatchSize(), props.getBatchFlushInterval()) {
            @Override
            protected void flush(List<AuditEvent> batch) {
                int n = calls.incrementAndGet();
                if (n == 1) {
                    throw new RuntimeException("first batch fail");
                }
            }
        };
        buffer.start();
        try {
            buffer.offer(sample(1));
            buffer.offer(sample(2));
            await().atMost(Duration.ofSeconds(3)).until(() -> calls.get() >= 1);
            // 再投递第二批，应仍能 flush
            buffer.offer(sample(3));
            buffer.offer(sample(4));
            await().atMost(Duration.ofSeconds(3)).until(() -> calls.get() >= 2);
        } finally {
            buffer.close();
        }
        assertThat(calls.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void offer_whenQueueFull_dropsAndCounts() {
        // 不 start() → 无后台 drain；用小容量(3)的 4 参构造触发满队列丢弃（架构审查 M14）
        AuditBatchBuffer<AuditEvent> buffer = new AuditBatchBuffer<>(
                "test", 2, Duration.ofMinutes(10), 3) {
            @Override
            protected void flush(List<AuditEvent> batch) {
            }
        };
        for (int i = 0; i < 10; i++) {
            buffer.offer(sample(i));
        }
        assertThat(buffer.queueSize()).isEqualTo(3);
        assertThat(buffer.droppedCount()).isEqualTo(7);
    }

    static class RecordingBuffer extends AuditBatchBuffer<AuditEvent> {
        final AtomicInteger flushedCount = new AtomicInteger();
        final List<AuditEvent> flushed = new ArrayList<>();
        RecordingBuffer(AuditProperties p) {
            super("test", p.getBatchSize(), p.getBatchFlushInterval());
        }
        @Override
        protected void flush(List<AuditEvent> batch) {
            flushedCount.addAndGet(batch.size());
            synchronized (flushed) { flushed.addAll(batch); }
        }
    }

    private static AuditEvent sample(int i) {
        return new AuditEvent((long) i, 0L, null, "rid-" + i, 1L, "u", null,
                "demo", "create", "Demo", String.valueOf(i), "d",
                null, null, 200, null, null, null, null, Instant.now());
    }
}
