package com.wensaas.core.impl.ticket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.ops.access.OpsAccessContext;
import com.wensaas.core.api.ops.access.OpsAccessContextResolver;
import com.wensaas.core.api.ops.access.OpsDataScope;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.ticket.OpsTicketService;
import com.wensaas.core.api.ticket.TicketAttachmentView;
import com.wensaas.core.api.ticket.TicketAuthorSide;
import com.wensaas.core.api.ticket.TicketDetailView;
import com.wensaas.core.api.ticket.TicketException;
import com.wensaas.core.api.ticket.TicketMessageView;
import com.wensaas.core.api.ticket.TicketPage;
import com.wensaas.core.api.ticket.TicketPriority;
import com.wensaas.core.api.ticket.TicketStatus;
import com.wensaas.core.api.ticket.TicketSummary;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;
import com.wensaas.core.api.auth.user.OpsAccountQuery;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 运营侧工单服务实现（运营客服跨租户处理）。
 *
 * <p>读写跨租户：每个方法用 {@link InterceptorIgnoreHelper} 关闭多租户 SQL 改写（同
 * {@code OpsHealthServiceImpl} 的取向）；写子表显式带工单 {@code tenant_id}。通知在退出
 * ignore 块后再发（避免站内信落库时漏注 tenant_id）。列表与操作按运营 dataScope 收敛。
 */
public class OpsTicketServiceImpl implements OpsTicketService {

    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();
    private static final int ACTIVE_PENDING = 0;
    private static final int ACTIVE_PROCESSING = 1;

    private final TicketTicketMapper ticketMapper;
    private final TicketMessageMapper messageMapper;
    private final TicketSupport support;
    private final TenantQueryService tenantQuery;
    private final OpsAccountQuery opsAccounts;
    private final OpsAccessContextResolver accessResolver;

    public OpsTicketServiceImpl(TicketTicketMapper ticketMapper,
                                TicketMessageMapper messageMapper,
                                TicketSupport support,
                                TenantQueryService tenantQuery,
                                OpsAccountQuery opsAccounts,
                                OpsAccessContextResolver accessResolver) {
        this.ticketMapper = ticketMapper;
        this.messageMapper = messageMapper;
        this.support = support;
        this.tenantQuery = tenantQuery;
        this.opsAccounts = opsAccounts;
        this.accessResolver = accessResolver;
    }

    @Override
    public TicketPage list(OpsTicketQuery q) {
        OpsAccessContext ctx = accessResolver.require();
        int p = q.page() <= 0 ? 1 : q.page();
        int s = q.size() <= 0 ? 20 : Math.min(q.size(), 200);
        return inIgnore(() -> {
            List<Long> ownedIds = ownedTenantIdsOrNull(ctx);
            if (ownedIds != null && ownedIds.isEmpty()) {
                return TicketPage.empty(p, s);
            }
            Instant now = Instant.now();
            LambdaQueryWrapper<TicketTicket> w = new LambdaQueryWrapper<TicketTicket>()
                    .in(ownedIds != null, TicketTicket::getTenantId, ownedIds == null ? List.of() : ownedIds)
                    .eq(q.status() != null, TicketTicket::getStatus, q.status() == null ? null : q.status().dbValue())
                    .eq(q.tenantId() != null, TicketTicket::getTenantId, q.tenantId())
                    .eq(q.type() != null, TicketTicket::getType, q.type() == null ? null : q.type().dbValue())
                    .eq(q.priority() != null, TicketTicket::getPriority, q.priority() == null ? null : q.priority().dbValue())
                    .eq(q.mineOnly(), TicketTicket::getAssigneeId, ctx.userId())
                    .eq(q.assigneeId() != null, TicketTicket::getAssigneeId, q.assigneeId());
            if (q.keyword() != null && !q.keyword().isBlank()) {
                String kw = q.keyword().trim();
                w.and(x -> x.like(TicketTicket::getTitle, kw).or().like(TicketTicket::getTicketNo, kw));
            }
            applySlaState(w, q.slaState(), now);
            w.orderByDesc(TicketTicket::getLastActivityAt);

            Page<TicketTicket> result = ticketMapper.selectPage(new Page<>(p, s), w);
            Map<Long, String> names = tenantNames(result.getRecords());
            List<TicketSummary> items = new ArrayList<>(result.getRecords().size());
            for (TicketTicket t : result.getRecords()) {
                items.add(support.toSummary(t, names.get(t.getTenantId()), now));
            }
            return new TicketPage(items, result.getTotal(), p, s);
        });
    }

    @Override
    public TicketDetailView detail(long ticketId) {
        OpsAccessContext ctx = accessResolver.require();
        return inIgnore(() -> {
            TicketTicket t = requireTicket(ticketId);
            requireScope(ctx, t);
            TenantInfo tenant = tenantQuery.tenantById(t.getTenantId());
            return support.toDetail(t, tenant == null ? null : tenant.name(), Instant.now());
        });
    }

    @Override
    @Transactional
    public void claim(long ticketId) {
        OpsAccessContext ctx = accessResolver.require();
        List<NotifyTask> pending = new ArrayList<>();
        inIgnore(() -> {
            TicketTicket t = requireTicket(ticketId);
            requireScope(ctx, t);
            // ② 审计对象名：加载后即登记工单号（业务前，失败也留意图）
            AuditObjects.add(null, String.valueOf(ticketId), t.getTicketNo());
            if (t.getStatus() != TicketStatus.PENDING.dbValue()) {
                throw new TicketException(TicketException.Code.ILLEGAL_STATE, "仅待处理工单可认领");
            }
            long me = ctx.userId();
            t.setAssigneeId(me);
            t.setAssigneeName(support.userName(me));
            t.setStatus(TicketStatus.PROCESSING.dbValue());
            touchAndUpdate(t);
            pending.add(new NotifyTask(t.getTenantId(), List.of(t.getSubmitterId()), "base.ticket.assigned",
                    Map.of("ticket_no", t.getTicketNo(),
                            "assignee_name", t.getAssigneeName() == null ? "" : t.getAssigneeName()),
                    t.getId()));
            return null;
        });
        fire(pending);
    }

    @Override
    @Transactional
    public void transfer(long ticketId, long toAssigneeId) {
        OpsAccessContext ctx = accessResolver.require();
        inIgnore(() -> {
            TicketTicket t = requireTicket(ticketId);
            requireScope(ctx, t);
            // ② 审计对象名：加载后即登记工单号（业务前，失败也留意图）
            AuditObjects.add(null, String.valueOf(ticketId), t.getTicketNo());
            if (t.getStatus() == TicketStatus.CLOSED.dbValue()) {
                throw new TicketException(TicketException.Code.ILLEGAL_STATE, "已关闭工单不可转单");
            }
            t.setAssigneeId(toAssigneeId);
            t.setAssigneeName(support.userName(toAssigneeId));
            if (t.getStatus() == TicketStatus.PENDING.dbValue()) {
                t.setStatus(TicketStatus.PROCESSING.dbValue());
            }
            touchAndUpdate(t);
            return null;
        });
    }

    @Override
    @Transactional
    public void changePriority(long ticketId, TicketPriority priority) {
        OpsAccessContext ctx = accessResolver.require();
        inIgnore(() -> {
            TicketTicket t = requireTicket(ticketId);
            requireScope(ctx, t);
            // ② 审计对象名：加载后即登记工单号（业务前，失败也留意图）
            AuditObjects.add(null, String.valueOf(ticketId), t.getTicketNo());
            Instant created = t.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant();
            t.setPriority(priority.dbValue());
            t.setSlaDueAt(created.plus(priority.slaHours(), ChronoUnit.HOURS));
            t.setSlaWarned(false);
            t.setSlaBreached(false);
            touchAndUpdate(t);
            return null;
        });
    }

    @Override
    @Transactional
    public TicketMessageView reply(long ticketId, String content) {
        OpsAccessContext ctx = accessResolver.require();
        List<NotifyTask> pending = new ArrayList<>();
        TicketMessageView view = inIgnore(() -> {
            TicketTicket t = requireTicket(ticketId);
            requireScope(ctx, t);
            // ② 审计对象名：加载后即登记工单号（业务前，失败也留意图）
            AuditObjects.add(null, String.valueOf(ticketId), t.getTicketNo());
            if (t.getStatus() == TicketStatus.CLOSED.dbValue()) {
                throw new TicketException(TicketException.Code.ILLEGAL_STATE, "已关闭工单不可回复，请先重启");
            }
            long me = ctx.userId();
            if (t.getStatus() == TicketStatus.PENDING.dbValue()) {
                t.setAssigneeId(me);
                t.setAssigneeName(support.userName(me));
                t.setStatus(TicketStatus.PROCESSING.dbValue());
            }
            TicketMessage m = insertOpsMessage(t, me, content);
            touchAndUpdate(t);
            String assigneeName = t.getAssigneeName() != null ? t.getAssigneeName() : support.userName(me);
            pending.add(new NotifyTask(t.getTenantId(), List.of(t.getSubmitterId()), "base.ticket.replied",
                    Map.of("ticket_no", t.getTicketNo(), "assignee_name", assigneeName == null ? "" : assigneeName),
                    t.getId()));
            return support.toMessageView(m);
        });
        fire(pending);
        return view;
    }

    @Override
    @Transactional
    public void resolve(long ticketId) {
        OpsAccessContext ctx = accessResolver.require();
        List<NotifyTask> pending = new ArrayList<>();
        inIgnore(() -> {
            TicketTicket t = requireTicket(ticketId);
            requireScope(ctx, t);
            // ② 审计对象名：加载后即登记工单号（业务前，失败也留意图）
            AuditObjects.add(null, String.valueOf(ticketId), t.getTicketNo());
            if (t.getStatus() != TicketStatus.PROCESSING.dbValue()) {
                throw new TicketException(TicketException.Code.ILLEGAL_STATE, "仅处理中工单可标记已解决");
            }
            Instant now = Instant.now();
            t.setStatus(TicketStatus.RESOLVED.dbValue());
            t.setResolvedAt(now);
            support.touch(t, now);
            ticketMapper.updateById(t);
            pending.add(new NotifyTask(t.getTenantId(), List.of(t.getSubmitterId()), "base.ticket.resolved",
                    Map.of("ticket_no", t.getTicketNo()), t.getId()));
            return null;
        });
        fire(pending);
    }

    @Override
    @Transactional
    public void close(long ticketId, String reason) {
        OpsAccessContext ctx = accessResolver.require();
        inIgnore(() -> {
            TicketTicket t = requireTicket(ticketId);
            requireScope(ctx, t);
            // ② 审计对象名：加载后即登记工单号（业务前，失败也留意图）
            AuditObjects.add(null, String.valueOf(ticketId), t.getTicketNo());
            if (t.getStatus() == TicketStatus.CLOSED.dbValue()) {
                throw new TicketException(TicketException.Code.ILLEGAL_STATE, "工单已关闭");
            }
            Instant now = Instant.now();
            t.setStatus(TicketStatus.CLOSED.dbValue());
            t.setClosedAt(now);
            t.setCloseReason(reason);
            support.touch(t, now);
            ticketMapper.updateById(t);
            return null;
        });
    }

    @Override
    @Transactional
    public void reopen(long ticketId) {
        OpsAccessContext ctx = accessResolver.require();
        inIgnore(() -> {
            TicketTicket t = requireTicket(ticketId);
            requireScope(ctx, t);
            // ② 审计对象名：加载后即登记工单号（业务前，失败也留意图）
            AuditObjects.add(null, String.valueOf(ticketId), t.getTicketNo());
            if (t.getStatus() != TicketStatus.CLOSED.dbValue()) {
                throw new TicketException(TicketException.Code.ILLEGAL_STATE, "仅已关闭工单可强制重启");
            }
            t.setStatus(TicketStatus.PROCESSING.dbValue());
            t.setClosedAt(null);
            t.setCloseReason(null);
            t.setResolvedAt(null);
            touchAndUpdate(t);
            return null;
        });
    }

    @Override
    @Transactional
    public TicketAttachmentView addAttachment(long ticketId, Long messageId, String fileName,
                                              String mimeType, long sizeBytes, InputStream content) {
        OpsAccessContext ctx = accessResolver.require();
        return inIgnore(() -> {
            TicketTicket t = requireTicket(ticketId);
            requireScope(ctx, t);
            // ② 审计对象名：加载后即登记工单号（业务前，失败也留意图）
            AuditObjects.add(null, String.valueOf(ticketId), t.getTicketNo());
            TicketAttachment saved = support.addAttachment(t, messageId, ctx.userId(),
                    fileName, mimeType, sizeBytes, content);
            touchAndUpdate(t);
            return support.toAttachmentView(saved);
        });
    }

    @Override
    public String attachmentDownloadUrl(long ticketId, long attachmentId) {
        OpsAccessContext ctx = accessResolver.require();
        return inIgnore(() -> {
            TicketTicket t = requireTicket(ticketId);
            requireScope(ctx, t);
            return support.downloadUrl(t, attachmentId);
        });
    }

    @Override
    public List<AssigneeOption> assigneeOptions() {
        accessResolver.require();
        return inIgnore(() -> opsAccounts.activeOpsUsers().stream()
                .map(u -> new AssigneeOption(u.id(), u.displayName()))
                .toList());
    }

    // ===================== 定时任务委托 =====================

    @Override
    @Transactional
    public SlaScanResult runSlaScan(Instant now) {
        List<NotifyTask> pending = new ArrayList<>();
        int[] counts = new int[2];
        inIgnore(() -> {
            List<TicketTicket> active = ticketMapper.selectList(new LambdaQueryWrapper<TicketTicket>()
                    .in(TicketTicket::getStatus, ACTIVE_PENDING, ACTIVE_PROCESSING));
            for (TicketTicket t : active) {
                if (t.getSlaDueAt() == null) {
                    continue;
                }
                if (!now.isBefore(t.getSlaDueAt()) && !Boolean.TRUE.equals(t.getSlaBreached())) {
                    t.setSlaBreached(true);
                    t.setSlaWarned(true);
                    ticketMapper.updateById(t);
                    pending.add(breachTask(t));
                    counts[1]++;
                } else if (now.isBefore(t.getSlaDueAt())
                        && !now.isBefore(support.warnAt(t))
                        && !Boolean.TRUE.equals(t.getSlaWarned())) {
                    t.setSlaWarned(true);
                    ticketMapper.updateById(t);
                    if (t.getAssigneeId() != null) {
                        pending.add(new NotifyTask(TenantContext.PLATFORM_TENANT_ID, List.of(t.getAssigneeId()),
                                "base.ticket.sla_warning",
                                Map.of("ticket_no", t.getTicketNo(),
                                        "sla_due_at", t.getSlaDueAt().toString()),
                                t.getId()));
                    }
                    counts[0]++;
                }
            }
            return null;
        });
        fire(pending);
        return new SlaScanResult(counts[0], counts[1]);
    }

    @Override
    @Transactional
    public int runAutoClose(Instant now) {
        List<NotifyTask> pending = new ArrayList<>();
        int[] closed = new int[1];
        inIgnore(() -> {
            Instant cutoff = now.minus(TicketServiceImpl.REOPEN_WINDOW_DAYS, ChronoUnit.DAYS);
            List<TicketTicket> resolved = ticketMapper.selectList(new LambdaQueryWrapper<TicketTicket>()
                    .eq(TicketTicket::getStatus, TicketStatus.RESOLVED.dbValue())
                    .le(TicketTicket::getResolvedAt, cutoff));
            for (TicketTicket t : resolved) {
                t.setStatus(TicketStatus.CLOSED.dbValue());
                t.setClosedAt(now);
                t.setCloseReason("已解决 7 天未回应，系统自动关闭");
                support.touch(t, now);
                ticketMapper.updateById(t);
                pending.add(new NotifyTask(t.getTenantId(), List.of(t.getSubmitterId()), "base.ticket.auto_closed",
                        Map.of("ticket_no", t.getTicketNo()), t.getId()));
                closed[0]++;
            }
            return null;
        });
        fire(pending);
        return closed[0];
    }

    // ===================== 内部 =====================

    private NotifyTask breachTask(TicketTicket t) {
        // 处理人 + 客服主管（以全部运营人员近似，含超管）
        List<Long> targets = new ArrayList<>(support.opsUserIds());
        if (t.getAssigneeId() != null) {
            targets.add(t.getAssigneeId());
        }
        return new NotifyTask(TenantContext.PLATFORM_TENANT_ID, targets, "base.ticket.sla_breach",
                Map.of("ticket_no", t.getTicketNo()), t.getId());
    }

    private void fire(List<NotifyTask> tasks) {
        for (NotifyTask task : tasks) {
            support.notify(task.ctxTenant(), task.userIds(), task.templateCode(), task.vars(), task.ticketId());
        }
    }

    private void applySlaState(LambdaQueryWrapper<TicketTicket> w, String slaState, Instant now) {
        if (slaState == null || slaState.isBlank()) {
            return;
        }
        switch (slaState) {
            case "breach" -> w.in(TicketTicket::getStatus, ACTIVE_PENDING, ACTIVE_PROCESSING)
                    .and(x -> x.eq(TicketTicket::getSlaBreached, true).or().le(TicketTicket::getSlaDueAt, now));
            case "warning" -> w.in(TicketTicket::getStatus, ACTIVE_PENDING, ACTIVE_PROCESSING)
                    .eq(TicketTicket::getSlaWarned, true).gt(TicketTicket::getSlaDueAt, now);
            case "normal" -> w.in(TicketTicket::getStatus, ACTIVE_PENDING, ACTIVE_PROCESSING)
                    .eq(TicketTicket::getSlaWarned, false).gt(TicketTicket::getSlaDueAt, now);
            default -> { /* 未知值不过滤 */ }
        }
    }

    private TicketMessage insertOpsMessage(TicketTicket t, long authorId, String content) {
        TicketMessage m = new TicketMessage();
        m.setTenantId(t.getTenantId());
        m.setTicketId(t.getId());
        m.setAuthorId(authorId);
        m.setAuthorName(support.userName(authorId));
        m.setAuthorSide(TicketAuthorSide.OPS.dbValue());
        m.setContent(content);
        m.setCreatedAt(LocalDateTime.now());
        m.setCreatedBy(authorId);
        messageMapper.insert(m);
        return m;
    }

    private void touchAndUpdate(TicketTicket t) {
        support.touch(t, Instant.now());
        ticketMapper.updateById(t);
    }

    private TicketTicket requireTicket(long ticketId) {
        TicketTicket t = ticketMapper.selectById(ticketId);
        if (t == null) {
            throw new TicketException(TicketException.Code.TICKET_NOT_FOUND, "工单不存在：" + ticketId);
        }
        return t;
    }

    private void requireScope(OpsAccessContext ctx, TicketTicket t) {
        if (ctx.dataScope() != OpsDataScope.OPS_OWNED) {
            return;
        }
        TenantInfo tenant = tenantQuery.tenantById(t.getTenantId());
        if (tenant == null || tenant.csmUserId() == null || tenant.csmUserId() != ctx.userId()) {
            throw new TicketException(TicketException.Code.FORBIDDEN, "无权处理该租户工单");
        }
    }

    private List<Long> ownedTenantIdsOrNull(OpsAccessContext ctx) {
        if (ctx.dataScope() != OpsDataScope.OPS_OWNED) {
            return null;
        }
        return tenantQuery.tenantIdsByCsm(ctx.userId());
    }

    private Map<Long, String> tenantNames(List<TicketTicket> tickets) {
        List<Long> ids = tickets.stream().map(TicketTicket::getTenantId).distinct().toList();
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

    /** 退出 ignore 块后再发的通知任务（保证站内信落库时正确注入 tenant_id）。 */
    private record NotifyTask(long ctxTenant, List<Long> userIds, String templateCode,
                             Map<String, Object> vars, long ticketId) {
    }
}
