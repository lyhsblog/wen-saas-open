package com.wensaas.core.impl.mq;

import com.wensaas.core.api.mq.MqConsumeSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQ 消费可靠性基建装配。
 *
 * <p>{@link DeadLetterRecorder} + {@link MqConsumeSupport}（接口在 core-api，供业务模块消费者注入；
 * 实现为 {@link DefaultMqConsumeSupport}）常开（10 个 CLUSTERING 消费者的失败分流依赖它们）。
 * 死信落库内聚在消费链路：毒丸即 ack 落库；瞬时异常达 {@code max-delivery-attempts} 落库并 ack，
 * 死信表为唯一真相源，无独立 DLQ 监控消费者。总开关 {@code wen-saas-boot.mq.dead-letter.enabled} 控制是否真正落库。
 *
 * <p><b>消费者总开关</b> {@code wen-saas-boot.mq.consumers-enabled}（默认 true）：置 false 时全部 10 个
 * {@code @RocketMQMessageListener} 消费者 bean 不装配，producer/publisher 侧不受影响（仍正常发消息，
 * 由其他进程消费）。两个用途：一是「只发不收」部署形态（Web 节点只发、独立 worker 节点消费）；
 * 二是集成测试上下文——每个 Spring 上下文起 10 个真实 push consumer 既拖慢启动，缓存上下文堆积的
 * 大量活消费者还会挤占 topic 路由，测试 yml 关掉后只保留 producer 链路。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MqDeadLetterProperties.class)
public class MqReliabilityConfiguration {

    @Bean
    public DeadLetterRecorder deadLetterRecorder(SysMqDeadLetterMapper mapper,
                                                 MqDeadLetterProperties props,
                                                 ObjectProvider<MeterRegistry> meterRegistry) {
        return new DeadLetterRecorder(mapper, props, meterRegistry);
    }

    @Bean
    public MqConsumeSupport mqConsumeSupport(DeadLetterRecorder recorder, MqDeadLetterProperties props) {
        return new DefaultMqConsumeSupport(recorder, props);
    }
}
