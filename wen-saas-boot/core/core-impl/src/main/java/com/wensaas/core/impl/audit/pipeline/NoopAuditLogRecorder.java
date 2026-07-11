package com.wensaas.core.impl.audit.pipeline;

import com.wensaas.core.api.audit.AuditEvent;
import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.audit.LoginAuditEvent;
import com.wensaas.core.impl.audit.AuditConfiguration;

/**
 * {@code wen-saas-boot.audit.enabled=false} 时的兜底实现，所有方法 no-op。
 * 让业务方调用点无需 null 判断。由 {@code AuditConfiguration} 在 audit 关闭时装配。
 */
public class NoopAuditLogRecorder implements AuditLogRecorder {

    @Override public void record(AuditEvent event) { /* no-op */ }

    @Override
    public void recordCreate(String module, String objectType, String objectId,
                              String objectDesc, Object after) { /* no-op */ }

    @Override
    public void recordUpdate(String module, String objectType, String objectId,
                              String objectDesc, Object before, Object after) { /* no-op */ }

    @Override
    public void recordDelete(String module, String objectType, String objectId,
                              String objectDesc, Object before) { /* no-op */ }

    @Override public void recordLogin(LoginAuditEvent event) { /* no-op */ }
}
