package com.wensaas.core.impl.webhook;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.event.EventEnvelope;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.file.CredentialCipher;
import com.wensaas.core.impl.openapi.key.ApiKeyCredentialGenerator;
import com.wensaas.core.impl.webhook.dispatch.WebhookSendEnvelope;
import com.wensaas.core.impl.webhook.dispatch.WebhookSendPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 租户端 Webhook 订阅管理（CRUD / 启停 / 密钥轮换 / 测试投递 / 投递日志查询）。
 *
 * <p>signing secret 仅创建 / 轮换时以明文返回一次，落库走 {@link CredentialCipher} AES 加密；
 * 回调地址保存期经 {@link WebhookUrlValidator} 防 SSRF 校验。删除为置 {@code status='deleted'}
 * （保留投递日志关联），列表默认排除。租户隔离由租户线拦截器自动叠加。
 */
public class WebhookAdminService {

    /** 单订阅事件数上限（防误把海量 code 塞进单条 jsonb）。 */
    private static final int MAX_EVENTS = 50;

    private final SysWebhookMapper webhookMapper;
    private final SysWebhookLogMapper logMapper;
    private final CredentialCipher cipher;
    private final WebhookSendPublisher sendPublisher;
    private final WebhookProperties props;

    public WebhookAdminService(SysWebhookMapper webhookMapper, SysWebhookLogMapper logMapper,
                               CredentialCipher cipher, WebhookSendPublisher sendPublisher,
                               WebhookProperties props) {
        this.webhookMapper = webhookMapper;
        this.logMapper = logMapper;
        this.cipher = cipher;
        this.sendPublisher = sendPublisher;
        this.props = props;
    }

    public List<WebhookView> list() {
        List<SysWebhook> rows = webhookMapper.selectList(new LambdaQueryWrapper<SysWebhook>()
                .ne(SysWebhook::getStatus, "deleted")
                .orderByDesc(SysWebhook::getCreatedAt));
        return rows.stream().map(WebhookAdminService::toView).toList();
    }

    public CreatedWebhook create(String name, String targetUrl, List<String> subscribedEvents) {
        WebhookUrlValidator.validate(targetUrl, props.isAllowPrivateTargets());
        List<String> events = normalizeEvents(subscribedEvents);

        String plainSecret = ApiKeyCredentialGenerator.generatePlainSecret();
        LocalDateTime now = LocalDateTime.now();
        SysWebhook row = new SysWebhook();
        row.setId(IdWorker.getId());
        row.setTenantId(TenantContext.require());
        row.setName(name.trim());
        row.setTargetUrl(targetUrl.trim());
        row.setSigningSecretEncrypted(cipher.encrypt(plainSecret));
        row.setSubscribedEvents(events);
        row.setConsecutiveFailures(0);
        row.setStatus("active");
        row.setCreatorId(UserContext.require().userId());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        webhookMapper.insert(row);
        return new CreatedWebhook(row.getId(), row.getName(), plainSecret);
    }

    public WebhookView update(Long id, String name, String targetUrl, List<String> subscribedEvents) {
        SysWebhook row = requireExisting(id);
        WebhookUrlValidator.validate(targetUrl, props.isAllowPrivateTargets());
        row.setName(name.trim());
        row.setTargetUrl(targetUrl.trim());
        row.setSubscribedEvents(normalizeEvents(subscribedEvents));
        row.setUpdatedAt(LocalDateTime.now());
        webhookMapper.updateById(row);
        return toView(row);
    }

    public WebhookView enable(Long id) {
        SysWebhook row = requireExisting(id);
        row.setStatus("active");
        row.setConsecutiveFailures(0);   // 人工重启即重置连续失败计数
        row.setUpdatedAt(LocalDateTime.now());
        webhookMapper.updateById(row);
        return toView(row);
    }

    public WebhookView disable(Long id) {
        SysWebhook row = requireExisting(id);
        row.setStatus("disabled");
        row.setUpdatedAt(LocalDateTime.now());
        webhookMapper.updateById(row);
        return toView(row);
    }

    public WebhookView delete(Long id) {
        SysWebhook row = requireExisting(id);
        row.setStatus("deleted");
        row.setUpdatedAt(LocalDateTime.now());
        webhookMapper.updateById(row);
        return toView(row);
    }

    /** 重新生成 signing secret（旧 secret 立即失效），明文仅本次返回。 */
    public CreatedWebhook rotateSecret(Long id) {
        SysWebhook row = requireExisting(id);
        String plainSecret = ApiKeyCredentialGenerator.generatePlainSecret();
        row.setSigningSecretEncrypted(cipher.encrypt(plainSecret));
        row.setUpdatedAt(LocalDateTime.now());
        webhookMapper.updateById(row);
        return new CreatedWebhook(row.getId(), row.getName(), plainSecret);
    }

    /**
     * 向该订阅投一条 {@code webhook.test} 测试事件，复用真实发送链（签名 / 日志 / 头部与生产一致）。
     * attempt 直接置为重试上限：失败即写 abandoned 终态，不进退避重试（测试不值得重试三次）。
     */
    public TestResult sendTest(Long id) {
        SysWebhook row = requireExisting(id);
        if (!"active".equals(row.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订阅已停用，请先启用再发送测试事件");
        }
        String eventId = String.valueOf(IdWorker.getId());
        EventEnvelope event = new EventEnvelope(
                row.getTenantId(), "webhook.test", eventId, Instant.now(),
                Map.of("message", "这是一条来自 Webhook 管理页的测试事件", "webhookName", row.getName()),
                RequestContext.get());
        sendPublisher.publish(new WebhookSendEnvelope(
                row.getId(), row.getTenantId(), RequestContext.get(), props.getRetryTimes(), event));
        return new TestResult(eventId);
    }

    public LogPage logs(Long id, String finalStatus, long pageNo, long size) {
        requireExisting(id);   // 归属校验（租户线保证跨租户 id 查不到 → 404）
        LambdaQueryWrapper<SysWebhookLog> w = new LambdaQueryWrapper<SysWebhookLog>()
                .eq(SysWebhookLog::getWebhookId, id)
                .orderByDesc(SysWebhookLog::getCreatedAt);
        if (finalStatus != null && !finalStatus.isBlank()) {
            w.eq(SysWebhookLog::getFinalStatus, finalStatus);
        }
        Page<SysWebhookLog> page = logMapper.selectPage(new Page<>(pageNo, size), w);
        List<LogItem> items = page.getRecords().stream()
                .map(r -> new LogItem(r.getId(), r.getEventType(), r.getEventId(), r.getHttpStatus(),
                        r.getDurationMs(), r.getRetryCount(), r.getFinalStatus(),
                        r.getErrorMessage(), r.getResponseBody(), r.getCreatedAt()))
                .toList();
        return new LogPage(items, page.getTotal());
    }

    private SysWebhook requireExisting(Long id) {
        SysWebhook row = webhookMapper.selectById(id);
        if (row == null || "deleted".equals(row.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook 订阅不存在");
        }
        return row;
    }

    private static List<String> normalizeEvents(List<String> events) {
        if (events == null || events.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "至少订阅一个事件");
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String e : events) {
            if (e != null && !e.isBlank()) {
                out.add(e.trim());
            }
        }
        if (out.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "至少订阅一个事件");
        }
        if (out.size() > MAX_EVENTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订阅事件数超过上限 " + MAX_EVENTS);
        }
        return List.copyOf(out);
    }

    private static WebhookView toView(SysWebhook r) {
        return new WebhookView(r.getId(), r.getName(), r.getTargetUrl(), r.getSubscribedEvents(),
                r.getStatus(), r.getConsecutiveFailures(), r.getCreatedAt(), r.getUpdatedAt());
    }

    // ============================== DTO ==============================

    public record WebhookView(Long id, String name, String targetUrl, List<String> subscribedEvents,
                              String status, Integer consecutiveFailures,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 创建 / 轮换密钥的返回：{@code signingSecret} 为明文，仅此一次，前端强提示保存。 */
    public record CreatedWebhook(Long id, String name, String signingSecret) {
    }

    public record TestResult(String eventId) {
    }

    public record LogItem(Long id, String eventType, String eventId, Integer httpStatus,
                          Integer durationMs, Integer retryCount, String finalStatus,
                          String errorMessage, String responseBody, LocalDateTime createdAt) {
    }

    public record LogPage(List<LogItem> records, long total) {
    }
}
