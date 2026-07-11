package com.wensaas.core.api.verification;

import com.wensaas.core.api.notification.send.Channel;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 验证码（OTP）通用能力：生成 → Redis 存储（哈希 + 尝试计数 + TTL）→ 通道同步下发 → 校验消费。
 *
 * <p>是 MFA（短信 / 邮箱通道）与改手机号双通道流的共同地基。下发复用通知底座的
 * {@link com.wensaas.core.api.notification.send.ChannelAdapter}，故 SMS 通道由 noop 切真发时 OTP 自动跟随。
 */
public interface VerificationCodeService {

    /**
     * 生成并下发验证码到指定通道（同步投递）。
     *
     * @param purpose  用途（隔离不同场景的码）
     * @param channel  下发通道（{@link Channel#SMS} / {@link Channel#EMAIL}）
     * @param target   目标（手机号 / 邮箱）
     * @param userId   归属用户 ID
     * @param tenantId 用户租户 ID（运营账号为 0）
     * @return 发送结果（脱敏目标 + 有效期 + 可重发间隔），供前端展示与倒计时
     * @throws VerificationException 限频 / 当日超限 / 通道不可用 / 投递失败
     */
    SentResult send(VerificationPurpose purpose, Channel channel, String target, long userId, long tenantId);

    /**
     * 校验验证码。成功即消费（删除）；连续错误达上限作废该码。
     *
     * @return true=通过；false=码错误 / 不存在 / 已过期
     */
    boolean verify(VerificationPurpose purpose, String target, String code);

    /**
     * @param maskedTarget       脱敏后的目标（如 {@code 138****0000} / {@code a***@b.com}）
     * @param ttlSeconds         验证码有效期（秒）
     * @param resendAfterSeconds 距可再次发送的间隔（秒）
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class SentResult {
        private String maskedTarget;
        private int ttlSeconds;
        private int resendAfterSeconds;

    }
}
