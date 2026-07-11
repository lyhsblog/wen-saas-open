package com.wensaas.core.impl.ops;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.DeliveryResult;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageStatus;
import com.wensaas.core.api.notification.send.Recipient;
import com.wensaas.core.api.notification.template.RenderedMessage;
import com.wensaas.core.impl.notification.adapter.EmailChannelAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * 开通租户时给主管理员邮箱直投「设置密码 / 激活账号」邮件。
 *
 * <p>不走 {@code MessageService}：开通发生在运营上下文（tenant_id=0），而收件人是新租户成员，
 * MessageService 的收件人解析按当前租户注入 tenant_id、跨租户取不到；且开通时已握有主管理员明文邮箱，
 * 无需二次解析。故直接构造 {@link RenderedMessage} 调 EMAIL 通道适配器同步投递（与验证码同一手法）。
 * 邮件的 noop 开关与当日发送量闸由 {@link EmailChannelAdapter} 内聚。
 */
public class ActivationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ActivationEmailSender.class);

    private final EmailChannelAdapter emailAdapter;

    public ActivationEmailSender(EmailChannelAdapter emailAdapter) {
        this.emailAdapter = emailAdapter;
    }

    /**
     * 发送激活邮件。失败仅记日志、不抛出——开通主流程不因邮件投递失败而回滚。
     */
    public void sendActivationLink(long tenantId, long adminUserId, String toEmail,
                                   String tenantName, String username,
                                   String activationUrl, int expireHours) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        if (emailAdapter == null) {
            log.warn("activation.email.skip EMAIL 通道未装配（通知模块关闭？） tenantId={}", tenantId);
            return;
        }
        try {
            String subject = "【Wen SaaS】您的账号已开通，请设置登录密码";
            String body = """
                    您好，

                    您的租户「%s」已开通。请使用以下信息设置登录密码并激活账号：

                    登录账号：%s
                    设置密码链接（%d 小时内有效）：
                    %s

                    如非本人操作，请忽略本邮件。

                    —— Wen SaaS"""
                    .formatted(tenantName, username, expireHours, activationUrl);

            Recipient recipient = new Recipient(adminUserId, tenantId, toEmail, null);
            RenderedMessage message = new RenderedMessage(tenantId, UUID.randomUUID().toString(),
                    Channel.EMAIL, "tenant.activation", MessageCategory.SYSTEM, null,
                    subject, body, activationUrl, null, null, "sys", Map.of());

            if (!emailAdapter.canDeliver(recipient)) {
                log.warn("activation.email.skip 收件人邮箱不可触达 tenantId={} userId={}", tenantId, adminUserId);
                return;
            }
            DeliveryResult result = emailAdapter.deliver(message, recipient);
            if (result.status() != MessageStatus.SENT) {
                log.warn("activation.email.failed tenantId={} userId={} reason={}",
                        tenantId, adminUserId, result.errorReason());
            } else {
                log.info("activation.email.sent tenantId={} userId={}", tenantId, adminUserId);
            }
        } catch (Exception e) {
            log.warn("activation.email.error tenantId={} userId={}: {}", tenantId, adminUserId, e.toString());
        }
    }
}
