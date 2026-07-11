package com.wensaas.core.impl.notification;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.MessageStatus;
import com.wensaas.core.api.notification.send.MessageTaskId;
import com.wensaas.core.api.notification.send.MessageTaskStatus;
import com.wensaas.core.api.notification.send.NotificationCategory;
import com.wensaas.core.api.notification.send.NotificationException;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.send.Recipient;
import com.wensaas.core.api.notification.template.TemplateContent;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.wensaas.core.impl.notification.delivery.MessageEnvelope;
import com.wensaas.core.impl.notification.delivery.NotificationPublisher;
import com.wensaas.core.impl.notification.delivery.RecipientResolver;
import com.wensaas.core.impl.notification.delivery.SysMessageTask;
import com.wensaas.core.impl.notification.delivery.SysMessageTaskMapper;
import com.wensaas.core.impl.notification.template.TemplateResolver;
import com.wensaas.core.impl.notification.template.TemplateSnapshot;
import com.wensaas.core.impl.notification.template.VariableRenderer;

/**
 * 默认 {@link MessageService} 实现：
 * <ol>
 *   <li>解析模板（tenant_id 优先 → 0 默认；走 {@link TemplateResolver} + Redis 缓存）</li>
 *   <li>变量替换（{@link VariableRenderer}）</li>
 *   <li>展开收件人（{@link RecipientResolver}）</li>
 *   <li>计算实际通道（request.channels 或模板 default_channels；站内信兜底）</li>
 *   <li>按 收件人 × 通道 写 sys_message_task PENDING 行</li>
 *   <li>逐条投递 RocketMQ；失败仅记日志（v1 不本地降级）</li>
 *   <li>返回业务 taskId</li>
 * </ol>
 */
public class MessageServiceImpl implements MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageServiceImpl.class);

    private final TemplateResolver templateResolver;
    private final RecipientResolver recipientResolver;
    private final NotificationPublisher publisher;
    private final SysMessageTaskMapper taskMapper;

    public MessageServiceImpl(TemplateResolver templateResolver,
                               RecipientResolver recipientResolver,
                               NotificationPublisher publisher,
                               SysMessageTaskMapper taskMapper) {
        this.templateResolver = templateResolver;
        this.recipientResolver = recipientResolver;
        this.publisher = publisher;
        this.taskMapper = taskMapper;
    }

    @Override
    public MessageTaskId send(MessageSendRequest request) {
        long tenantId = TenantContext.require();
        TemplateSnapshot tpl = templateResolver.resolve(tenantId, request.templateCode())
                .orElseThrow(() -> new NotificationException(
                        NotificationException.Code.TEMPLATE_NOT_FOUND,
                        "通知模板未找到: " + request.templateCode()));

        if (tpl.suppressed()) {
            // 租户主管理员已停用该模板：静默跳过，不投递任何通道（含站内信兜底），
            // 也不向业务方抛错——返回一个不含任务行的 taskId。
            if (log.isDebugEnabled()) {
                log.debug("notify.send.suppressed template={} tenant={}", tpl.templateCode(), tenantId);
            }
            return new MessageTaskId(nextTaskId());
        }

        List<Recipient> recipients = recipientResolver.resolve(request.recipients());
        if (recipients.isEmpty()) {
            throw new NotificationException(
                    NotificationException.Code.NO_RECIPIENT_RESOLVED,
                    "通知收件人解析为空: " + request.templateCode());
        }

        List<Channel> channels = effectiveChannels(request, tpl);
        if (channels.isEmpty()) {
            throw new NotificationException(
                    NotificationException.Code.INVALID_CHANNEL_CONFIG,
                    "通知通道解析为空: " + request.templateCode());
        }

        Priority effectivePriority = request.priority() != null ? request.priority() : tpl.priority();
        String taskId = nextTaskId();
        String requestId = RequestContext.get();
        String sourceModule = moduleFromTemplateCode(tpl.templateCode());
        NotificationCategory notificationCategory = parseNotificationCategory(tpl.notificationCategory());

        List<SysMessageTask> rows = new ArrayList<>();
        List<MessageEnvelope> envelopes = new ArrayList<>();
        for (Channel channel : channels) {
            TemplateContent content = tpl.contents().get(channel);
            if (content == null) {
                throw new NotificationException(
                        NotificationException.Code.TEMPLATE_CHANNEL_MISSING,
                        "模板 " + tpl.templateCode() + " 缺少通道 " + channel + " 的文案");
            }
            String renderedTitle = VariableRenderer.render(content.title(), request.variables());
            String renderedBody = VariableRenderer.render(content.body(), request.variables());

            for (Recipient r : recipients) {
                SysMessageTask row = newTaskRow(tenantId, taskId, tpl.templateCode(),
                        channel, effectivePriority, r.userId(),
                        renderedTitle, renderedBody, request);
                rows.add(row);
                envelopes.add(new MessageEnvelope(
                        row.getId(),
                        taskId,
                        tenantId,
                        channel,
                        r.userId(),
                        tpl.templateCode(),
                        request.category(),
                        notificationCategory,
                        effectivePriority,
                        renderedTitle,
                        renderedBody,
                        request.link(),
                        request.relatedType(),
                        request.relatedId(),
                        sourceModule,
                        request.variables(),
                        requestId,
                        1));
            }
        }

        // 一次性批量落库（避免广播按 收件人 × 通道 逐行 INSERT；id 已由 IdWorker 预生成，envelope 不依赖回填）
        taskMapper.insertBatch(rows);

        for (MessageEnvelope env : envelopes) {
            publisher.publish(env);
        }

        if (log.isDebugEnabled()) {
            log.debug("notify.send template={} recipients={} channels={} taskId={}",
                    tpl.templateCode(), recipients.size(), channels, taskId);
        }
        return new MessageTaskId(taskId);
    }

    @Override
    public Optional<MessageTaskStatus> status(MessageTaskId taskId) {
        List<SysMessageTask> rows = taskMapper.selectByTaskId(taskId.value());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        int pending = 0, sent = 0, failed = 0, skipped = 0;
        List<MessageTaskStatus.Entry> entries = new ArrayList<>(rows.size());
        for (SysMessageTask r : rows) {
            MessageStatus s = MessageStatus.valueOf(r.getStatus());
            switch (s) {
                case PENDING -> pending++;
                case SENT -> sent++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
            entries.add(new MessageTaskStatus.Entry(
                    r.getRecipientId(),
                    Channel.valueOf(r.getChannel()),
                    s,
                    r.getRetryCount() == null ? 0 : r.getRetryCount(),
                    r.getErrorReason()));
        }
        return Optional.of(new MessageTaskStatus(taskId.value(), rows.size(),
                pending, sent, failed, skipped, entries));
    }

    /**
     * 计算实际通道。
     * <ul>
     *   <li>request.channels 非空 → 用它；否则用 tpl.defaultChannels</li>
     *   <li>默认 STATION 兜底（除非 request.noStationFallback=true 显式拒绝）</li>
     *   <li>同 channel 去重，保留首次出现顺序</li>
     * </ul>
     */
    static List<Channel> effectiveChannels(MessageSendRequest request, TemplateSnapshot tpl) {
        Set<Channel> ordered = new LinkedHashSet<>();
        List<Channel> source = request.channels().isEmpty() ? tpl.defaultChannels() : request.channels();
        ordered.addAll(source);
        if (!request.noStationFallback()) {
            ordered.add(Channel.STATION);
        }
        // 校验：选定通道都必须在模板 contents 中有文案（如 noStationFallback=false 但模板无 STATION，
        // 该 STATION 会在 send 阶段抛 TEMPLATE_CHANNEL_MISSING）
        return new ArrayList<>(ordered);
    }

    private static SysMessageTask newTaskRow(long tenantId, String taskId, String templateCode,
                                              Channel channel, Priority priority, long recipientId,
                                              String title, String body, MessageSendRequest request) {
        SysMessageTask row = new SysMessageTask();
        row.setId(IdWorker.getId());
        row.setTenantId(tenantId);
        row.setTaskId(taskId);
        row.setTemplateCode(templateCode);
        row.setRecipientId(recipientId);
        row.setChannel(channel.name());
        row.setPriority(priority.name());
        row.setRenderedTitle(title);
        row.setRenderedBody(body);
        row.setRelatedType(request.relatedType());
        row.setRelatedId(request.relatedId());
        row.setLink(request.link());
        row.setStatus(MessageStatus.PENDING.name());
        row.setRetryCount(0);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        return row;
    }

    private static String nextTaskId() {
        return "nt-" + IdWorker.getId();
    }

    /** 从 templateCode "{module}.{template_name}" 取前缀；无点号时返回 null。 */
    static String moduleFromTemplateCode(String code) {
        if (code == null) return null;
        int idx = code.indexOf('.');
        return idx > 0 ? code.substring(0, idx) : null;
    }

    /** 模板声明的通知类别字符串 → 枚举；空或非法值返回 null（不参与偏好过滤）。 */
    static NotificationCategory parseNotificationCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return NotificationCategory.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            log.warn("notify.template.unknownCategory value={}", raw);
            return null;
        }
    }
}
