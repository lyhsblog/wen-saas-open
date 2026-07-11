package com.wensaas.core.impl.audit.pipeline;

import tools.jackson.databind.ObjectMapper;
import com.wensaas.core.api.audit.AuditEvent;
import com.wensaas.core.api.audit.AuditObjectRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import com.wensaas.core.impl.audit.AuditProperties;
import com.wensaas.core.impl.audit.log.SysOperationLog;
import com.wensaas.core.impl.audit.log.SysOperationLogMapper;

/**
 * 操作日志缓冲。flush 把 AuditEvent → SysOperationLog 批量 INSERT。
 */
public class OperationAuditBatchBuffer extends AuditBatchBuffer<AuditEvent> {

    private static final Logger log = LoggerFactory.getLogger(OperationAuditBatchBuffer.class);

    private final SysOperationLogMapper mapper;
    /** 与 HTTP 响应序列化同源的 Jackson 3 mapper（雪花 Long→string 等全局配置），object_refs 写 jsonb 复用之。 */
    private final ObjectMapper jackson;

    public OperationAuditBatchBuffer(SysOperationLogMapper mapper, AuditProperties props, ObjectMapper jackson) {
        super("operation", props.getBatchSize(), props.getBatchFlushInterval());
        this.mapper = mapper;
        this.jackson = jackson;
    }

    @Override
    protected void flush(List<AuditEvent> batch) {
        List<SysOperationLog> rows = new ArrayList<>(batch.size());
        for (AuditEvent e : batch) {
            rows.add(toEntity(e));
        }
        mapper.insertBatch(rows);
    }

    private SysOperationLog toEntity(AuditEvent e) {
        SysOperationLog r = new SysOperationLog();
        r.setId(e.id());
        r.setTenantId(e.tenantId() == null ? 0L : e.tenantId());
        r.setTargetTenantId(e.targetTenantId());
        r.setRequestId(e.requestId());
        r.setOperatorId(e.operatorId() == null ? 0L : e.operatorId());
        r.setOperatorName(e.operatorName());
        r.setOperatorDeptId(e.operatorDeptId());
        r.setModule(e.module());
        r.setAction(e.action());
        r.setObjectType(e.objectType());
        r.setObjectId(e.objectId());
        List<AuditObjectRef> refs = resolveRefList(e);
        r.setObjectDesc(resolveObjectDesc(e, refs));
        r.setObjectRefs(serializeRefs(refs));
        r.setMethod(e.method());
        r.setPath(e.path());
        r.setStatusCode(e.statusCode());
        r.setDurationMs(e.durationMs());
        r.setErrorMessage(e.errorMessage());
        r.setIp(e.ip());
        r.setUserAgent(e.userAgent());
        r.setCreatedAt(LocalDateTime.ofInstant(e.createdAt(), ZoneId.systemDefault()));
        return r;
    }

    /** 主对象引用列表：发射缓冲多对象优先；否则从主对象（objectType/id/desc）派生 1 元素；objectId 为空则空。 */
    private static List<AuditObjectRef> resolveRefList(AuditEvent e) {
        List<AuditObjectRef> refs = e.objectRefs();
        if (refs != null && !refs.isEmpty()) {
            return refs;
        }
        if (e.objectId() == null) {
            return List.of();
        }
        return List.of(new AuditObjectRef(e.objectType(), e.objectId(), e.objectDesc()));
    }

    /**
     * object_desc：注解 {@code objectDescExpr} 显式值优先；否则从 {@code object_refs} 的友好名派生，
     * 让列表/详情接口（只回 objectDesc）也能展示 service 层 {@code AuditObjects.add} 登记的名称。
     * 单名直出；多名去重后 "、" 拼接，超过 3 个截断为 "a、b、c等N个"。
     */
    private static String resolveObjectDesc(AuditEvent e, List<AuditObjectRef> refs) {
        if (e.objectDesc() != null && !e.objectDesc().isBlank()) {
            return e.objectDesc();
        }
        List<String> names = new ArrayList<>();
        for (AuditObjectRef ref : refs) {
            String n = ref.name();
            if (n != null && !n.isBlank() && !names.contains(n)) {
                names.add(n);
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        if (names.size() <= 3) {
            return String.join("、", names);
        }
        return String.join("、", names.subList(0, 3)) + "等" + names.size() + "个";
    }

    /** 序列化 refs 为 jsonb 文本；空列表返回 null。 */
    private String serializeRefs(List<AuditObjectRef> refs) {
        if (refs.isEmpty()) {
            return null;
        }
        try {
            return jackson.writeValueAsString(refs);
        } catch (Exception ex) {
            log.warn("audit.object_refs.serialize.fail reason={}", ex.getMessage());
            return null;
        }
    }
}
