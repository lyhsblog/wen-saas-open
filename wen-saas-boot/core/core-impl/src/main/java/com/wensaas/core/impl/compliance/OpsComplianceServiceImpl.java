package com.wensaas.core.impl.compliance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.compliance.ComplianceException;
import com.wensaas.core.api.compliance.export.ExportRequestPage;
import com.wensaas.core.api.compliance.export.ExportRequestView;
import com.wensaas.core.api.ops.access.OpsAccessContext;
import com.wensaas.core.api.ops.access.OpsAccessContextResolver;
import com.wensaas.core.api.ops.access.OpsDataScope;
import com.wensaas.core.api.compliance.OpsComplianceService;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.wensaas.core.impl.compliance.export.ExportRequest;
import com.wensaas.core.impl.compliance.export.ExportRequestMapper;

/**
 * 运营侧数据隐私与合规审计服务实现（运营跨租户只读）。
 *
 * <p>读跨租户：用 {@link InterceptorIgnoreHelper} 关闭多租户 SQL 改写（同 {@code OpsTicketServiceImpl}）；
 * 列表与详情按运营 dataScope（OPS_OWNED 仅看 {@code csm_user_id=self} 的租户）收敛。
 */
public class OpsComplianceServiceImpl implements OpsComplianceService {

    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();

    private final ExportRequestMapper requestMapper;
    private final ComplianceSupport support;
    private final TenantQueryService tenantQuery;
    private final OpsAccessContextResolver accessResolver;

    public OpsComplianceServiceImpl(ExportRequestMapper requestMapper,
                                    ComplianceSupport support,
                                    TenantQueryService tenantQuery,
                                    OpsAccessContextResolver accessResolver) {
        this.requestMapper = requestMapper;
        this.support = support;
        this.tenantQuery = tenantQuery;
        this.accessResolver = accessResolver;
    }

    @Override
    public ExportRequestPage list(OpsExportQuery q) {
        OpsAccessContext ctx = accessResolver.require();
        int p = q.page() <= 0 ? 1 : q.page();
        int s = q.size() <= 0 ? 20 : Math.min(q.size(), 200);
        return inIgnore(() -> {
            List<Long> ownedIds = ownedTenantIdsOrNull(ctx);
            if (ownedIds != null && ownedIds.isEmpty()) {
                return ExportRequestPage.empty(p, s);
            }
            LambdaQueryWrapper<ExportRequest> w = new LambdaQueryWrapper<ExportRequest>()
                    .in(ownedIds != null, ExportRequest::getTenantId, ownedIds == null ? List.of() : ownedIds)
                    .eq(q.tenantId() != null, ExportRequest::getTenantId, q.tenantId())
                    .eq(q.status() != null, ExportRequest::getStatus, q.status() == null ? null : q.status().name())
                    .orderByDesc(ExportRequest::getAppliedAt);
            if (q.keyword() != null && !q.keyword().isBlank()) {
                String kw = q.keyword().trim();
                w.and(x -> x.like(ExportRequest::getRequestNo, kw).or().like(ExportRequest::getObjectType, kw));
            }
            Page<ExportRequest> result = requestMapper.selectPage(new Page<>(p, s), w);
            Map<Long, String> names = tenantNames(result.getRecords());
            List<ExportRequestView> items = result.getRecords().stream()
                    .map(r -> support.toView(r, names.get(r.getTenantId()))).toList();
            return new ExportRequestPage(items, result.getTotal(), p, s);
        });
    }

    @Override
    public ExportRequestView detail(long id) {
        OpsAccessContext ctx = accessResolver.require();
        return inIgnore(() -> {
            ExportRequest r = requestMapper.selectById(id);
            if (r == null) {
                throw new ComplianceException(ComplianceException.Code.REQUEST_NOT_FOUND, "导出申请不存在：" + id);
            }
            requireScope(ctx, r);
            TenantInfo tenant = tenantQuery.tenantById(r.getTenantId());
            return support.toView(r, tenant == null ? null : tenant.name());
        });
    }

    // ===================== 内部 =====================

    private void requireScope(OpsAccessContext ctx, ExportRequest r) {
        if (ctx.dataScope() != OpsDataScope.OPS_OWNED) {
            return;
        }
        TenantInfo tenant = tenantQuery.tenantById(r.getTenantId());
        if (tenant == null || tenant.csmUserId() == null || tenant.csmUserId() != ctx.userId()) {
            throw new ComplianceException(ComplianceException.Code.FORBIDDEN, "无权查看该租户导出申请");
        }
    }

    private List<Long> ownedTenantIdsOrNull(OpsAccessContext ctx) {
        if (ctx.dataScope() != OpsDataScope.OPS_OWNED) {
            return null;
        }
        return tenantQuery.tenantIdsByCsm(ctx.userId());
    }

    private Map<Long, String> tenantNames(List<ExportRequest> rows) {
        List<Long> ids = rows.stream().map(ExportRequest::getTenantId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (TenantInfo t : tenantQuery.tenantsByIds(ids)) {
            map.put(t.id(), t.name());
        }
        return map;
    }

    private <T> T inIgnore(Supplier<T> action) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            return action.get();
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }
}
