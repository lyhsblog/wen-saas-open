package com.wensaas.core.impl.ops.health;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.ops.access.OpsAccessContext;
import com.wensaas.core.api.ops.access.OpsAccessContextResolver;
import com.wensaas.core.api.ops.access.OpsDataScope;
import com.wensaas.core.api.ops.health.HealthLevel;
import com.wensaas.core.api.ops.health.OpsHealthException;
import com.wensaas.core.api.ops.health.OpsHealthService;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.pkg.SysPackage;
import com.wensaas.core.impl.pkg.SysPackageMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户健康度读 + 重算实现。
 *
 * <p>列表/总览读 {@code sys_tenant_health} 快照；详情实时重算。所有读路径按
 * {@code OpsAccessContext.dataScope} 过滤（{@code OPS_OWNED} 仅 {@code csm_user_id=self}）。
 */
public class OpsHealthServiceImpl implements OpsHealthService {

    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();
    private static final int RECOMPUTE_BATCH = 500;

    private final SysTenantHealthMapper healthMapper;
    private final SysTenantMapper tenantMapper;
    private final SysPackageMapper packageMapper;
    private final HealthScoreCalculator calculator;
    private final OpsAccessContextResolver accessResolver;

    public OpsHealthServiceImpl(SysTenantHealthMapper healthMapper,
                                SysTenantMapper tenantMapper,
                                SysPackageMapper packageMapper,
                                HealthScoreCalculator calculator,
                                OpsAccessContextResolver accessResolver) {
        this.healthMapper = healthMapper;
        this.tenantMapper = tenantMapper;
        this.packageMapper = packageMapper;
        this.calculator = calculator;
        this.accessResolver = accessResolver;
    }

    @Override
    public OverviewMetrics overview() {
        OpsAccessContext ctx = accessResolver.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            List<Long> ownedIds = ownedTenantIdsOrNull(ctx);
            if (ownedIds != null && ownedIds.isEmpty()) {
                return new OverviewMetrics(0, 0, 0, 0, 0, 0);
            }
            long healthyNow = countByLevel(ownedIds, HealthLevel.HEALTHY, false);
            long warningNow = countByLevel(ownedIds, HealthLevel.WARNING, false);
            long dangerNow = countByLevel(ownedIds, HealthLevel.DANGER, false);
            long healthyPrev = countByLevel(ownedIds, HealthLevel.HEALTHY, true);
            long warningPrev = countByLevel(ownedIds, HealthLevel.WARNING, true);
            long dangerPrev = countByLevel(ownedIds, HealthLevel.DANGER, true);
            return new OverviewMetrics(
                    healthyNow, healthyNow - healthyPrev,
                    warningNow, warningNow - warningPrev,
                    dangerNow, dangerNow - dangerPrev);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public HealthPage list(HealthLevel level, String keyword, int page, int pageSize) {
        OpsAccessContext ctx = accessResolver.require();
        int p = page <= 0 ? 1 : page;
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            // 候选租户 id 过滤：OWNED 范围 + 关键词搜租户名/编号时先在 sys_tenant 收敛
            List<Long> candidateIds = resolveCandidateIds(ctx, keyword);
            if (candidateIds != null && candidateIds.isEmpty()) {
                return new HealthPage(List.of(), 0L, p, size);
            }
            LambdaQueryWrapper<SysTenantHealth> w = new LambdaQueryWrapper<SysTenantHealth>()
                    .eq(level != null, SysTenantHealth::getLevel, level == null ? null : level.dbValue())
                    .in(candidateIds != null, SysTenantHealth::getTenantId, candidateIds)
                    .orderByAsc(SysTenantHealth::getScore);
            Page<SysTenantHealth> result = healthMapper.selectPage(new Page<>(p, size), w);

            Map<Long, SysTenant> tenants = loadTenants(result.getRecords().stream()
                    .map(SysTenantHealth::getTenantId).toList());
            List<HealthListItem> items = new ArrayList<>(result.getRecords().size());
            for (SysTenantHealth h : result.getRecords()) {
                SysTenant t = tenants.get(h.getTenantId());
                items.add(new HealthListItem(
                        h.getTenantId(),
                        t == null ? null : t.getName(),
                        t == null ? null : t.getTenantNo(),
                        h.getScore(),
                        HealthLevel.fromDbValue(h.getLevel()),
                        h.getRiskSignals() == null ? List.of() : h.getRiskSignals(),
                        h.getComputedAt()));
            }
            return new HealthPage(items, result.getTotal(), p, size);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public HealthDetail detail(long tenantId) {
        OpsAccessContext ctx = accessResolver.require();
        Instant now = Instant.now();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysTenant tenant = tenantMapper.selectById(tenantId);
            if (tenant == null) {
                throw new OpsHealthException(OpsHealthException.Code.TENANT_NOT_FOUND, "租户不存在：" + tenantId);
            }
            if (ctx.dataScope() == OpsDataScope.OPS_OWNED
                    && (tenant.getCsmUserId() == null || tenant.getCsmUserId() != ctx.userId())) {
                throw new OpsHealthException(OpsHealthException.Code.FORBIDDEN, "无权查看该租户健康度");
            }
            HealthScoreCalculator.Computation c = calculator.compute(tenant, systemPackageCodes(), now);
            return new HealthDetail(
                    tenant.getId(), tenant.getName(), tenant.getTenantNo(),
                    c.score(), c.level(), c.signals(), c.metrics(),
                    tenant.getExpireAt(), now);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public RecomputeResult recomputeAll() {
        Instant now = Instant.now();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            Set<String> sysCodes = systemPackageCodes();
            List<SysTenant> tenants = tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                    .last("LIMIT " + RECOMPUTE_BATCH));
            int healthy = 0, warning = 0, danger = 0;
            List<DangerBrief> dangerList = new ArrayList<>();
            LocalDateTime nowLdt = LocalDateTime.now();
            for (SysTenant t : tenants) {
                HealthScoreCalculator.Computation c = calculator.compute(t, sysCodes, now);
                switch (c.level()) {
                    case HEALTHY -> healthy++;
                    case WARNING -> warning++;
                    case DANGER -> {
                        danger++;
                        dangerList.add(new DangerBrief(t.getId(), t.getName(), c.signals()));
                    }
                }
                upsert(t.getId(), c, now, nowLdt);
            }
            return new RecomputeResult(tenants.size(), healthy, warning, danger, dangerList);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    // ===================== 私有 =====================

    private void upsert(long tenantId, HealthScoreCalculator.Computation c, Instant now, LocalDateTime nowLdt) {
        SysTenantHealth existing = healthMapper.selectOne(new LambdaQueryWrapper<SysTenantHealth>()
                .eq(SysTenantHealth::getTenantId, tenantId));
        if (existing == null) {
            SysTenantHealth row = new SysTenantHealth();
            row.setTenantId(tenantId);
            row.setScore(c.score());
            row.setLevel(c.level().dbValue());
            row.setPrevLevel(null);
            row.setRiskSignals(c.signals());
            row.setMetrics(c.metrics());
            row.setComputedAt(now);
            row.setCreatedAt(nowLdt);
            row.setUpdatedAt(nowLdt);
            healthMapper.insert(row);
        } else {
            existing.setPrevLevel(existing.getLevel());
            existing.setScore(c.score());
            existing.setLevel(c.level().dbValue());
            existing.setRiskSignals(c.signals());
            existing.setMetrics(c.metrics());
            existing.setComputedAt(now);
            existing.setUpdatedAt(nowLdt);
            healthMapper.updateById(existing);
        }
    }

    private long countByLevel(List<Long> ownedIds, HealthLevel level, boolean usePrev) {
        LambdaQueryWrapper<SysTenantHealth> w = new LambdaQueryWrapper<SysTenantHealth>()
                .in(ownedIds != null, SysTenantHealth::getTenantId, ownedIds);
        if (usePrev) {
            w.eq(SysTenantHealth::getPrevLevel, level.dbValue());
        } else {
            w.eq(SysTenantHealth::getLevel, level.dbValue());
        }
        return healthMapper.selectCount(w);
    }

    /** OWNED → 自己归属的 tenant id 列表（可能空）；ALL → null（不限）。 */
    private List<Long> ownedTenantIdsOrNull(OpsAccessContext ctx) {
        if (ctx.dataScope() != OpsDataScope.OPS_OWNED) {
            return null;
        }
        return tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                        .select(SysTenant::getId)
                        .eq(SysTenant::getCsmUserId, ctx.userId())).stream()
                .map(SysTenant::getId).toList();
    }

    /** 列表候选 id：OWNED 收敛 + 关键词搜租户名/编号；都不限时返回 null。 */
    private List<Long> resolveCandidateIds(OpsAccessContext ctx, String keyword) {
        boolean owned = ctx.dataScope() == OpsDataScope.OPS_OWNED;
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        if (!owned && !hasKeyword) {
            return null;
        }
        LambdaQueryWrapper<SysTenant> w = new LambdaQueryWrapper<SysTenant>().select(SysTenant::getId);
        if (owned) {
            w.eq(SysTenant::getCsmUserId, ctx.userId());
        }
        if (hasKeyword) {
            String kw = keyword.trim();
            w.and(q -> q.like(SysTenant::getName, kw).or().like(SysTenant::getTenantNo, kw));
        }
        return tenantMapper.selectList(w).stream().map(SysTenant::getId).toList();
    }

    private Map<Long, SysTenant> loadTenants(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysTenant> map = new HashMap<>();
        for (SysTenant t : tenantMapper.selectBatchIds(ids)) {
            map.put(t.getId(), t);
        }
        return map;
    }

    private Set<String> systemPackageCodes() {
        return packageMapper.selectList(new LambdaQueryWrapper<SysPackage>()
                        .eq(SysPackage::getIsSystem, true)
                        .select(SysPackage::getCode)).stream()
                .map(SysPackage::getCode).collect(Collectors.toSet());
    }
}
