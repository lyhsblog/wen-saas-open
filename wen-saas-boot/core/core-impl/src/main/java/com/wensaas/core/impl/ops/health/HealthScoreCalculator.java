package com.wensaas.core.impl.ops.health;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wensaas.core.api.auth.user.MemberCountQuery;
import com.wensaas.core.api.ops.health.HealthLevel;
import com.wensaas.core.api.ops.health.OpsHealthService.HealthMetrics;
import com.wensaas.core.api.ops.health.OpsHealthService.RiskSignal;
import com.wensaas.core.api.ticket.TicketPriority;
import com.wensaas.core.api.ticket.TicketStatus;
import com.wensaas.core.impl.audit.log.SysLoginLog;
import com.wensaas.core.impl.audit.log.SysLoginLogMapper;
import com.wensaas.core.impl.audit.log.SysOperationLog;
import com.wensaas.core.impl.audit.log.SysOperationLogMapper;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.ops.monitor.SysTenantRenewalLog;
import com.wensaas.core.impl.ops.monitor.SysTenantRenewalLogMapper;
import com.wensaas.core.api.tenant.TenantPlan;
import com.wensaas.core.impl.ticket.TicketTicket;
import com.wensaas.core.impl.ticket.TicketTicketMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 健康度评分核心计算器。
 *
 * <p>评分模型：基准 100，命中风险信号即按权重扣分，clamp[0,100]；再按阈值归档。
 * 计算所依赖的 sys_login_log / sys_user / sys_operation_log 都是混合表（带 tenant_id），
 * 调用方须先 {@code InterceptorIgnoreHelper.handle(tenantLine)} 关闭自动注入，
 * 本类对每张表显式 {@code eq tenant_id=被评估租户}。
 *
 * <p>R9（2026-06-05）：续费断档信号接 {@code sys_tenant_renewal_log} 数据源；数据增长指标接
 * {@code sys_operation_log} 中 {@code action='create'} 的建档量代理（无专用业务数据行计数源，取近似）。
 */
public class HealthScoreCalculator {

    private static final ZoneId TZ = ZoneId.of("Asia/Shanghai");
    /** 「数据增长」指标的代理动作：审计日志里的建档操作（@Auditable action）。 */
    private static final String CREATE_ACTION = "create";

    private final SysLoginLogMapper loginLogMapper;
    private final MemberCountQuery memberCount;
    private final SysOperationLogMapper opLogMapper;
    private final TicketTicketMapper ticketMapper;
    private final SysTenantRenewalLogMapper renewalLogMapper;
    private final HealthScoreSettings settings;

    public HealthScoreCalculator(SysLoginLogMapper loginLogMapper,
                                 MemberCountQuery memberCount,
                                 SysOperationLogMapper opLogMapper,
                                 TicketTicketMapper ticketMapper,
                                 SysTenantRenewalLogMapper renewalLogMapper,
                                 HealthScoreSettings settings) {
        this.loginLogMapper = loginLogMapper;
        this.memberCount = memberCount;
        this.opLogMapper = opLogMapper;
        this.ticketMapper = ticketMapper;
        this.renewalLogMapper = renewalLogMapper;
        this.settings = settings;
    }

    /** 计算结果。 */
    public record Computation(int score, HealthLevel level, List<RiskSignal> signals, HealthMetrics metrics) {}

    /**
     * 计算单租户健康度。调用前请确保已关闭多租户自动注入（见类注释）。
     *
     * @param tenant             被评估租户
     * @param systemPackageCodes 系统/基础套餐 code 集合（用于「仅基础包」判定）
     * @param now                计算基准时刻
     */
    public Computation compute(SysTenant tenant, Set<String> systemPackageCodes, Instant now) {
        long tid = tenant.getId();
        LocalDateTime nowLdt = LocalDateTime.ofInstant(now, TZ);
        LocalDateTime in30dAgo = nowLdt.minusDays(settings.noLoginDays());
        LocalDateTime metric30dAgo = nowLdt.minusDays(30);
        LocalDateTime metric60dAgo = nowLdt.minusDays(60);
        LocalDateTime startOfThisMonth = nowLdt.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfLastMonth = startOfThisMonth.minusMonths(1);

        List<RiskSignal> signals = new ArrayList<>();
        int score = 100;

        // ---- 信号 1：长期无登录（窗口内 0 次成功登录）----
        long loginsInWindow = countSuccessLogins(tid, in30dAgo, nowLdt);
        if (loginsInWindow == 0) {
            score -= add(signals, "no_login_30d",
                    settings.noLoginDays() + " 天无登录", settings.weightNoLogin());
        }

        // ---- 信号 2：月活下降（当月月活 < 上月 * ratio%）----
        int mauThisMonth = distinctActiveMembers(tid, startOfThisMonth, nowLdt);
        int mauLastMonth = distinctActiveMembers(tid, startOfLastMonth, startOfThisMonth);
        // 需上月基数达到最小阈值才判「下降」：待激活/低活跃租户上月哪怕只有 1 人登录、本月为 0，
        // 否则会被算成「月活下降 100%」的误报（小样本无统计意义）。
        if (mauLastMonth >= settings.mauMinBase()
                && mauThisMonth < mauLastMonth * settings.mauDropRatioPct() / 100.0) {
            int dropPct = (int) Math.round((1 - (double) mauThisMonth / mauLastMonth) * 100);
            score -= add(signals, "mau_drop", "月活下降 " + dropPct + "%", settings.weightMauDrop());
        }

        // ---- 信号 3：距套餐到期临近 ----
        if (tenant.getExpireAt() != null) {
            long daysLeft = java.time.Duration.between(now, tenant.getExpireAt()).toDays();
            if (daysLeft >= 0 && daysLeft < settings.expiringSoonDays()) {
                score -= add(signals, "expiring_soon", "距到期 " + daysLeft + " 天", settings.weightExpiring());
            }
        }

        // ---- 信号 7：仅基础包（无任何 active 业务包）----
        if (onlyBasicPackage(tenant, systemPackageCodes, now)) {
            score -= add(signals, "only_basic", "仅基础包", settings.weightOnlyBasic());
        }

        // ---- 信号 5/6：工单（未关闭 = 状态非「已解决/已关闭」）----
        long openTickets = countOpenTickets(tid);
        long urgentOpenTickets = countOpenUrgentTickets(tid);
        if (urgentOpenTickets > 0) {
            score -= add(signals, "urgent_ticket",
                    "紧急工单 " + urgentOpenTickets + " 个待处理", settings.weightUrgentTicket());
        }
        if (openTickets >= settings.ticketVolumeThreshold()) {
            score -= add(signals, "ticket_volume",
                    "未处理工单 " + openTickets + " 个", settings.weightTicketVolume());
        }

        // ---- 信号 4：续费断档（曾续费的老客户，套餐已过期且至今未续）----
        if (tenant.getExpireAt() != null && tenant.getExpireAt().isBefore(now)
                && hasPriorRenewal(tid)) {
            long expiredDays = java.time.Duration.between(tenant.getExpireAt(), now).toDays();
            score -= add(signals, "renewal_gap",
                    "续费断档：已过期 " + expiredDays + " 天未续费", settings.weightRenewalGap());
        }

        score = Math.max(0, Math.min(100, score));
        HealthLevel level = HealthLevel.fromScore(score, settings.healthyThreshold(), settings.warningThreshold());

        // ---- 最近 30 天指标快照 ----
        int totalMembers = (int) memberCount.countMembers(tid);
        int activeMembers30d = distinctActiveMembers(tid, metric30dAgo, nowLdt);
        long moduleAccess30d = countModuleAccess(tid, metric30dAgo, nowLdt);
        long moduleAccessPrev30d = countModuleAccess(tid, metric60dAgo, metric30dAgo);
        Integer moduleAccessChangePct = moduleAccessPrev30d == 0
                ? null
                : (int) Math.round((moduleAccess30d - moduleAccessPrev30d) * 100.0 / moduleAccessPrev30d);
        long dataGrowth30d = countCreates(tid, metric30dAgo, nowLdt);
        HealthMetrics metrics = new HealthMetrics(
                activeMembers30d, totalMembers, moduleAccess30d, moduleAccessChangePct,
                dataGrowth30d,         // 数据增长：近 30 天建档操作（action='create'）量代理
                (int) openTickets);    // 未关闭工单数

        return new Computation(score, level, signals, metrics);
    }

    private static int add(List<RiskSignal> signals, String code, String label, int weight) {
        signals.add(new RiskSignal(code, label, weight));
        return weight;
    }

    private long countSuccessLogins(long tenantId, LocalDateTime from, LocalDateTime to) {
        return loginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
                .eq(SysLoginLog::getTenantId, tenantId)
                .eq(SysLoginLog::getAction, "login")
                .eq(SysLoginLog::getResult, "success")
                .ge(SysLoginLog::getCreatedAt, from)
                .lt(SysLoginLog::getCreatedAt, to));
    }

    private int distinctActiveMembers(long tenantId, LocalDateTime from, LocalDateTime to) {
        QueryWrapper<SysLoginLog> qw = new QueryWrapper<SysLoginLog>()
                .select("DISTINCT operator_id")
                .eq("tenant_id", tenantId)
                .eq("action", "login")
                .eq("result", "success")
                .isNotNull("operator_id")
                .ge("created_at", from)
                .lt("created_at", to);
        return loginLogMapper.selectObjs(qw).size();
    }

    private long countModuleAccess(long tenantId, LocalDateTime from, LocalDateTime to) {
        return opLogMapper.selectCount(new LambdaQueryWrapper<SysOperationLog>()
                .eq(SysOperationLog::getTenantId, tenantId)
                .ge(SysOperationLog::getCreatedAt, from)
                .lt(SysOperationLog::getCreatedAt, to));
    }

    /** 近 30 天建档操作（action='create'）量，作为「数据增长」近似指标。 */
    private long countCreates(long tenantId, LocalDateTime from, LocalDateTime to) {
        Long n = opLogMapper.selectCount(new LambdaQueryWrapper<SysOperationLog>()
                .eq(SysOperationLog::getTenantId, tenantId)
                .eq(SysOperationLog::getAction, CREATE_ACTION)
                .ge(SysOperationLog::getCreatedAt, from)
                .lt(SysOperationLog::getCreatedAt, to));
        return n == null ? 0 : n;
    }

    /** 是否有过 renew 续费事件（区分「断档老客户」与「从未续费的试用」）。 */
    private boolean hasPriorRenewal(long tenantId) {
        Long n = renewalLogMapper.selectCount(new LambdaQueryWrapper<SysTenantRenewalLog>()
                .eq(SysTenantRenewalLog::getTenantId, tenantId)
                .eq(SysTenantRenewalLog::getEventType, SysTenantRenewalLog.EVENT_RENEW));
        return n != null && n > 0;
    }

    /** 未关闭工单数（状态非「已解决/已关闭」）。软删由 @TableLogic 自动过滤。 */
    private long countOpenTickets(long tenantId) {
        Long n = ticketMapper.selectCount(new LambdaQueryWrapper<TicketTicket>()
                .eq(TicketTicket::getTenantId, tenantId)
                .notIn(TicketTicket::getStatus,
                        TicketStatus.RESOLVED.dbValue(), TicketStatus.CLOSED.dbValue()));
        return n == null ? 0 : n;
    }

    /** 未关闭的紧急工单数。 */
    private long countOpenUrgentTickets(long tenantId) {
        Long n = ticketMapper.selectCount(new LambdaQueryWrapper<TicketTicket>()
                .eq(TicketTicket::getTenantId, tenantId)
                .eq(TicketTicket::getPriority, TicketPriority.URGENT.dbValue())
                .notIn(TicketTicket::getStatus,
                        TicketStatus.RESOLVED.dbValue(), TicketStatus.CLOSED.dbValue()));
        return n == null ? 0 : n;
    }

    private boolean onlyBasicPackage(SysTenant tenant, Set<String> systemPackageCodes, Instant now) {
        List<TenantPlan> plans = tenant.getPlans();
        if (plans == null || plans.isEmpty()) {
            return true; // 无任何套餐 → 视为无业务包
        }
        for (TenantPlan p : plans) {
            if (p.isActive(now) && !systemPackageCodes.contains(p.packageCode())) {
                return false; // 存在 active 的非系统包 → 不算"仅基础包"
            }
        }
        return true;
    }
}
