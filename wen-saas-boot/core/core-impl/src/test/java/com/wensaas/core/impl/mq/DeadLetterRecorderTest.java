package com.wensaas.core.impl.mq;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DeadLetterRecorder} 纯单测：落库映射、tenant_id 解析兜底、body 截断、落库失败不抛、关闭时不落库。
 * MeterRegistry 用空 ObjectProvider（无 actuator 环境的优雅跳过）。
 */
class DeadLetterRecorderTest {

    private SysMqDeadLetterMapper mapper;
    private MqDeadLetterProperties props;
    private DeadLetterRecorder recorder;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(SysMqDeadLetterMapper.class);
        props = new MqDeadLetterProperties();
        ObjectProvider<MeterRegistry> meterRegistry = mock(ObjectProvider.class);
        when(meterRegistry.getIfAvailable()).thenReturn(null);  // 无注册表 → counter 跳过
        recorder = new DeadLetterRecorder(mapper, props, meterRegistry);
    }

    @Test
    void record_mapsAllFields() {
        LocalDateTime born = LocalDateTime.now().minusMinutes(5);
        recorder.record(new DeadLetterRecord("grp-x", "topic-x", "msg-1", "key-1",
                "{\"tenantId\":9001}", 3, born, "boom summary", DeadLetterRecord.SOURCE_RETRY_EXHAUSTED));

        SysMqDeadLetter row = captureInserted();
        assertThat(row.getId()).isNotNull();
        assertThat(row.getConsumerGroup()).isEqualTo("grp-x");
        assertThat(row.getOriginalTopic()).isEqualTo("topic-x");
        assertThat(row.getMsgId()).isEqualTo("msg-1");
        assertThat(row.getMsgKeys()).isEqualTo("key-1");
        assertThat(row.getReconsumeTimes()).isEqualTo(3);
        assertThat(row.getBornAt()).isEqualTo(born);
        assertThat(row.getExceptionSummary()).isEqualTo("boom summary");
        assertThat(row.getSource()).isEqualTo(DeadLetterRecord.SOURCE_RETRY_EXHAUSTED);
        assertThat(row.getStatus()).isEqualTo("new");
        assertThat(row.getCreatedAt()).isNotNull();
    }

    @Test
    void record_resolvesTenantIdFromBody() {
        recorder.record(poison("{\"tenantId\":12345,\"foo\":\"bar\"}"));
        assertThat(captureInserted().getTenantId()).isEqualTo(12345L);
    }

    @Test
    void record_tenantIdFallbackZero_whenAbsentMalformedOrNull() {
        recorder.record(poison("{\"foo\":\"bar\"}"));   // 无 tenantId
        assertThat(captureInserted().getTenantId()).isZero();

        recorder = freshRecorder();
        recorder.record(poison("not-a-json"));           // 解析失败
        assertThat(captureInserted().getTenantId()).isZero();

        recorder = freshRecorder();
        recorder.record(poison(null));                   // body 为 null
        assertThat(captureInserted().getTenantId()).isZero();
    }

    @Test
    void record_truncatesBody() {
        props.setBodyTruncateBytes(10);
        String big = "0123456789ABCDEFGHIJ";  // 20 chars
        recorder.record(poison(big));
        assertThat(captureInserted().getBody()).hasSize(10).isEqualTo("0123456789");
    }

    @Test
    void record_disabled_skipsInsert_noThrow() {
        props.setEnabled(false);
        assertThatCode(() -> recorder.record(poison("{}"))).doesNotThrowAnyException();
        verify(mapper, never()).insert(any(SysMqDeadLetter.class));
    }

    @Test
    void record_insertThrows_doesNotPropagate() {
        when(mapper.insert(any(SysMqDeadLetter.class))).thenThrow(new RuntimeException("db down"));
        // 监控自身别造环：落库失败只 log.error 不抛
        assertThatCode(() -> recorder.record(poison("{}"))).doesNotThrowAnyException();
    }

    private DeadLetterRecorder freshRecorder() {
        mapper = mock(SysMqDeadLetterMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> mp = mock(ObjectProvider.class);
        when(mp.getIfAvailable()).thenReturn(null);
        return new DeadLetterRecorder(mapper, props, mp);
    }

    private SysMqDeadLetter captureInserted() {
        ArgumentCaptor<SysMqDeadLetter> cap = ArgumentCaptor.forClass(SysMqDeadLetter.class);
        verify(mapper).insert(cap.capture());
        return cap.getValue();
    }

    private static DeadLetterRecord poison(String body) {
        return DeadLetterRecord.poison("grp", "topic", body, new RuntimeException("x"));
    }
}
