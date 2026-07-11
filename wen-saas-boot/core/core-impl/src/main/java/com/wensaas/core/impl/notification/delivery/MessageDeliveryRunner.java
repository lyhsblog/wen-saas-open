package com.wensaas.core.impl.notification.delivery;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.ChannelAdapter;
import com.wensaas.core.api.notification.send.DeliveryResult;
import com.wensaas.core.api.notification.send.MessageStatus;
import com.wensaas.core.api.notification.NotificationPreferenceService;
import com.wensaas.core.api.notification.send.Recipient;
import com.wensaas.core.api.notification.template.RenderedMessage;
import com.wensaas.core.api.idempotent.IdempotencyGuard;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.auth.user.UserDeliveryContactQuery;
import com.wensaas.core.api.auth.user.UserDeliveryContactQuery.UserContact;
import com.wensaas.core.impl.tenant.web.TenantActivityGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import com.wensaas.core.impl.notification.NotificationProperties;

/**
 * 通道消费者公共执行器：解析 JSON → 还原上下文 → 路由 adapter → 写 task 终态 →
 * 失败时按指数退避重试。所有 4 个 {@code *Consumer} 都委托到这里。
 *
 * <p>路由由 {@link ChannelAdapter#channel()} 自报；不存在对应 adapter 时直接标 FAILED。
 * 上下文恢复仅 tenantId + requestId（消息源已带）；UserContext 不透传。
 *
 * <p><b>幂等去重（M6）</b>：{@link #handle} 经 {@link IdempotencyGuard#runOnce} 守卫，去重维度为
 * 「同一条投递消息」= {@code channel:msgTaskId:attempt}。这挡住 MQ at-least-once 对<em>同一条</em>消息的
 * 重复投递（避免重复外发邮件/短信/IM、重复写 task 终态）；失败重试发出的是 {@code attempt+1} 的新消息、
 * 去重键不同，正常放行。投递逻辑内部已把所有异常收敛为 task 终态（不抛），故 {@code runOnce} 不会回滚——
 * 一次完成的投递（无论 sent/failed/skipped）不应被重投重做。
 */
public class MessageDeliveryRunner {

    private static final Logger log = LoggerFactory.getLogger(MessageDeliveryRunner.class);

    /** 去重域：通知投递。 */
    static final String DEDUP_SCOPE = "notify-delivery";

    private final Map<Channel, ChannelAdapter> adapters;
    private final SysMessageTaskMapper taskMapper;
    private final UserDeliveryContactQuery deliveryContacts;
    private final NotificationPublisher publisher;
    private final NotificationProperties props;
    private final TenantActivityGuard activityGuard;
    private final NotificationPreferenceService preferenceService;
    private final IdempotencyGuard idempotency;

    public MessageDeliveryRunner(Map<Channel, ChannelAdapter> adapters,
                                  SysMessageTaskMapper taskMapper,
                                  UserDeliveryContactQuery deliveryContacts,
                                  NotificationPublisher publisher,
                                  NotificationProperties props,
                                  TenantActivityGuard activityGuard,
                                  NotificationPreferenceService preferenceService,
                                  IdempotencyGuard idempotency) {
        this.adapters = adapters;
        this.taskMapper = taskMapper;
        this.deliveryContacts = deliveryContacts;
        this.publisher = publisher;
        this.props = props;
        this.activityGuard = activityGuard;
        this.preferenceService = preferenceService;
        this.idempotency = idempotency;
    }

    /** 消费入口：幂等去重守卫，同一条消息（channel:msgTaskId:attempt）只投一次。 */
    public void handle(String json) {
        MessageEnvelope env = JSON.parseObject(json, MessageEnvelope.class);
        String dedupId = env.channel() + ":" + env.msgTaskId() + ":" + env.attempt();
        boolean executed = idempotency.runOnce(DEDUP_SCOPE, dedupId, props.getDedupTtl(),
                () -> deliverOnce(env));
        if (!executed) {
            log.info("notify.skip.duplicate msgTaskId={} channel={} attempt={}",
                    env.msgTaskId(), env.channel(), env.attempt());
        }
    }

    private void deliverOnce(MessageEnvelope env) {
        try {
            TenantContext.set(env.tenantId());
            if (env.requestId() != null) {
                RequestContext.restoreFromMessage(env.requestId());
            }
            if (!activityGuard.isActive(env.tenantId())) {
                log.info("notify.skip.tenant-inactive msgTaskId={} channel={} tenantId={}",
                        env.msgTaskId(), env.channel(), env.tenantId());
                taskMapper.markSkipped(env.msgTaskId(), "tenant-inactive");
                return;
            }
            doDeliver(env);
        } catch (RuntimeException e) {
            // 解析 / 数据 异常 → 视为 FAILED，避免无限重试 + log.error
            log.error("notify.consume.fail msgTaskId={} channel={} reason={}",
                    env.msgTaskId(), env.channel(), e.getMessage(), e);
            taskMapper.markFailed(env.msgTaskId(), env.attempt(), trim(e.toString()), true);
        } finally {
            TenantContext.clear();
            RequestContext.clear();
        }
    }

    private void doDeliver(MessageEnvelope env) {
        ChannelAdapter adapter = adapters.get(env.channel());
        if (adapter == null) {
            taskMapper.markFailed(env.msgTaskId(), env.attempt(),
                    "no adapter for channel: " + env.channel(), true);
            return;
        }

        Recipient recipient = loadRecipient(env);
        if (recipient == null) {
            taskMapper.markSkipped(env.msgTaskId(), "recipient not found");
            return;
        }

        if (!adapter.canDeliver(recipient)) {
            taskMapper.markSkipped(env.msgTaskId(), "channel not deliverable to recipient");
            return;
        }

        // 用户通知偏好过滤：模板声明了类别且非站内信时，按 (用户,类别,通道) 查偏好；关闭则跳过。
        if (env.notificationCategory() != null && env.channel() != Channel.STATION
                && !preferenceService.isChannelEnabled(
                        env.tenantId(), env.recipientId(), env.notificationCategory(), env.channel())) {
            taskMapper.markSkipped(env.msgTaskId(), "user-preference-off");
            return;
        }

        RenderedMessage rendered = toRenderedMessage(env);
        DeliveryResult result;
        try {
            result = adapter.deliver(rendered, recipient);
        } catch (RuntimeException e) {
            result = DeliveryResult.failed(trim(e.toString()));
            log.warn("notify.deliver.throw msgTaskId={} channel={} attempt={}",
                    env.msgTaskId(), env.channel(), env.attempt(), e);
        }

        switch (result.status()) {
            case SENT -> taskMapper.markSent(env.msgTaskId(), env.attempt(), LocalDateTime.now());
            case SKIPPED -> taskMapper.markSkipped(env.msgTaskId(), result.errorReason());
            case FAILED -> handleFailure(env, result);
            default -> throw new IllegalStateException("Unexpected status: " + result.status());
        }
    }

    private void handleFailure(MessageEnvelope env, DeliveryResult result) {
        int attempt = env.attempt();
        int maxAttempts = props.getRetryTimes();
        if (attempt >= maxAttempts) {
            taskMapper.markFailed(env.msgTaskId(), attempt, result.errorReason(), true);
            return;
        }
        // 计算下次退避：backoff[attempt-1]（数组按 0 起；attempt=1 取 backoff[0]）
        Duration[] backoff = props.getRetryBackoff();
        Duration delay = (backoff != null && attempt - 1 < backoff.length)
                ? backoff[attempt - 1]
                : Duration.ofSeconds(30);
        taskMapper.markFailed(env.msgTaskId(), attempt, result.errorReason(), false);
        publisher.republish(env.withAttempt(attempt + 1), delay.toMillis());
        if (log.isDebugEnabled()) {
            log.debug("notify.retry msgTaskId={} channel={} attempt={}→{} delayMs={}",
                    env.msgTaskId(), env.channel(), attempt, attempt + 1, delay.toMillis());
        }
    }

    private Recipient loadRecipient(MessageEnvelope env) {
        // 跨 tenant 查询：consumer 已 TenantContext.set(env.tenantId)，让 MP TenantLine 自动注入
        UserContact c = deliveryContacts.contactForDelivery(env.recipientId());
        if (c == null) {
            return null;
        }
        return new Recipient(c.userId(), c.tenantId(), c.email(), c.mobile());
    }

    private static RenderedMessage toRenderedMessage(MessageEnvelope env) {
        return new RenderedMessage(
                env.tenantId(),
                env.taskId(),
                env.channel(),
                env.templateCode(),
                env.category(),
                env.priority(),
                env.title(),
                env.body(),
                env.link(),
                env.relatedType(),
                env.relatedId(),
                env.sourceModule(),
                env.variables() == null ? new HashMap<>() : env.variables());
    }

    private static String trim(String s) {
        if (s == null) return null;
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }
}
