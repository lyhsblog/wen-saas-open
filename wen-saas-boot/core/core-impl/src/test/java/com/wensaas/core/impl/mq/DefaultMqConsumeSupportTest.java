package com.wensaas.core.impl.mq;

import com.alibaba.fastjson2.JSONException;
import com.wensaas.core.api.mq.NonRetryableConsumeException;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageId;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultMqConsumeSupport} 纯单测：毒丸 / 重试耗尽 / 待重投三态分类 + {@link ConsumeResult} 语义
 * （含 core-api 接口默认方法 {@code consume} 的包裹路径）。
 */
class DefaultMqConsumeSupportTest {

    private DeadLetterRecorder recorder;
    private DefaultMqConsumeSupport support;

    @BeforeEach
    void setUp() {
        recorder = mock(DeadLetterRecorder.class);
        support = new DefaultMqConsumeSupport(recorder, new MqDeadLetterProperties());  // max-delivery-attempts 默认 3
    }

    /** 构造一个携带 body 与投递次数的 MessageView mock。 */
    private static MessageView mv(String body, int attempt) {
        MessageView v = mock(MessageView.class);
        when(v.getBody()).thenAnswer(i -> ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
        when(v.getDeliveryAttempt()).thenReturn(attempt);
        when(v.getKeys()).thenReturn(List.of());
        when(v.getBornTimestamp()).thenReturn(0L);
        when(v.getMessageId()).thenReturn(mock(MessageId.class));
        return v;
    }

    @Test
    void isPoison_classifies() {
        assertThat(support.isPoison(new JSONException("bad json"))).isTrue();
        assertThat(support.isPoison(new IllegalArgumentException("bad arg"))).isTrue();
        assertThat(support.isPoison(new NonRetryableConsumeException("nope"))).isTrue();
        assertThat(support.isPoison(new RuntimeException("transient"))).isFalse();
        assertThat(support.isPoison(new NullPointerException())).isFalse();
    }

    @Test
    void isPoison_walksCauseChain() {
        Throwable wrapped = new RuntimeException("wrap", new JSONException("inner parse"));
        assertThat(support.isPoison(wrapped)).isTrue();
    }

    @Test
    void isPoison_cyclicCauseChain_terminates() {
        // 构造双向环 a↔b（均非毒丸）：深度封顶应保证终止返回 false，而非死循环
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        assertThat(support.isPoison(a)).isFalse();
    }

    @Test
    void onConsumeFailure_poison_recordsAndAcks() {
        JSONException poison = new JSONException("bad");
        ConsumeResult r = support.onConsumeFailure("grp", "topic", mv("{bad", 1), "{bad", poison);

        assertThat(r).isEqualTo(ConsumeResult.SUCCESS);
        ArgumentCaptor<DeadLetterRecord> cap = ArgumentCaptor.forClass(DeadLetterRecord.class);
        verify(recorder).record(cap.capture());
        assertThat(cap.getValue().source()).isEqualTo(DeadLetterRecord.SOURCE_POISON);
        assertThat(cap.getValue().consumerGroup()).isEqualTo("grp");
        assertThat(cap.getValue().originalTopic()).isEqualTo("topic");
        assertThat(cap.getValue().body()).isEqualTo("{bad");
        assertThat(cap.getValue().exceptionSummary()).contains("JSONException");
    }

    @Test
    void onConsumeFailure_transientBelowLimit_failsForRetry_noRecord() {
        RuntimeException transientErr = new RuntimeException("db down");
        ConsumeResult r = support.onConsumeFailure("grp", "topic", mv("body", 1), "body", transientErr);

        assertThat(r).isEqualTo(ConsumeResult.FAILURE);
        verify(recorder, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onConsumeFailure_transientAtLimit_recordsExhaustedAndAcks() {
        RuntimeException transientErr = new RuntimeException("db down");
        ConsumeResult r = support.onConsumeFailure("grp", "topic", mv("body", 3), "body", transientErr);

        assertThat(r).isEqualTo(ConsumeResult.SUCCESS);
        ArgumentCaptor<DeadLetterRecord> cap = ArgumentCaptor.forClass(DeadLetterRecord.class);
        verify(recorder).record(cap.capture());
        assertThat(cap.getValue().source()).isEqualTo(DeadLetterRecord.SOURCE_RETRY_EXHAUSTED);
        assertThat(cap.getValue().reconsumeTimes()).isEqualTo(3);
    }

    @Test
    void consume_poisonWork_recordsAndAcks() {
        ConsumeResult r = support.consume("grp", "topic", mv("{bad", 1), json -> {
            throw new JSONException("parse");
        });
        assertThat(r).isEqualTo(ConsumeResult.SUCCESS);
        verify(recorder).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void consume_transientWorkBelowLimit_fails() {
        ConsumeResult r = support.consume("grp", "topic", mv("body", 1), json -> {
            throw new RuntimeException("net");
        });
        assertThat(r).isEqualTo(ConsumeResult.FAILURE);
        verify(recorder, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void consume_success_noRecord() {
        ConsumeResult r = support.consume("grp", "topic", mv("body", 1), json -> { /* ok */ });
        assertThat(r).isEqualTo(ConsumeResult.SUCCESS);
        verify(recorder, never()).record(org.mockito.ArgumentMatchers.any());
    }
}
