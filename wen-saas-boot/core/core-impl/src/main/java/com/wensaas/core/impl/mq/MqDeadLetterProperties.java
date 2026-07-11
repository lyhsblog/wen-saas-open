package com.wensaas.core.impl.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * MQ 死信兜底配置。
 *
 * <pre>
 * wen-saas-boot:
 *   mq:
 *     dead-letter:
 *       enabled: true
 *       max-delivery-attempts: 3
 *       body-truncate-bytes: 8000
 * </pre>
 */
@ConfigurationProperties(prefix = "wen-saas-boot.mq.dead-letter")
@Getter
@Setter
public class MqDeadLetterProperties {

    /** 总开关：关闭则毒丸 / 重试耗尽都不落库（仅日志）。默认开。 */
    private boolean enabled = true;

    /** 瞬时异常的客户端重试上限：投递次数达此值仍失败即落死信并 ack（不再交 broker 重投）。 */
    private int maxDeliveryAttempts = 3;

    /** 落库 body 截断字节数（死信体可能很大，避免撑爆 TEXT 列与日志）。 */
    private int bodyTruncateBytes = 8000;

}
