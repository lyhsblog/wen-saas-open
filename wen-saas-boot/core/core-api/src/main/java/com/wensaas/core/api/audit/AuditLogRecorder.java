package com.wensaas.core.api.audit;

/**
 * 操作日志写入入口。业务方通过它主动记录（CRUD / 敏感读 / 业务级动作）；
 * AOP 切面 {@code AuditableAspect} 也调用其 {@link #record(AuditEvent)} 投递。
 *
 * <p>实现走 RocketMQ 异步管道，主线程立即返回。
 * 当 {@code wen-saas-boot.audit.enabled=false} 时，提供 {@code NoopAuditLogRecorder} 兜底，
 * 所有方法静默 no-op。
 */
public interface AuditLogRecorder {

    /** 通用入口；调用方负责完整构造 {@link AuditEvent}（已脱敏）。 */
    void record(AuditEvent event);

    /** 便捷方法：自动从 UserContext / RequestContext / 当前时刻补全公共字段。 */
    void recordCreate(String module, String objectType, String objectId, String objectDesc, Object after);

    void recordUpdate(String module, String objectType, String objectId, String objectDesc,
                      Object before, Object after);

    void recordDelete(String module, String objectType, String objectId, String objectDesc, Object before);

    /** 登录链路专用：{@link LoginAuditEvent} 走独立 topic。 */
    void recordLogin(LoginAuditEvent event);
}
