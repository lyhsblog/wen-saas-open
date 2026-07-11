package com.wensaas.core.impl.audit.pipeline;

import com.wensaas.core.api.audit.LoginAuditEvent;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import com.wensaas.core.impl.audit.AuditProperties;
import com.wensaas.core.impl.audit.log.SysLoginLog;
import com.wensaas.core.impl.audit.log.SysLoginLogMapper;

/**
 * 登录日志缓冲。flush 把 LoginAuditEvent → SysLoginLog 批量 INSERT。
 */
public class LoginAuditBatchBuffer extends AuditBatchBuffer<LoginAuditEvent> {

    private final SysLoginLogMapper mapper;

    public LoginAuditBatchBuffer(SysLoginLogMapper mapper, AuditProperties props) {
        super("login", props.getBatchSize(), props.getBatchFlushInterval());
        this.mapper = mapper;
    }

    @Override
    protected void flush(List<LoginAuditEvent> batch) {
        List<SysLoginLog> rows = new ArrayList<>(batch.size());
        for (LoginAuditEvent e : batch) {
            rows.add(toEntity(e));
        }
        mapper.insertBatch(rows);
    }

    private static SysLoginLog toEntity(LoginAuditEvent e) {
        SysLoginLog r = new SysLoginLog();
        r.setId(e.id());
        r.setTenantId(e.tenantId() == null ? 0L : e.tenantId());
        r.setRequestId(e.requestId());
        r.setOperatorId(e.operatorId());
        r.setOperatorName(e.operatorName());
        r.setOperatorDeptId(e.operatorDeptId());
        r.setAccount(e.account());
        r.setLoginMethod(e.loginMethod());
        r.setAction(e.action());
        r.setResult(e.result());
        r.setFailReason(e.failReason());
        r.setIp(e.ip());
        r.setUserAgent(e.userAgent());
        r.setCreatedAt(LocalDateTime.ofInstant(e.createdAt(), ZoneId.systemDefault()));
        return r;
    }
}
