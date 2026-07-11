package com.wensaas.core.impl.ops.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.ops.access.OpsAccessContext;
import com.wensaas.core.api.ops.access.OpsAccessContextResolver;
import com.wensaas.core.api.ops.access.OpsDataScope;
import com.wensaas.core.api.ops.monitor.OpsPlatformMonitorService;
import com.wensaas.core.impl.audit.log.SysLoginLog;
import com.wensaas.core.impl.audit.log.SysLoginLogMapper;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.file.SysFile;
import com.wensaas.core.impl.file.SysFileMapper;
import com.wensaas.core.impl.openapi.calllog.SysApiCallLog;
import com.wensaas.core.impl.openapi.calllog.SysApiCallLogMapper;
import com.wensaas.core.impl.pkg.SysPackage;
import com.wensaas.core.impl.pkg.SysPackageMapper;
import com.wensaas.core.api.tenant.TenantPlan;
import com.wensaas.core.impl.spi.module.SysBusinessModule;
import com.wensaas.core.impl.spi.module.SysBusinessModuleMapper;
import com.wensaas.core.impl.ticket.TicketTicket;
import com.wensaas.core.impl.ticket.TicketTicketMapper;
import com.wensaas.core.impl.webhook.SysWebhook;
import com.wensaas.core.impl.webhook.SysWebhookLog;
import com.wensaas.core.impl.webhook.SysWebhookLogMapper;
import com.wensaas.core.impl.webhook.SysWebhookMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * 平台运维监控看板实现（O9）。实时聚合，按 {@code dataScope} 收敛。
 *
 * <p>多数业务/日志表（login/api/webhook/ticket/file）非 platform-only，查询需关多租户线
 * （{@link InterceptorIgnoreHelper}）后显式按 {@code ownedIds} 过滤；时序按天循环计数（当前
 * 百级租户量足够，量大可改 date_trunc GROUP BY 预聚合）。
 */
public class OpsPlatformMonitorServiceImpl implements OpsPlatformMonitorService {

    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");
    private static final int TENANT_ACTIVE = 1;

    private final SysTenantMapper tenantMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SysApiCallLogMapper apiCallLogMapper;
    private final SysWebhookLogMapper webhookLogMapper;
    private final SysWebhookMapper webhookMapper;
    private final TicketTicketMapper ticketMapper;
    private final SysFileMapper fileMapper;
    private final SysPackageMapper packageMapper;
    private final SysBusinessModuleMapper businessModuleMapper;
    private final SysModuleAccessLogMapper moduleAccessMapper;
    private final SysTenantRenewalLogMapper renewalLogMapper;
    private final com.wensaas.core.impl.ops.SysTenantSeatAddonMapper seatAddonMapper;
    private final OpsAccessContextResolver accessResolver;

    public OpsPlatformMonitorServiceImpl(SysTenantMapper tenantMapper,
                                         SysLoginLogMapper loginLogMapper,
                                         SysApiCallLogMapper apiCallLogMapper,
                                         SysWebhookLogMapper webhookLogMapper,
                                         SysWebhookMapper webhookMapper,
                                         TicketTicketMapper ticketMapper,
                                         SysFileMapper fileMapper,
                                         SysPackageMapper packageMapper,
                                         SysBusinessModuleMapper businessModuleMapper,
                                         SysModuleAccessLogMapper moduleAccessMapper,
                                         SysTenantRenewalLogMapper renewalLogMapper,
                                         com.wensaas.core.impl.ops.SysTenantSeatAddonMapper seatAddonMapper,
                                         OpsAccessContextResolver accessResolver) {
        this.tenantMapper = tenantMapper;
        this.loginLogMapper = loginLogMapper;
        this.apiCallLogMapper = apiCallLogMapper;
        this.webhookLogMapper = webhookLogMapper;
        this.webhookMapper = webhookMapper;
        this.ticketMapper = ticketMapper;
        this.fileMapper = fileMapper;
        this.packageMapper = packageMapper;
        this.businessModuleMapper = businessModuleMapper;
        this.moduleAccessMapper = moduleAccessMapper;
        this.renewalLogMapper = renewalLogMapper;
        this.seatAddonMapper = seatAddonMapper;
        this.accessResolver = accessResolver;
    }

    // ===================== overview =====================

    @Override
    public Overview overview() {
        OpsAccessContext ctx = accessResolver.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            List<Long> owned = ownedIdsOrNull(ctx);
            if (ownedEmpty(owned)) {
                List<Long> z = zeros(14);
                return new Overview(0, 0, 0, 0, 0, 0, 0, 0, z, z, z, z, 0, 0);
            }
            LocalDate today = LocalDate.now();
            LocalDateTime todayStart = today.atStartOfDay();
            LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();
            LocalDateTime yesterday = today.minusDays(1).atStartOfDay();

            long activeTenants = countTenants(owned, TENANT_ACTIVE);
            long newTenants30 = countNewTenants(today.minusDays(30).atStartOfDay(), tomorrow, owned);

            long dauToday = distinctLoginUsers(todayStart, tomorrow, owned);
            long dauYesterday = distinctLoginUsers(yesterday, todayStart, owned);

            long apiToday = countApi(todayStart, tomorrow, owned, false);
            long apiYesterday = countApi(yesterday, todayStart, owned, false);

            long openTickets = countOpenTickets(owned);
            long newTickets7 = countNewTickets(today.minusDays(7).atStartOfDay(), tomorrow, owned);

            List<Long> tenantSpark = dailySeries(14, (f, t) -> countNewTenants(f, t, owned));
            List<Long> dauSpark = dailySeries(14, (f, t) -> distinctLoginUsers(f, t, owned));
            List<Long> apiSpark = dailySeries(14, (f, t) -> countApi(f, t, owned, false));
            List<Long> ticketSpark = dailySeries(14, (f, t) -> countNewTickets(f, t, owned));

            long expiring30 = countExpiringWithin(owned, 30);
            long expiring7 = countExpiringWithin(owned, 7);

            return new Overview(
                    activeTenants, newTenants30,
                    dauToday, pct(dauToday - dauYesterday, dauYesterday),
                    apiToday, pct(apiToday - apiYesterday, apiYesterday),
                    openTickets, newTickets7,
                    tenantSpark, dauSpark, apiSpark, ticketSpark,
                    expiring30, expiring7);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    // ===================== trends =====================

    @Override
    public TrendSeries trends(String type, String range) {
        OpsAccessContext ctx = accessResolver.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            List<Long> owned = ownedIdsOrNull(ctx);
            int days = rangeDays(range);
            String t = type == null ? "active" : type;
            List<String> dates = new ArrayList<>(days);
            LocalDate today = LocalDate.now();
            for (int i = days - 1; i >= 0; i--) {
                dates.add(today.minusDays(i).format(MD));
            }
            if (ownedEmpty(owned)) {
                return new TrendSeries(dates, zeros(days), zeros(days), label1(t), label2(t));
            }
            BiFunction<LocalDateTime, LocalDateTime, Long> f1;
            BiFunction<LocalDateTime, LocalDateTime, Long> f2;
            switch (t) {
                case "tenant" -> {
                    f1 = (a, b) -> countNewTenants(a, b, owned);
                    f2 = (a, b) -> countLostTenants(a, b, owned);
                }
                case "api" -> {
                    f1 = (a, b) -> countApi(a, b, owned, false);
                    f2 = (a, b) -> countApi(a, b, owned, true);
                }
                case "ticket" -> {
                    f1 = (a, b) -> countNewTickets(a, b, owned);
                    f2 = (a, b) -> countClosedTickets(a, b, owned);
                }
                default -> { // active
                    f1 = (a, b) -> distinctLoginUsers(a, b, owned);
                    f2 = (a, b) -> countLogins(a, b, owned);
                }
            }
            return new TrendSeries(dates, dailySeries(days, f1), dailySeries(days, f2), label1(t), label2(t));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    private static String label1(String t) {
        return switch (t) {
            case "tenant" -> "新增租户";
            case "api" -> "API 调用";
            case "ticket" -> "新建工单";
            default -> "活跃用户(DAU)";
        };
    }

    private static String label2(String t) {
        return switch (t) {
            case "tenant" -> "流失租户";
            case "api" -> "错误调用";
            case "ticket" -> "关闭工单";
            default -> "登录次数";
        };
    }

    // ===================== moduleMetrics =====================

    @Override
    public List<ModuleMetric> moduleMetrics() {
        OpsAccessContext ctx = accessResolver.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            List<Long> owned = ownedIdsOrNull(ctx);
            if (ownedEmpty(owned)) {
                return List.of();
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime since30 = now.minusDays(30);
            LocalDateTime since60 = now.minusDays(60);

            // 模块 code → name（真实注册的业务模块）
            Map<String, String> names = new LinkedHashMap<>();
            for (SysBusinessModule m : businessModuleMapper.selectList(new LambdaQueryWrapper<>())) {
                names.put(m.getCode(), m.getName());
            }

            // 近 30 天按模块聚合：calls / dau / tenants
            List<Map<String, Object>> rows = moduleAccessMapper.selectMaps(new QueryWrapper<SysModuleAccessLog>()
                    .select("module_code",
                            "COUNT(*) AS calls",
                            "COUNT(DISTINCT operator_id) AS dau",
                            "COUNT(DISTINCT tenant_id) AS tenants")
                    .ge("created_at", since30)
                    .in(owned != null, "tenant_id", ownedOrDummy(owned))
                    .groupBy("module_code"));

            List<ModuleMetric> out = new ArrayList<>(rows.size());
            for (Map<String, Object> r : rows) {
                String code = (String) r.get("module_code");
                long calls = asLong(r.get("calls"));
                long dau = asLong(r.get("dau"));
                long tenants = asLong(r.get("tenants"));
                long prevCalls = moduleAccessMapper.selectCount(new LambdaQueryWrapper<SysModuleAccessLog>()
                        .eq(SysModuleAccessLog::getModuleCode, code)
                        .ge(SysModuleAccessLog::getCreatedAt, since60)
                        .lt(SysModuleAccessLog::getCreatedAt, since30)
                        .in(owned != null, SysModuleAccessLog::getTenantId, ownedOrDummy(owned)));
                out.add(new ModuleMetric(code, names.getOrDefault(code, code),
                        tenants, calls, dau, pctDouble(calls - prevCalls, prevCalls)));
            }
            return out;
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    // ===================== planDistribution =====================

    @Override
    public List<PlanSlice> planDistribution() {
        OpsAccessContext ctx = accessResolver.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            List<Long> owned = ownedIdsOrNull(ctx);
            if (ownedEmpty(owned)) {
                return List.of();
            }
            Map<String, String> pkgNames = new LinkedHashMap<>();
            for (SysPackage p : packageMapper.selectList(new LambdaQueryWrapper<>())) {
                pkgNames.put(p.getCode(), p.getName());
            }
            Instant now = Instant.now();
            Map<String, Long> counts = new LinkedHashMap<>();
            for (SysTenant t : loadTenants(owned)) {
                if (t.getPlans() == null) {
                    continue;
                }
                for (TenantPlan p : t.getPlans()) {
                    // 只计真正 active 的授权（未撤销且未过期）；过期未撤销的历史授权不再计入，
                    // 否则中心「活跃订阅」总数会被撑大、与图例明细对不上。
                    if (p.isActive(now)) {
                        counts.merge(p.packageCode(), 1L, Long::sum);
                    }
                }
            }
            List<PlanSlice> out = new ArrayList<>(counts.size());
            counts.forEach((code, cnt) -> out.add(new PlanSlice(code, pkgNames.getOrDefault(code, code), cnt)));
            out.sort((a, b) -> Long.compare(b.count(), a.count()));
            return out;
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    // ===================== renewalTrend =====================

    @Override
    public RenewalTrend renewalTrend() {
        OpsAccessContext ctx = accessResolver.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            List<Long> owned = ownedIdsOrNull(ctx);
            List<String> months = new ArrayList<>(6);
            List<Double> rates = new ArrayList<>(6);
            YearMonth nowM = YearMonth.now();
            if (ownedEmpty(owned)) {
                for (int i = 5; i >= 0; i--) {
                    months.add(nowM.minusMonths(i).toString());
                    rates.add(0.0);
                }
                return new RenewalTrend(months, rates, 0, 0, 0);
            }
            for (int i = 5; i >= 0; i--) {
                YearMonth ym = nowM.minusMonths(i);
                months.add(ym.toString());
                LocalDateTime from = ym.atDay(1).atStartOfDay();
                LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay();
                long renew = countRenewal(SysTenantRenewalLog.EVENT_RENEW, from, to, owned);
                long revoke = countRenewal(SysTenantRenewalLog.EVENT_REVOKE, from, to, owned);
                rates.add(pctDouble(renew, renew + revoke));
            }
            LocalDateTime monthStart = nowM.atDay(1).atStartOfDay();
            LocalDateTime monthEnd = nowM.plusMonths(1).atDay(1).atStartOfDay();
            long renewedThisMonth = countRenewal(SysTenantRenewalLog.EVENT_RENEW, monthStart, monthEnd, owned);
            long addonThisMonth = seatAddonMapper.selectCount(
                    new LambdaQueryWrapper<com.wensaas.core.impl.ops.SysTenantSeatAddon>()
                            .ge(com.wensaas.core.impl.ops.SysTenantSeatAddon::getCreatedAt,
                                    monthStart.atZone(ZoneId.systemDefault()).toInstant())
                            .lt(com.wensaas.core.impl.ops.SysTenantSeatAddon::getCreatedAt,
                                    monthEnd.atZone(ZoneId.systemDefault()).toInstant())
                            .in(owned != null, com.wensaas.core.impl.ops.SysTenantSeatAddon::getTenantId,
                                    ownedOrDummy(owned)));
            long expiringSoon = countExpiringWithin(owned, 30);
            return new RenewalTrend(months, rates, expiringSoon, renewedThisMonth, addonThisMonth);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    // ===================== resourceUsage =====================

    @Override
    public ResourceUsage resourceUsage() {
        OpsAccessContext ctx = accessResolver.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            List<Long> owned = ownedIdsOrNull(ctx);
            if (ownedEmpty(owned)) {
                return new ResourceUsage(0, 0, 0, 0, List.of(), 0);
            }
            long totalFiles = 0, totalSize = 0, platformSize = 0, tenantOssSize = 0;
            List<Map<String, Object>> byBackend = fileMapper.selectMaps(new QueryWrapper<SysFile>()
                    .select("backend_type", "COUNT(*) AS cnt", "COALESCE(SUM(size_bytes),0) AS total")
                    .isNull("deleted_at")
                    .in(owned != null, "tenant_id", ownedOrDummy(owned))
                    .groupBy("backend_type"));
            for (Map<String, Object> r : byBackend) {
                String backend = (String) r.get("backend_type");
                long cnt = asLong(r.get("cnt"));
                long total = asLong(r.get("total"));
                totalFiles += cnt;
                totalSize += total;
                if (backend == null || backend.equalsIgnoreCase("local") || backend.toLowerCase().contains("platform")) {
                    platformSize += total;
                } else {
                    tenantOssSize += total;
                }
            }
            List<Map<String, Object>> topRows = fileMapper.selectMaps(new QueryWrapper<SysFile>()
                    .select("tenant_id", "COALESCE(SUM(size_bytes),0) AS total")
                    .isNull("deleted_at")
                    .in(owned != null, "tenant_id", ownedOrDummy(owned))
                    .groupBy("tenant_id").orderByDesc("total").last("LIMIT 5"));
            // 含软删租户按 id 取名（不走会过滤软删的 EntityNameResolver）：已删除租户展示真实名 + 标注，
            // 而非笼统的雪花 id。tenant_id=0 是平台级文件（无 sys_tenant 行），保留 "0" 供前端识别。
            Map<Long, String> nameById = new LinkedHashMap<>();
            Map<Long, Boolean> deletedById = new LinkedHashMap<>();
            List<Long> lookupIds = topRows.stream()
                    .map(r -> asLong(r.get("tenant_id"))).filter(id -> id != 0L).distinct().toList();
            if (!lookupIds.isEmpty()) {
                for (SysTenant t : tenantMapper.selectNamesIncludeDeleted(lookupIds)) {
                    nameById.put(t.getId(), t.getName());
                    deletedById.put(t.getId(), t.getDeletedAt() != null);
                }
            }
            List<TenantSize> top = new ArrayList<>(topRows.size());
            for (Map<String, Object> r : topRows) {
                long tid = asLong(r.get("tenant_id"));
                String name = nameById.getOrDefault(tid, tid == 0L ? String.valueOf(tid) : "已删除租户");
                boolean deleted = deletedById.getOrDefault(tid, false);
                top.add(new TenantSize(tid, name, asLong(r.get("total")), deleted));
            }
            Long increment = (Long) fileMapper.selectObjs(new QueryWrapper<SysFile>()
                    .select("COALESCE(SUM(size_bytes),0) AS total")
                    .isNull("deleted_at")
                    .ge("created_at", LocalDateTime.now().minusDays(30))
                    .in(owned != null, "tenant_id", ownedOrDummy(owned)))
                    .stream().findFirst().map(this::asLongObj).orElse(0L);
            return new ResourceUsage(totalFiles, totalSize, platformSize, tenantOssSize, top, increment);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    // ===================== apiWebhookOverview =====================

    @Override
    public ApiWebhookOverview apiWebhookOverview() {
        OpsAccessContext ctx = accessResolver.require();
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            List<Long> owned = ownedIdsOrNull(ctx);
            if (ownedEmpty(owned)) {
                return new ApiWebhookOverview(0, 0, 0, 0, List.of());
            }
            LocalDateTime since7 = LocalDateTime.now().minusDays(7);
            LocalDateTime now = LocalDateTime.now();
            long apiCalls = countApi(since7, now, owned, false);
            long apiErrors = countApi(since7, now, owned, true);

            long webhookPushes = webhookLogMapper.selectCount(new LambdaQueryWrapper<SysWebhookLog>()
                    .ge(SysWebhookLog::getCreatedAt, since7)
                    .in(owned != null, SysWebhookLog::getTenantId, ownedOrDummy(owned)));
            long webhookSuccess = webhookLogMapper.selectCount(new LambdaQueryWrapper<SysWebhookLog>()
                    .ge(SysWebhookLog::getCreatedAt, since7)
                    .eq(SysWebhookLog::getFinalStatus, "success")
                    .in(owned != null, SysWebhookLog::getTenantId, ownedOrDummy(owned)));
            double successRate = pctDouble(webhookSuccess, webhookPushes);

            List<Map<String, Object>> failRows = webhookLogMapper.selectMaps(new QueryWrapper<SysWebhookLog>()
                    .select("webhook_id", "event_type", "COUNT(*) AS fail_count")
                    .ge("created_at", since7)
                    .ne("final_status", "success")
                    .in(owned != null, "tenant_id", ownedOrDummy(owned))
                    .groupBy("webhook_id", "event_type")
                    .orderByDesc("fail_count").last("LIMIT 4"));
            // 解析 webhook_id → 端点名（展示名而非雪花 id）。已删除端点查不到 → 前端回退 #id。
            Map<Long, String> webhookNames = new LinkedHashMap<>();
            List<Long> failWebhookIds = failRows.stream().map(r -> asLong(r.get("webhook_id"))).distinct().toList();
            if (!failWebhookIds.isEmpty()) {
                for (SysWebhook w : webhookMapper.selectBatchIds(failWebhookIds)) {
                    webhookNames.put(w.getId(), w.getName());
                }
            }
            List<WebhookFailure> failures = new ArrayList<>(failRows.size());
            for (Map<String, Object> r : failRows) {
                long wid = asLong(r.get("webhook_id"));
                String et = (String) r.get("event_type");
                long fc = asLong(r.get("fail_count"));
                long total = webhookLogMapper.selectCount(new LambdaQueryWrapper<SysWebhookLog>()
                        .eq(SysWebhookLog::getWebhookId, wid)
                        .eq(SysWebhookLog::getEventType, et)
                        .ge(SysWebhookLog::getCreatedAt, since7));
                long ok = total - fc;
                failures.add(new WebhookFailure(wid, webhookNames.get(wid), et, fc, pctDouble(ok, total)));
            }
            return new ApiWebhookOverview(apiCalls, apiErrors, webhookPushes, successRate, failures);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    // ===================== 私有 counters =====================

    private List<Long> dailySeries(int days, BiFunction<LocalDateTime, LocalDateTime, Long> counter) {
        List<Long> out = new ArrayList<>(days);
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            out.add(counter.apply(d.atStartOfDay(), d.plusDays(1).atStartOfDay()));
        }
        return out;
    }

    private long countTenants(List<Long> owned, int status) {
        return tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getStatus, status)
                .isNull(SysTenant::getDeletedAt)
                .in(owned != null, SysTenant::getId, ownedOrDummy(owned)));
    }

    private long countNewTenants(LocalDateTime from, LocalDateTime to, List<Long> owned) {
        return tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>()
                .ge(SysTenant::getCreatedAt, from).lt(SysTenant::getCreatedAt, to)
                .in(owned != null, SysTenant::getId, ownedOrDummy(owned)));
    }

    private long countLostTenants(LocalDateTime from, LocalDateTime to, List<Long> owned) {
        return tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>()
                .ge(SysTenant::getDeletedAt, from).lt(SysTenant::getDeletedAt, to)
                .in(owned != null, SysTenant::getId, ownedOrDummy(owned)));
    }

    private long distinctLoginUsers(LocalDateTime from, LocalDateTime to, List<Long> owned) {
        QueryWrapper<SysLoginLog> qw = new QueryWrapper<SysLoginLog>()
                .select("DISTINCT operator_id")
                .eq("action", "login").eq("result", "success")
                .ge("created_at", from).lt("created_at", to);
        if (owned != null) {
            qw.in("tenant_id", ownedOrDummy(owned));
        }
        return loginLogMapper.selectObjs(qw).stream().filter(Objects::nonNull).count();
    }

    private long countLogins(LocalDateTime from, LocalDateTime to, List<Long> owned) {
        return loginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
                .eq(SysLoginLog::getAction, "login").eq(SysLoginLog::getResult, "success")
                .ge(SysLoginLog::getCreatedAt, from).lt(SysLoginLog::getCreatedAt, to)
                .in(owned != null, SysLoginLog::getTenantId, ownedOrDummy(owned)));
    }

    private long countApi(LocalDateTime from, LocalDateTime to, List<Long> owned, boolean errorsOnly) {
        LambdaQueryWrapper<SysApiCallLog> w = new LambdaQueryWrapper<SysApiCallLog>()
                .ge(SysApiCallLog::getCreatedAt, from).lt(SysApiCallLog::getCreatedAt, to)
                .in(owned != null, SysApiCallLog::getTenantId, ownedOrDummy(owned));
        if (errorsOnly) {
            w.ge(SysApiCallLog::getStatusCode, 400);
        }
        return apiCallLogMapper.selectCount(w);
    }

    private long countOpenTickets(List<Long> owned) {
        return ticketMapper.selectCount(new LambdaQueryWrapper<TicketTicket>()
                .in(TicketTicket::getStatus, List.of(0, 1))
                .isNull(TicketTicket::getDeletedAt)
                .in(owned != null, TicketTicket::getTenantId, ownedOrDummy(owned)));
    }

    private long countNewTickets(LocalDateTime from, LocalDateTime to, List<Long> owned) {
        return ticketMapper.selectCount(new LambdaQueryWrapper<TicketTicket>()
                .ge(TicketTicket::getCreatedAt, from).lt(TicketTicket::getCreatedAt, to)
                .in(owned != null, TicketTicket::getTenantId, ownedOrDummy(owned)));
    }

    private long countClosedTickets(LocalDateTime from, LocalDateTime to, List<Long> owned) {
        Instant fi = from.atZone(ZoneId.systemDefault()).toInstant();
        Instant ti = to.atZone(ZoneId.systemDefault()).toInstant();
        return ticketMapper.selectCount(new LambdaQueryWrapper<TicketTicket>()
                .ge(TicketTicket::getClosedAt, fi).lt(TicketTicket::getClosedAt, ti)
                .in(owned != null, TicketTicket::getTenantId, ownedOrDummy(owned)));
    }

    private long countRenewal(String eventType, LocalDateTime from, LocalDateTime to, List<Long> owned) {
        return renewalLogMapper.selectCount(new LambdaQueryWrapper<SysTenantRenewalLog>()
                .eq(SysTenantRenewalLog::getEventType, eventType)
                .ge(SysTenantRenewalLog::getCreatedAt, from).lt(SysTenantRenewalLog::getCreatedAt, to)
                .in(owned != null, SysTenantRenewalLog::getTenantId, ownedOrDummy(owned)));
    }

    private long countExpiringWithin(List<Long> owned, int days) {
        Instant now = Instant.now();
        Instant limit = now.plusSeconds((long) days * 86400L);
        long count = 0;
        for (SysTenant t : loadTenants(owned)) {
            if (t.getPlans() == null) {
                continue;
            }
            for (TenantPlan p : t.getPlans()) {
                if (p.revokedAt() == null && p.expiresAt() != null) {
                    Instant exp = Instant.parse(p.expiresAt());
                    if (!exp.isBefore(now) && exp.isBefore(limit)) {
                        count++;
                        break; // 每租户计一次
                    }
                }
            }
        }
        return count;
    }

    private List<SysTenant> loadTenants(List<Long> owned) {
        return tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                .isNull(SysTenant::getDeletedAt)
                .in(owned != null, SysTenant::getId, ownedOrDummy(owned)));
    }

    // ===================== dataScope / 工具 =====================

    private List<Long> ownedIdsOrNull(OpsAccessContext ctx) {
        if (ctx.dataScope() != OpsDataScope.OPS_OWNED) {
            return null;
        }
        return tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                        .select(SysTenant::getId)
                        .eq(SysTenant::getCsmUserId, ctx.userId())).stream()
                .map(SysTenant::getId).toList();
    }

    private static boolean ownedEmpty(List<Long> owned) {
        return owned != null && owned.isEmpty();
    }

    /** owned 非空时返回自身；owned==null（全量）调用方已用 condition 关掉 in，这里仅避免 NPE。 */
    private static List<Long> ownedOrDummy(List<Long> owned) {
        return owned == null ? List.of(-1L) : owned;
    }

    private static int rangeDays(String range) {
        if (range == null) {
            return 30;
        }
        return switch (range) {
            case "7d" -> 7;
            case "mtd" -> LocalDate.now().getDayOfMonth();
            case "qtr" -> 90;
            default -> 30;
        };
    }

    private static long pct(long delta, long base) {
        return base <= 0 ? 0 : Math.round(delta * 100.0 / base);
    }

    private static double pctDouble(long part, long base) {
        return base <= 0 ? 0 : Math.round(part * 1000.0 / base) / 10.0;
    }

    private static List<Long> zeros(int n) {
        List<Long> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(0L);
        }
        return out;
    }

    private static long asLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private long asLongObj(Object o) {
        return asLong(o);
    }
}
