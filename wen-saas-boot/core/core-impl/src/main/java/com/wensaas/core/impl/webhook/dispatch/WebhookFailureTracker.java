package com.wensaas.core.impl.webhook.dispatch;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.impl.webhook.SysWebhook;
import com.wensaas.core.impl.webhook.SysWebhookMapper;
import com.wensaas.core.impl.webhook.WebhookProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Webhook 连续失败计数与自动停用。
 *
 * <p>按「事件」粒度计数：单个事件的多次退避重试只在写下 {@code abandoned} 终态时 +1，
 * 任意一次投递成功即清零。连续失败达到 {@code auto-disable-threshold} 时把订阅置
 * {@code disabled} 并站内信告警租户主管理员（阈值 0 = 关闭本机制）。
 *
 * <p>计数走单条 {@code UPDATE ... RETURNING} 原子自增——同一批事件的失败终态经常同时到达
 * （同一退避序列），读改写会丢计数；停用是条件更新（仅 active→disabled），并发下只有
 * 完成停用的那个调用方发告警，不重复通知。调用方（Sender 消费者）已处于目标租户的
 * {@code TenantContext} 内。
 */
public class WebhookFailureTracker {

    private static final Logger log = LoggerFactory.getLogger(WebhookFailureTracker.class);

    /** 自动停用告警模板 code（平台默认模板，迁移预置）。 */
    static final String TPL_AUTO_DISABLED = "sys.webhook.auto_disabled";

    private final SysWebhookMapper webhookMapper;
    private final WebhookProperties props;
    private final MessageService messageService;
    private final TenantQueryService tenantQuery;

    public WebhookFailureTracker(SysWebhookMapper webhookMapper, WebhookProperties props,
                                 MessageService messageService, TenantQueryService tenantQuery) {
        this.webhookMapper = webhookMapper;
        this.props = props;
        this.messageService = messageService;
        this.tenantQuery = tenantQuery;
    }

    public void onSuccess(SysWebhook hook) {
        if (hook.getConsecutiveFailures() == null || hook.getConsecutiveFailures() == 0) {
            return;
        }
        webhookMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysWebhook>()
                .eq(SysWebhook::getId, hook.getId())
                .set(SysWebhook::getConsecutiveFailures, 0)
                .set(SysWebhook::getUpdatedAt, LocalDateTime.now()));
    }

    /** 一个事件重试穷尽（abandoned）后调用。 */
    public void onAbandoned(SysWebhook hook) {
        int failures = webhookMapper.incrementConsecutiveFailures(hook.getId());
        int threshold = props.getAutoDisableThreshold();
        if (threshold <= 0 || failures < threshold) {
            return;
        }
        if (webhookMapper.disableIfActive(hook.getId()) > 0) {
            log.warn("webhook.auto-disabled webhookId={} tenantId={} consecutiveFailures={}",
                    hook.getId(), hook.getTenantId(), failures);
            notifyAdmin(hook, failures);
        }
    }

    private void notifyAdmin(SysWebhook hook, int failures) {
        try {
            TenantQueryService.TenantInfo tenant = tenantQuery.tenantById(hook.getTenantId());
            if (tenant == null || tenant.mainAdminUserId() == null) {
                return;
            }
            messageService.send(MessageSendRequest.builder(TPL_AUTO_DISABLED)
                    .variables(Map.of("webhookName", hook.getName(), "failures", failures))
                    .recipients(List.of(RecipientSpec.member(tenant.mainAdminUserId())))
                    .channels(List.of(Channel.STATION))
                    .category(MessageCategory.SYSTEM)
                    .priority(Priority.IMPORTANT)
                    .build());
        } catch (RuntimeException e) {
            // 告警失败不影响停用主流程
            log.warn("webhook.auto-disabled.notify.fail webhookId={} reason={}", hook.getId(), e.getMessage());
        }
    }
}
