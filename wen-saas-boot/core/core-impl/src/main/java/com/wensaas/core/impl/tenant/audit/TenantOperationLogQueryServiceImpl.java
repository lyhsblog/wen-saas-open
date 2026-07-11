package com.wensaas.core.impl.tenant.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.ops.audit.OpsOperationLogQueryService.DataChange;
import com.wensaas.core.api.tenant.audit.TenantOperationLogQueryService;
import com.wensaas.core.impl.audit.log.AuditChangeRenderer;
import com.wensaas.core.impl.audit.log.SysDataChangeLog;
import com.wensaas.core.impl.audit.log.SysDataChangeLogMapper;
import com.wensaas.core.impl.audit.log.SysOperationLog;
import com.wensaas.core.impl.audit.log.SysOperationLogMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 租户操作审计日志查询实现。
 *
 * <p>底层表 {@code sys_operation_log}（混合表 + 月分区）。<b>不</b>手动加 {@code tenant_id} 过滤、
 * <b>不</b>忽略租户线——租户隔离全交给 MyBatis-Plus 租户线拦截器自动注入 {@code tenant_id = 当前租户}
 * （与运营侧 {@code OpsOperationLogQueryServiceImpl} 显式 {@code tenant_id=0} + 忽略租户线相反）。
 *
 * <p>{@code restrictOperatorId} 非空时再加 {@code operator_id=:self}（我的操作记录视角）。
 */
public class TenantOperationLogQueryServiceImpl implements TenantOperationLogQueryService {

    private final SysOperationLogMapper opLogMapper;
    private final SysDataChangeLogMapper dataChangeLogMapper;
    private final AuditChangeRenderer changeRenderer;

    public TenantOperationLogQueryServiceImpl(SysOperationLogMapper opLogMapper,
                                              SysDataChangeLogMapper dataChangeLogMapper,
                                              AuditChangeRenderer changeRenderer) {
        this.opLogMapper = opLogMapper;
        this.dataChangeLogMapper = dataChangeLogMapper;
        this.changeRenderer = changeRenderer;
    }

    @Override
    public OpLogPage list(OpLogQuery q, Long restrictOperatorId) {
        int page = q.page() <= 0 ? 1 : q.page();
        int size = q.size() <= 0 ? 20 : Math.min(q.size(), 200);

        LambdaQueryWrapper<SysOperationLog> w = new LambdaQueryWrapper<SysOperationLog>()
                .orderByDesc(SysOperationLog::getCreatedAt);
        if (restrictOperatorId != null) w.eq(SysOperationLog::getOperatorId, restrictOperatorId);
        if (q.startAt() != null) w.ge(SysOperationLog::getCreatedAt, toLdt(q.startAt()));
        if (q.endAt() != null) w.lt(SysOperationLog::getCreatedAt, toLdt(q.endAt()));
        if (q.operatorId() != null) w.eq(SysOperationLog::getOperatorId, q.operatorId());
        if (q.action() != null && !q.action().isBlank()) w.eq(SysOperationLog::getAction, q.action());
        if (q.objectType() != null && !q.objectType().isBlank()) {
            w.eq(SysOperationLog::getObjectType, q.objectType());
        }
        if (q.requestId() != null && !q.requestId().isBlank()) {
            w.eq(SysOperationLog::getRequestId, q.requestId());
        }
        // 对象精确筛：object_refs 容器查询命中多主对象行（次对象也算）+ 兼容旧行的 object_id 列；
        // 与「对象类型」(object_type) 是各管各列的正交筛选，两者都给则 AND 同时生效。
        if (q.objectId() != null && !q.objectId().isBlank()) {
            String ref = "[{\"id\":" + jsonStr(q.objectId()) + "}]";
            w.and(ww -> ww.eq(SysOperationLog::getObjectId, q.objectId())
                    .or().apply("object_refs @> {0}::jsonb", ref));
        }

        Page<SysOperationLog> p = opLogMapper.selectPage(new Page<>(page, size), w);
        List<OpLogListItem> records = p.getRecords().stream()
                .map(r -> new OpLogListItem(
                        r.getId(), r.getRequestId(),
                        r.getOperatorId() == null ? 0L : r.getOperatorId(),
                        r.getOperatorName(), r.getModule(), r.getAction(),
                        r.getObjectType(), r.getObjectId(), r.getObjectDesc(),
                        r.getMethod(), r.getPath(), r.getStatusCode(), r.getDurationMs(),
                        r.getIp(), r.getCreatedAt()))
                .toList();
        return new OpLogPage(records, p.getTotal());
    }

    @Override
    public OpLogDetail getById(long id, Instant createdAt, Long restrictOperatorId) {
        // 分区表查询必须带 created_at 才能命中单分区
        LambdaQueryWrapper<SysOperationLog> w = new LambdaQueryWrapper<SysOperationLog>()
                .eq(SysOperationLog::getId, id);
        if (createdAt != null) {
            LocalDateTime t = toLdt(createdAt);
            w.between(SysOperationLog::getCreatedAt, t.minusSeconds(1), t.plusSeconds(1));
        }
        if (restrictOperatorId != null) w.eq(SysOperationLog::getOperatorId, restrictOperatorId);
        SysOperationLog r = opLogMapper.selectOne(w);
        if (r == null) return null;
        return new OpLogDetail(
                r.getId(), r.getRequestId(),
                r.getOperatorId() == null ? 0L : r.getOperatorId(),
                r.getOperatorName(), r.getModule(), r.getAction(),
                r.getObjectType(), r.getObjectId(), r.getObjectDesc(),
                r.getMethod(), r.getPath(), r.getStatusCode(), r.getDurationMs(),
                r.getErrorMessage(), r.getIp(), r.getUserAgent(), r.getCreatedAt(),
                loadDataChanges(r.getRequestId(), r.getCreatedAt()));
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
