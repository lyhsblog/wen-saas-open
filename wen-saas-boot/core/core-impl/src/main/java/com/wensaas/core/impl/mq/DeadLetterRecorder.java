package com.wensaas.core.impl.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;

/**
 * 死信落库 + 告警 + 指标。
 *
 * <p>两条路径共用：重试耗尽（{@code source=retry_exhausted}）与毒丸快失（{@code source=poison_fast_fail}）。
 * 落库 + 稳定标记 {@code mq.dead-letter} 的 {@code log.error}（供日志告警）+ Micrometer counter
 * （{@code mq.dead.letter}，按 {@code consumer_group}/{@code source} 维度，接 Prometheus）。
 *
 * <p><b>不造环</b>：落库 / 指标失败只 {@code log.error} 不抛——本类在消费线程内执行，
 * 抛出会把消息打回重试循环。
 */
public class DeadLetterRecorder {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterRecorder.class);

    private final SysMqDeadLetterMapper mapper;
    private final MqDeadLetterProperties props;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    public DeadLetterRecorder(SysMqDeadLetterMapper mapper, MqDeadLetterProperties props,
                              ObjectProvider<MeterRegistry> meterRegistry) {
        this.mapper = mapper;
        this.props = props;
        this.meterRegistry = meterRegistry;
    }

    public void record(DeadLetterRecord r) {
        if (!props.isEnabled()) {
            // 关闭落库时仍记一条日志，死信不至于无声消失
            log.error("mq.dead-letter (persistence disabled) group={} topic={} source={} summary={}",
                    r.consumerGroup(), r.originalTopic(), r.source(), r.exceptionSummary());
            return;
        }
        try {
            SysMqDeadLetter row = new SysMqDeadLetter();
            row.setId(IdWorker.getId());
            row.setConsumerGroup(r.consumerGroup());
            row.setOriginalTopic(r.originalTopic());
            row.setMsgId(r.msgId());
            row.setMsgKeys(r.msgKeys());
            row.setTenantId(resolveTenantId(r.body()));
            row.setBody(truncate(r.body(), props.getBodyTruncateBytes()));
            row.setReconsumeTimes(r.reconsumeTimes());
            row.setBornAt(r.bornAt());
            row.setExceptionSummary(r.exceptionSummary());
            row.setSource(r.source());
            row.setStatus("new");
            row.setCreatedAt(LocalDateTime.now());
            mapper.insert(row);

            // 稳定标记，供日志告警订阅
            log.error("mq.dead-letter group={} topic={} source={} msgId={} reconsume={} tenantId={} summary={}",
                    r.consumerGroup(), r.originalTopic(), r.source(), r.msgId(),
                    r.reconsumeTimes(), row.getTenantId(), r.exceptionSummary());
            incrementCounter(r.consumerGroup(), r.source());
        } catch (RuntimeException e) {
            // 兜底：监控自身别造环。落库/指标失败只记日志，死信至少留在日志里。
            log.error("mq.dead-letter.record.fail group={} source={} reason={} body={}",
                    r.consumerGroup(), r.source(), e.getMessage(), r.body(), e);
        }
    }

    /** 尽力从 body 解析 tenantId；结构不符/解析失败填 0（平台表，不做隔离）。 */
    private long resolveTenantId(String body) {
        if (body == null || body.isBlank()) return 0L;
        try {
            JSONObject obj = JSON.parseObject(body);
            Long tid = obj == null ? null : obj.getLong("tenantId");
            return tid == null ? 0L : tid;
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private void incrementCounter(String group, String source) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null) {
            return;  // 测试/无 actuator 环境：优雅跳过
        }
        Counter.builder("mq.dead.letter")
                .description("MQ 死信落库计数（M7）")
                .tag("consumer_group", group == null ? "unknown" : group)
                .tag("source", source == null ? "unknown" : source)
                .register(registry)
                .increment();
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max);
    }
}
