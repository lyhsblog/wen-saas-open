package com.wensaas.core.impl.ops.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.ops.access.OpsAccessContext;
import com.wensaas.core.api.ops.access.OpsAccessContextResolver;
import com.wensaas.core.api.ops.access.OpsDataScope;
import com.wensaas.core.api.ops.audit.OpsOperationLogQueryService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.audit.log.AuditChangeRenderer;
import com.wensaas.core.impl.audit.log.SysDataChangeLog;
import com.wensaas.core.impl.audit.log.SysDataChangeLogMapper;
import com.wensaas.core.impl.audit.log.SysOperationLog;
import com.wensaas.core.impl.audit.log.SysOperationLogMapper;
import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.impl.namesource.EntityNameResolver;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 运营操作日志查询实现。
 *
 * <p>底层表 {@code sys_operation_log} 按 (id, created_at) 复合主键 + 月分区；
 * 查询 {@code WHERE tenant_id=0}（运营产生）按 created_at 倒序分页；
 * {@code OPS_OWNED} 增 {@code AND operator_id=:self}。
 *
 * <p>{@code targetTenantName} 一次性按 batch 反查 sys_tenant 名称（不做 JOIN，避免跨分区表）。
 */
public class OpsOperationLogQueryServiceImpl implements OpsOperationLogQueryService {

    private static final long OPS_TENANT = TenantContext.PLATFORM_TENANT_ID;
    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();

    private final SysOperationLogMapper opLogMapper;
    private final SysDataChangeLogMapper dataChangeLogMapper;
    private final AuditChangeRenderer changeRenderer;
    private final EntityNameResolver entityNameResolver;
    private final OpsAccessContextResolver accessResolver;

    public OpsOperationLogQueryServiceImpl(SysOperationLogMapper opLogMapper,
                                            SysDataChangeLogMapper dataChangeLogMapper,
                                            AuditChangeRenderer changeRenderer,
                                            EntityNameResolver entityNameResolver,
                                            OpsAccessContextResolver accessResolver) {
        this.opLogMapper = opLogMapper;
        this.dataChangeLogMapper = dataChangeLogMapper;
        this.changeRenderer = changeRenderer;
        this.entityNameResolver = entityNameResolver;
        this.accessResolver = accessResolver;
    }

    @Override
    public OpLogPage list(OpLogQuery q) {
        OpsAccessContext ctx = accessResolver.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            int page = q.page() <= 0 ? 1 : q.page();
            int size = q.size() <= 0 ? 20 : Math.min(q.size(), 200);

            LambdaQueryWrapper<SysOperationLog> w = new LambdaQueryWrapper<SysOperationLog>()
                    .eq(SysOperationLog::getTenantId, OPS_TENANT)
                    .orderByDesc(SysOperationLog::getCreatedAt);
            if (q.startAt() != null) w.ge(SysOperationLog::getCreatedAt, toLdt(q.startAt()));
            if (q.endAt() != null)   w.lt(SysOperationLog::getCreatedAt, toLdt(q.endAt()));
            if (q.operatorId() != null) w.eq(SysOperationLog::getOperatorId, q.operatorId());
            if (q.action() != null && !q.action().isBlank()) w.eq(SysOperationLog::getAction, q.action());
            if (q.targetTenantId() != null) w.eq(SysOperationLog::getTargetTenantId, q.targetTenantId());
            if (q.requestId() != null && !q.requestId().isBlank()) w.eq(SysOperationLog::getRequestId, q.requestId());
            if (q.module() != null && !q.module().isBlank()) w.eq(SysOperationLog::getModule, q.module());
            if (q.objectId() != null && !q.objectId().isBlank()) {
                // 按对象 id 追溯：object_refs 容器查询命中多主对象行（次对象也算）+ 兼容旧行的 object_id 列
                String ref = "[{\"id\":" + jsonStr(q.objectId()) + "}]";
                w.and(ww -> ww.eq(SysOperationLog::getObjectId, q.objectId())
                        .or().apply("object_refs @> {0}::jsonb", ref));
            } else if (q.objectType() != null && !q.objectType().isBlank()) {
                w.eq(SysOperationLog::getObjectType, q.objectType());
            }
            if (ctx.dataScope() == OpsDataScope.OPS_OWNED) {
                w.eq(SysOperationLog::getOperatorId, ctx.userId());
            }
            Page<SysOperationLog> p = opLogMapper.selectPage(new Page<>(page, size), w);

            List<Long> tenantIds = p.getRecords().stream()
                    .map(SysOperationLog::getTargetTenantId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Long, String> tenantNames = entityNameResolver.resolve(EntityTypes.SYS_TENANT, tenantIds);
            List<OpLogListItem> records = p.getRecords().stream()
                    .map(r -> new OpLogListItem(
                            r.getId(), r.getRequestId(),
                            r.getOperatorId() == null ? 0L : r.getOperatorId(),
                            r.getOperatorName(), r.getModule(), r.getAction(),
                            r.getObjectType(), entityNameResolver.displayLabel(r.getObjectType()),
                            r.getObjectId(), r.getObjectDesc(),
                            r.getTargetTenantId(),
                            r.getTargetTenantId() == null ? null : tenantNames.get(r.getTargetTenantId()),
                            r.getMethod(), r.getPath(), r.getStatusCode(), r.getDurationMs(),
                            r.getIp(), r.getCreatedAt()))
                    .toList();
            return new OpLogPage(records, p.getTotal());
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public OpLogDetail getById(long id, Instant createdAt) {
        OpsAccessContext ctx = accessResolver.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            // 分区表查询必须带 created_at 才能命中单分区
            LambdaQueryWrapper<SysOperationLog> w = new LambdaQueryWrapper<SysOperationLog>()
                    .eq(SysOperationLog::getTenantId, OPS_TENANT)
                    .eq(SysOperationLog::getId, id);
            if (createdAt != null) {
                LocalDateTime t = toLdt(createdAt);
                w.between(SysOperationLog::getCreatedAt, t.minusSeconds(1), t.plusSeconds(1));
            }
            if (ctx.dataScope() == OpsDataScope.OPS_OWNED) {
                w.eq(SysOperationLog::getOperatorId, ctx.userId());
            }
            SysOperationLog r = opLogMapper.selectOne(w);
            if (r == null) return null;
            String targetName = entityNameResolver.resolve(EntityTypes.SYS_TENANT, r.getTargetTenantId());
            return new OpLogDetail(
                    r.getId(), r.getRequestId(),
                    r.getOperatorId() == null ? 0L : r.getOperatorId(),
                    r.getOperatorName(), r.getModule(), r.getAction(),
                    r.getObjectType(), entityNameResolver.displayLabel(r.getObjectType()),
                    r.getObjectId(), r.getObjectDesc(),
                    r.getTargetTenantId(), targetName,
                    r.getMethod(), r.getPath(), r.getStatusCode(), r.getDurationMs(),
                    r.getErrorMessage(), r.getIp(), r.getUserAgent(), r.getCreatedAt(),
                    loadDataChanges(r.getRequestId(), r.getCreatedAt()));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    /** 取同 requestId 下的 ③ 字段级变更（按 ② 行 created_at ±1 天分区裁剪），按列字典渲染成句。 */
    private List<DataChange> loadDataChanges(String requestId, LocalDateTime around) {
        if (requestId == null || requestId.isBlank()) {
            return List.of();
        }
        LocalDateTime from = around == null ? null : around.minusDays(1);
        LocalDateTime to = around == null ? null : around.plusDays(1);
        List<SysDataChangeLog> rows = dataChangeLogMapper.findByRequestId(requestId, from, to);
        return changeRenderer.render(rows);
    }

    private static LocalDateTime toLdt(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneId.systemDefault());
    }

    /** 构造 JSON 字符串字面量（含引号），剥离 " 与 \ 防止 object_refs 容器查询的 jsonb 文本损坏。 */
    private static String jsonStr(String raw) {
        return '"' + raw.replace("\\", "").replace("\"", "") + '"';
    }
}
