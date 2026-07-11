package com.wensaas.core.impl.mq;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_mq_dead_letter} 表实体（MQ 死信兜底，架构审查 M7）。
 *
 * <p>两条来源（均由消费链路内 {@link DefaultMqConsumeSupport} 落库）：
 * <ul>
 *   <li>{@code source=retry_exhausted}：瞬时异常重试到 {@code max-delivery-attempts} 仍失败；</li>
 *   <li>{@code source=poison_fast_fail}：消费侧判定为不可重试（解析失败 / 非法参数 /
 *       {@link com.wensaas.core.api.mq.NonRetryableConsumeException}），不浪费 broker 重试直接落库。</li>
 * </ul>
 *
 * <p>平台表：进 platform-only-tables 白名单，{@code tenant_id} 仅作过滤列、不被 TenantLine 注入
 * （死信跨租户、消费线程无租户上下文）。不软删、不用 @TableLogic。
 */
@TableName("sys_mq_dead_letter")
@Getter
@Setter
public class SysMqDeadLetter {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String consumerGroup;
    private String originalTopic;
    private String msgId;
    private String msgKeys;
    private Long tenantId;
    private String body;
    private Integer reconsumeTimes;
    private LocalDateTime bornAt;
    private String exceptionSummary;
    private String source;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
    private Long handledBy;

}
