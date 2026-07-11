package com.wensaas.core.impl.openapi.calllog;

import com.wensaas.core.impl.audit.pipeline.AuditBatchBuffer;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import com.wensaas.core.impl.openapi.OpenApiProperties;

/**
 * 开放 API 调用日志批量缓冲（复用 {@link AuditBatchBuffer} 抽象基类）。
 *
 * <p>flush 把 {@link ApiCallEvent} → {@link SysApiCallLog} 批量 INSERT 到分区表。
 */
public class ApiCallBatchBuffer extends AuditBatchBuffer<ApiCallEvent> {

    private final SysApiCallLogMapper mapper;

    public ApiCallBatchBuffer(SysApiCallLogMapper mapper, OpenApiProperties props) {
        super("openapi-call", props.getCallLog().getBatchSize(),
                props.getCallLog().getBatchFlushInterval());
        this.mapper = mapper;
    }

    @Override
    protected void flush(List<ApiCallEvent> batch) {
        List<SysApiCallLog> rows = new ArrayList<>(batch.size());
        for (ApiCallEvent e : batch) {
            rows.add(toEntity(e));
        }
        mapper.insertBatch(rows);
    }

    private static SysApiCallLog toEntity(ApiCallEvent e) {
        SysApiCallLog r = new SysApiCallLog();
        r.setId(e.id());
        r.setTenantId(e.tenantId() == null ? 0L : e.tenantId());
        r.setApiKeyId(e.apiKeyId());
        r.setApiKeyName(e.apiKeyName());
        r.setRequestId(e.requestId());
        r.setMethod(e.method());
        r.setPath(e.path());
        r.setStatusCode(e.statusCode());
        r.setDurationMs(e.durationMs());
        r.setRequestSize(e.requestSize());
        r.setResponseSize(e.responseSize());
        r.setErrorMessage(e.errorMessage());
        r.setIp(e.ip());
        r.setUserAgent(e.userAgent());
        r.setModuleCode(e.moduleCode());
        r.setCreatedAt(LocalDateTime.ofInstant(e.createdAt(), ZoneId.systemDefault()));
        return r;
    }
}
