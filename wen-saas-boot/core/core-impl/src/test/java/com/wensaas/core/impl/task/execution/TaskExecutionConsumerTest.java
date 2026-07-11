package com.wensaas.core.impl.task.execution;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.idempotent.IdempotencyGuard;
import com.wensaas.core.api.mq.MqConsumeSupport;
import com.wensaas.core.impl.mq.DeadLetterRecorder;
import com.wensaas.core.impl.mq.DefaultMqConsumeSupport;
import com.wensaas.core.impl.mq.MqDeadLetterProperties;
import com.wensaas.core.impl.tenant.web.TenantActivityGuard;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageId;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.task.TaskProperties;

/**
 * {@link TaskExecutionConsumer} 纯单测：验证 M6 幂等去重键、重复跳过、瞬时异常返回 FAILURE（保留 MQ 重投），
 * 毒丸落死信并 ack，以及租户停用跳过。{@link IdempotencyGuard} 用 mock，默认回放 action。
 */
class TaskExecutionConsumerTest {

    private TaskRunner runner;
    private TenantActivityGuard activityGuard;
    private IdempotencyGuard idempotency;
    private TaskProperties props;
    private DeadLetterRecorder deadLetterRecorder;
    private TaskExecutionConsumer consumer;

    @BeforeEach
    void setUp() {
        runner = mock(TaskRunner.class);
        activityGuard = mock(TenantActivityGuard.class);
        when(activityGuard.isActive(any())).thenReturn(true);
        idempotency = mock(IdempotencyGuard.class);
        // 默认非重复：回放 action 返回 true
        when(idempotency.runOnce(any(), any(), any(), any())).thenAnswer(inv -> {
            inv.<Runnable>getArgument(3).run();
            return true;
        });
        props = new TaskProperties();
        deadLetterRecorder = mock(DeadLetterRecorder.class);
        MqConsumeSupport mqSupport = new DefaultMqConsumeSupport(deadLetterRecorder, new MqDeadLetterProperties());
        consumer = new TaskExecutionConsumer(runner, activityGuard, idempotency, props, mqSupport);
    }

    private static String msg(long taskId) {
        return JSON.toJSONString(new TaskExecutionMessage(taskId, 9001L, "req-1"));
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
    void firstSeen_runsTask_withTaskIdDedupKey() {
        ConsumeResult r = consumer.consume(mv(msg(7777L), 1));

        assertThat(r).isEqualTo(ConsumeResult.SUCCESS);
        verify(idempotency).runOnce(eq("task-exec"), eq("7777"), eq(props.getDedupTtl()), any());
        verify(runner).run(7777L);
    }

    @Test
    void duplicateMessage_skipsRun() {
        // 重复投递：runOnce 判重返回 false 且不回放（doReturn 避免二次触发旧 answer 抛 NPE）
        doReturn(false).when(idempotency).runOnce(any(), any(), any(), any());

        ConsumeResult r = consumer.consume(mv(msg(7777L), 1));

        assertThat(r).isEqualTo(ConsumeResult.SUCCESS);
        verify(runner, never()).run(7777L);
    }

    @Test
    void runnerThrows_belowLimit_returnsFailureForMqRetry() {
        doThrow(new RuntimeException("boom")).when(runner).run(7777L);

        // 投递次数 1 < max-delivery-attempts(3)：返回 FAILURE 交 broker 重投（不落死信）
        ConsumeResult r = consumer.consume(mv(msg(7777L), 1));

        assertThat(r).isEqualTo(ConsumeResult.FAILURE);
        verify(deadLetterRecorder, never()).record(any());
        // 真实 guard 会在异常时 release（见 RedisIdempotencyGuardTest），允许 MQ 重投重试
    }

    @Test
    void tenantInactive_skipsRun() {
        when(activityGuard.isActive(any())).thenReturn(false);

        ConsumeResult r = consumer.consume(mv(msg(7777L), 1));

        assertThat(r).isEqualTo(ConsumeResult.SUCCESS);
        verify(runner, never()).run(7777L);
    }

    @Test
    void poisonBody_recordedToDeadLetter_acked() {
        // 解析不了的毒丸 body：落死信（source=poison_fast_fail）并返回 SUCCESS（ack，不进 broker 重试）
        ConsumeResult r = consumer.consume(mv("{not-json", 1));

        assertThat(r).isEqualTo(ConsumeResult.SUCCESS);
        org.mockito.ArgumentCaptor<com.wensaas.core.impl.mq.DeadLetterRecord> cap =
                org.mockito.ArgumentCaptor.forClass(com.wensaas.core.impl.mq.DeadLetterRecord.class);
        verify(deadLetterRecorder).record(cap.capture());
        assertThat(cap.getValue().source())
                .isEqualTo(com.wensaas.core.impl.mq.DeadLetterRecord.SOURCE_POISON);
        assertThat(cap.getValue().consumerGroup())
                .isEqualTo(props.getExecuteConsumerGroup());
        verify(runner, never()).run(anyLong());
    }
}
