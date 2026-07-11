package com.wensaas.core.impl.audit.pipeline;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.audit.AuditEvent;
import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.audit.LoginAuditEvent;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;

import java.time.Instant;

/**
 * 默认 {@link AuditLogRecorder} 实现：补全公共字段后投递到 {@link AuditEventPublisher}。
 *
 * <p>{@code requestId} 缺失时 fallback {@code "system-" + 雪花}，避免分区表 NOT NULL 列写入失败。
 */
public class AuditLogRecorderImpl implements AuditLogRecorder {

    private final AuditEventPublisher publisher;

    public AuditLogRecorderImpl(AuditEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void record(AuditEvent event) {
        AuditEvent normalized = ensureRequiredFields(event);
        publisher.publish(normalized);
    }

    @Override
    public void recordCreate(String module, String objectType, String objectId, String objectDesc, Object after) {
        publisher.publish(buildEvent(module, "create", objectType, objectId, objectDesc));
    }

    @Override
    public void recordUpdate(String module, String objectType, String objectId, String objectDesc,
                              Object before, Object after) {
        publisher.publish(buildEvent(module, "update", objectType, objectId, objectDesc));
    }

    @Override
    public void recordDelete(String module, String objectType, String objectId, String objectDesc, Object before) {
        publisher.publish(buildEvent(module, "delete", objectType, objectId, objectDesc));
    }

    @Override
    public void recordLogin(LoginAuditEvent event) {
        publisher.publish(ensureLoginRequiredFields(event));
    }

    private AuditEvent ensureRequiredFields(AuditEvent e) {
        Long id = e.id() != null ? e.id() : IdWorker.getId();
        String requestId = e.requestId() != null && !e.requestId().isBlank() ? e.requestId() : fallbackRequestId();
        Instant createdAt = e.createdAt() != null ? e.createdAt() : Instant.now();
        Long operatorId = e.operatorId() != null ? e.operatorId() : 0L;
        return new AuditEvent(id, e.tenantId(), e.targetTenantId(), requestId,
                operatorId, e.operatorName(), e.operatorDeptId(),
                e.module(), e.action(), e.objectType(), e.objectId(), e.objectDesc(),
                e.method(), e.path(), e.statusCode(), e.durationMs(), e.errorMessage(),
                e.ip(), e.userAgent(), createdAt, e.objectRefs());
    }

    private LoginAuditEvent ensureLoginRequiredFields(LoginAuditEvent e) {
        Long id = e.id() != null ? e.id() : IdWorker.getId();
        String requestId = e.requestId() != null && !e.requestId().isBlank() ? e.requestId() : fallbackRequestId();
        Instant createdAt = e.createdAt() != null ? e.createdAt() : Instant.now();
        return new LoginAuditEvent(id, e.tenantId(), requestId,
                e.operatorId(), e.operatorName(), e.operatorDeptId(),
                e.account(), e.loginMethod(), e.action(), e.result(), e.failReason(),
                e.ip(), e.userAgent(), createdAt);
    }

    private AuditEvent buildEvent(String module, String action, String objectType,
                                   String objectId, String objectDesc) {
        UserPrincipal p = UserContext.get();
        Long tenantId = currentTenantId(p);
        return new AuditEvent(
                IdWorker.getId(),
                tenantId,
                null,
                fallbackRequestId(),
                p == null ? 0L : p.userId(),
                operatorName(p),
                p == null ? null : p.mainDeptId(),
                module,
                action,
                objectType,
                objectId,
                objectDesc,
                null, null, 200, null, null, null, null,
                Instant.now());
    }

    // 操作人展示名与 @Auditable 切面同口径：优先 realName（登录态已快照），缺失回退账号。
    // 编程式 recordCreate/Update/Delete 走此路径，避免直接用 username 造成"账号风格"与切面日志不一致。
    private static String operatorName(UserPrincipal p) {
        if (p == null) {
            return null;
        }
        return (p.realName() != null && !p.realName().isBlank()) ? p.realName() : p.username();
    }

    private static Long currentTenantId(UserPrincipal p) {
        Long t = TenantContext.get();
        if (t != null) {
            return t;
        }
        return p == null ? null : p.tenantId();
    }

    private static String fallbackRequestId() {
        String rid = RequestContext.get();
        return (rid != null && !rid.isBlank()) ? rid : "system-" + IdWorker.getId();
    }
}
