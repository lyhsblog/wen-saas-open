package com.wensaas.core.impl.ops.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.ops.access.OpsAccessContext;
import com.wensaas.core.api.ops.access.OpsAccessContextResolver;
import com.wensaas.core.api.ops.access.OpsDataScope;
import com.wensaas.core.api.ops.dashboard.OpsDashboardService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.audit.log.SysOperationLog;
import com.wensaas.core.impl.audit.log.SysOperationLogMapper;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作台聚合实现。
 *
 * <p>所有指标按 {@code OpsAccessContext.dataScope} 过滤：
 * {@code OPS_OWNED} → 仅看 {@code sys_tenant.csm_user_id=self} 的租户。
 */
public class OpsDashboardServiceImpl implements OpsDashboardService {

    private static final long OPS_TENANT = TenantContext.PLATFORM_TENANT_ID;
    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();
    private static final ZoneId TZ = ZoneId.of("Asia/Shanghai");
    private static final int RECENT_LIMIT = 20;
    private static final int TODO_LIMIT = 20;

    private final SysTenantMapper tenantMapper;
    private final SysOperationLogMapper opLogMapper;
    private final OpsAccessContextResolver accessResolver;

    public OpsDashboardServiceImpl(SysTenantMapper tenantMapper,
                                    SysOperationLogMapper opLogMapper,
                                    OpsAccessContextResolver accessResolver) {
        this.tenantMapper = tenantMapper;
        this.opLogMapper = opLogMapper;
        this.accessResolver = accessResolver;
    }

    @Override
    public OverviewMetrics overview() {
        OpsAccessContext ctx = accessResolver.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();
            LocalDateTime endOfLastMonth = startOfMonth.minusNanos(1);

            long activeNow = countTenants(ctx, w -> w.eq(SysTenant::getStatus, 1));
            long activeLastMonthEnd = countTenants(ctx, w -> w.eq(SysTenant::getStatus, 1)
                    .lt(SysTenant::getCreatedAt, startOfMonth));
            long delta = activeNow - activeLastMonthEnd;
            long newThisMonth = countTenants(ctx, w -> w.ge(SysTenant::getCreatedAt, startOfMonth));

            Instant in30 = now.plusDays(30).atZone(TZ).toInstant();
            Instant in7 = now.plusDays(7).atZone(TZ).toInstant();
            Instant nowI = now.atZone(TZ).toInstant();
            long expiring30 = countTenants(ctx, w -> w.ge(SysTenant::getExpireAt, nowI)
                    .lt(SysTenant::getExpireAt, in30));
            long expiring7 = countTenants(ctx, w -> w.ge(SysTenant::getExpireAt, nowI)
                    .lt(SysTenant::getExpireAt, in7));

            long renewals = countRenewals(ctx, startOfMonth);

            return new OverviewMetrics(activeNow, delta, newThisMonth, renewals, expiring30, expiring7);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public List<TodoItem> todos() {
        OpsAccessContext ctx = accessResolver.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            LocalDateTime now = LocalDateTime.now();
            Instant in30 = now.plusDays(30).atZone(TZ).toInstant();
            Instant nowI = now.atZone(TZ).toInstant();
            LambdaQueryWrapper<SysTenant> w = new LambdaQueryWrapper<SysTenant>()
                    .ge(SysTenant::getExpireAt, nowI)
                    .lt(SysTenant::getExpireAt, in30)
                    .orderByAsc(SysTenant::getExpireAt);
            if (ctx.dataScope() == OpsDataScope.OPS_OWNED) {
                w.eq(SysTenant::getCsmUserId, ctx.userId());
            }
            Page<SysTenant> page = tenantMapper.selectPage(new Page<>(1, TODO_LIMIT), w);
            List<TodoItem> out = new ArrayList<>(page.getRecords().size());
            for (SysTenant t : page.getRecords()) {
                LocalDateTime due = LocalDateTime.ofInstant(t.getExpireAt(), TZ);
                long daysLeft = java.time.Duration.between(now, due).toDays();
                String severity = daysLeft <= 7 ? "urgent" : "warn";
                out.add(new TodoItem(
                        "tenant_expiring",
                        "租户 " + t.getName() + " 即将到期",
                        daysLeft + " 天后到期 · 跟进续费",
                        severity,
                        t.getId(), t.getName(),
                        "/tenants/" + t.getId(),
                        due));
            }
            return out;
            // 密码重置 token / 工单待办留下一个会话补
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public List<RecentActivity> recentActivities(int limit) {
        OpsAccessContext ctx = accessResolver.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            int size = limit <= 0 ? RECENT_LIMIT : Math.min(limit, 100);
            LambdaQueryWrapper<SysOperationLog> w = new LambdaQueryWrapper<SysOperationLog>()
                    .eq(SysOperationLog::getTenantId, OPS_TENANT)
                    .orderByDesc(SysOperationLog::getCreatedAt);
            if (ctx.dataScope() == OpsDataScope.OPS_OWNED) {
                w.eq(SysOperationLog::getOperatorId, ctx.userId());
            }
            Page<SysOperationLog> page = opLogMapper.selectPage(new Page<>(1, size), w);
            Set<Long> ids = page.getRecords().stream()
                    .map(SysOperationLog::getTargetTenantId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toCollection(HashSet::new));
            Map<Long, String> names = new HashMap<>();
            if (!ids.isEmpty()) {
                for (SysTenant t : tenantMapper.selectBatchIds(ids)) {
                    names.put(t.getId(), t.getName());
                }
            }
            List<RecentActivity> out = new ArrayList<>(page.getRecords().size());
            for (SysOperationLog r : page.getRecords()) {
                out.add(new RecentActivity(
                        r.getId(),
                        r.getOperatorId() == null ? 0L : r.getOperatorId(),
                        r.getOperatorName(),
                        r.getAction(),
                        r.getObjectDesc(),
                        r.getTargetTenantId(),
                        r.getTargetTenantId() == null ? null : names.get(r.getTargetTenantId()),
                        r.getCreatedAt()));
            }
            return out;
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    private long countTenants(OpsAccessContext ctx,
                               java.util.function.Consumer<LambdaQueryWrapper<SysTenant>> extra) {
        LambdaQueryWrapper<SysTenant> w = new LambdaQueryWrapper<>();
        if (ctx.dataScope() == OpsDataScope.OPS_OWNED) {
            w.eq(SysTenant::getCsmUserId, ctx.userId());
        }
        extra.accept(w);
        return tenantMapper.selectCount(w);
    }

    private long countRenewals(OpsAccessContext ctx, LocalDateTime since) {
        LambdaQueryWrapper<SysOperationLog> w = new LambdaQueryWrapper<SysOperationLog>()
                .eq(SysOperationLog::getTenantId, OPS_TENANT)
                .eq(SysOperationLog::getAction, "renew")
                .isNotNull(SysOperationLog::getTargetTenantId)
                .ge(SysOperationLog::getCreatedAt, since);
        if (ctx.dataScope() == OpsDataScope.OPS_OWNED) {
            // 续费的 target 应归属当前 CSM；间接条件：查 sys_tenant.csm_user_id=self 的 ids 再 in
            List<Long> myTenantIds = tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                    .eq(SysTenant::getCsmUserId, ctx.userId())).stream()
                    .map(SysTenant::getId).toList();
            if (myTenantIds.isEmpty()) return 0L;
            w.in(SysOperationLog::getTargetTenantId, myTenantIds);
        }
        return opLogMapper.selectCount(w);
    }
}
