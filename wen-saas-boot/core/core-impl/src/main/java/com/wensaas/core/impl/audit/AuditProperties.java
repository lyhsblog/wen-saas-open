package com.wensaas.core.impl.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * 操作日志框架配置。
 *
 * <pre>
 * wen-saas-boot:
 *   audit:
 *     enabled: true
 *     operation-topic: wen.audit.operation
 *     login-topic: wen.audit.login
 *     operation-consumer-group: wen-saas-boot-audit-op
 *     login-consumer-group: wen-saas-boot-audit-login
 *     batch-size: 100
 *     batch-flush-interval: 5s
 * </pre>
 *
 * <p>注：消费重试上限由 {@code wen-saas-boot.mq.dead-letter.max-delivery-attempts} 统一控制，
 * 达上限的消息由消费链路落 {@code sys_mq_dead_letter} 兜底。
 */
@ConfigurationProperties(prefix = "wen-saas-boot.audit")
@Getter
@Setter
public class AuditProperties {

    private boolean enabled = true;
    // RocketMQ topic 名约束：^[%|a-zA-Z0-9_-]+$，不允许点号；用下划线分隔
    private String operationTopic = "wen_audit_operation";
    private String loginTopic = "wen_audit_login";
    private String operationConsumerGroup = "wen-saas-boot-audit-op";
    private String loginConsumerGroup = "wen-saas-boot-audit-login";
    private int batchSize = 100;
    private Duration batchFlushInterval = Duration.ofSeconds(5);

}
