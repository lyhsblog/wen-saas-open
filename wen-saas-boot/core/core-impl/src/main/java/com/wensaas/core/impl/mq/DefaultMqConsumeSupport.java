package com.wensaas.core.impl.mq;

import com.alibaba.fastjson2.JSONException;
import com.wensaas.core.api.mq.MqConsumeSupport;
import com.wensaas.core.api.mq.NonRetryableConsumeException;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;

/**
 * {@link MqConsumeSupport} 唯一实现：毒丸判定 + 死信落库分流。
 *
 * <p>三态契约见接口文档；本类持有落库回调（{@link DeadLetterRecorder}）与
 * {@link MqDeadLetterProperties}（重试上限），二者为 core-impl 内部件，不对业务模块暴露。
 */
public class DefaultMqConsumeSupport implements MqConsumeSupport {

    private final DeadLetterRecorder recorder;
    private final MqDeadLetterProperties props;

    public DefaultMqConsumeSupport(DeadLetterRecorder recorder, MqDeadLetterProperties props) {
        this.recorder = recorder;
        this.props = props;
    }

    /** cause 链最大回溯深度，兜底任意环（含构造出的双向环），避免死循环。 */
    private static final int MAX_CAUSE_DEPTH = 16;

    /**
     * 沿 cause 链判定是否毒丸（不可重试）；深度封顶兜底环。毒丸类型：
     * {@link NonRetryableConsumeException}（显式声明）、{@link JSONException}（解析失败）、
     * {@link IllegalArgumentException}（非法参数）。
     */
    @Override
    public boolean isPoison(Throwable t) {
        Throwable c = t;
        for (int depth = 0; c != null && depth < MAX_CAUSE_DEPTH; depth++, c = c.getCause()) {
            if (c instanceof NonRetryableConsumeException
                    || c instanceof JSONException
                    || c instanceof IllegalArgumentException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ConsumeResult onConsumeFailure(String consumerGroup, String topic, MessageView mv,
                                          String body, RuntimeException e) {
        if (isPoison(e)) {
            recorder.record(DeadLetterRecord.poison(consumerGroup, topic, body, e));
            return ConsumeResult.SUCCESS;
        }
        if (mv.getDeliveryAttempt() >= props.getMaxDeliveryAttempts()) {
            recorder.record(DeadLetterRecord.retryExhausted(consumerGroup, topic, mv, body, e));
            return ConsumeResult.SUCCESS;
        }
        return ConsumeResult.FAILURE;
    }
}
