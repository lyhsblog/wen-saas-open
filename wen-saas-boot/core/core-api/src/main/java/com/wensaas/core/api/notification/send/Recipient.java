package com.wensaas.core.api.notification.send;

/**
 * 解析展开后的单个收件人快照。
 *
 * <p>由 MessageService 收件人解析阶段产出，喂给 {@link ChannelAdapter#canDeliver}。
 *
 * @param userId   用户 ID
 * @param tenantId 用户租户 ID
 * @param email    邮箱（可空）
 * @param phone    手机号（可空）
 */
public record Recipient(long userId, long tenantId, String email, String phone) { }
