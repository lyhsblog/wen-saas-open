package com.wensaas.core.impl.ticket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.ticket.TicketAttachmentView;
import com.wensaas.core.api.ticket.TicketAuthorSide;
import com.wensaas.core.api.ticket.TicketDetailView;
import com.wensaas.core.api.ticket.TicketException;
import com.wensaas.core.api.ticket.TicketMessageView;
import com.wensaas.core.api.ticket.TicketPage;
import com.wensaas.core.api.ticket.TicketService;
import com.wensaas.core.api.ticket.TicketStatus;
import com.wensaas.core.api.ticket.TicketSummary;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;
import com.wensaas.core.impl.permission.eval.PermissionResolver;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 租户侧工单服务实现（运行在租户上下文）。
 */
public class TicketServiceImpl implements TicketService {

    static final int REOPEN_WINDOW_DAYS = 7;
    private static final String VIEW_ALL = "base.ticket.view_all";

    private final TicketTicketMapper ticketMapper;
    private final TicketMessageMapper messageMapper;
    private final TicketNoGenerator noGenerator;
    private final TicketSupport support;
    private final TenantQueryService tenantQuery;
    private final PermissionResolver permissionResolver;

    public TicketServiceImpl(TicketTicketMapper ticketMapper,
                             TicketMessageMapper messageMapper,
                             TicketNoGenerator noGenerator,
                             TicketSupport support,
                             TenantQueryService tenantQuery,
                             PermissionResolver permissionResolver) {
        this.ticketMapper = ticketMapper;
        this.messageMapper = messageMapper;
        this.noGenerator = noGenerator;
        this.support = support;
        this.tenantQuery = tenantQuery;
        this.permissionResolver = permissionResolver;
    }

    @Override
    @Transactional
    public TicketDetailView submit(SubmitCommand cmd) {
        long tenantId = TenantContext.require();
        UserPrincipal me = UserContext.require();
        Instant now = Instant.now();
        LocalDateTime nowLdt = LocalDateTime.now();

        TicketTicket t = new TicketTicket();
        t.setTenantId(tenantId);
        t.setTicketNo(noGenerator.next());
        t.setTitle(cmd.title());
        t.setType(cmd.type().dbValue());
        t.setPriority(cmd.priority().dbValue());
        t.setStatus(TicketStatus.PENDING.dbValue());
        t.setSubmitterId(me.userId());
        t.setSubmitterName(support.userName(me.userId()));
        t.setSlaDueAt(now.plus(cmd.priority().slaHours(), ChronoUnit.HOURS));
        t.setSlaWarned(false);
        t.setSlaBreached(false);
        t.setLastActivityAt(now);
        t.setCreatedAt(nowLdt);
        t.setCreatedBy(me.userId());
        t.setCreatedByDept(me.mainDeptId());
        t.setUpdatedAt(nowLdt);
        ticketMapper.insert(t);

        insertMessage(tenantId, t.getId(), me.userId(), t.getSubmitterName(),
                TicketAuthorSide.TENANT, cmd.content(), nowLdt);

        TenantInfo tenant = tenantQuery.tenantById(tenantId);
        String tenantName = tenant == null ? null : tenant.name();
        support.notify(TenantContext.PLATFORM_TENANT_ID, support.opsUserIds(), "base.ticket.submitted",
                Map.of("ticket_no", t.getTicketNo(), "title", t.getTitle(),
                        "tenant_name", tenantName == null ? "" : tenantName),
                t.getId());

        return support.toDetail(t, tenantName, now);
    }

    @Override
    public TicketPage myTickets(TicketStatus statusFilter, int page, int size) {
        long userId = UserContext.require().userId();
        int p = page <= 0 ? 1 : page;
        int s = size <= 0 ? 20 : Math.min(size, 200);
        LambdaQueryWrapper<TicketTicket> w = new LambdaQueryWrapper<TicketTicket>()
                .eq(TicketTicket::getSubmitterId, userId)
                .eq(statusFilter != null, TicketTicket::getStatus, statusFilter == null ? null : statusFilter.dbValue())
                .orderByDesc(TicketTicket::getLastActivityAt);
        return pageOf(w, p, s);
    }

    @Override
    public TicketPage tenantTickets(TicketStatus statusFilter, String keyword, int page, int size) {
        int p = page <= 0 ? 1 : page;
        int s = size <= 0 ? 20 : Math.min(size, 200);
        LambdaQueryWrapper<TicketTicket> w = new LambdaQueryWrapper<TicketTicket>()
                .eq(statusFilter != null, TicketTicket::getStatus, statusFilter == null ? null : statusFilter.dbValue())
                .orderByDesc(TicketTicket::getLastActivityAt);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            w.and(q -> q.like(TicketTicket::getTitle, kw).or().like(TicketTicket::getTicketNo, kw));
        }
        return pageOf(w, p, s);
    }

    @Override
    public TicketDetailView detail(long ticketId) {
        TicketTicket t = requireTicket(ticketId);
        requireOwnerOrViewAll(t);
        return support.toDetail(t, null, Instant.now());
    }

    @Override
    @Transactional
    public TicketMessageView reply(long ticketId, String content) {
        TicketTicket t = requireTicket(ticketId);
        requireOwner(t);
        // ② 审计对象名：加载后即登记工单号（业务前，失败也留意图）
        AuditObjects.add(null, String.valueOf(ticketId), t.getTicketNo());
        Instant now = Instant.now();
        LocalDateTime nowLdt = LocalDateTime.now();
        int st = t.getStatus();
        if (st == TicketStatus.CLOSED.dbValue()) {
            ensureReopenable(t);
            t.setStatus(TicketStatus.PROCESSING.dbValue());
            t.setClosedAt(null);
            t.setCloseReason(null);
        } else if (st == TicketStatus.RESOLVED.dbValue()) {
            t.setStatus(TicketStatus.PROCESSING.dbValue());
            t.setResolvedAt(null);
        }
        TicketMessage m = insertMessage(t.getTenantId(), t.getId(), t.getSubmitterId(),
                t.getSubmitterName(), TicketAuthorSide.TENANT, content, nowLdt);
        support.touch(t, now);
        ticketMapper.updateById(t);

        notifyAssigneeOnCustomerReply(t);
        return support.toMessageView(m);
    }

    @Override
    @Transactional
    public void confirmResolved(long ticketId) {
        TicketTicket t = requireTicket(ticketId);
        requireOwner(t);
        // ② 审计对象名：加载后即登记工单号（业务前，失败也留意图）
        AuditObjects.add(null, String.valueOf(ticketId), t.getTicketNo());
        if (t.getStatus() != TicketStatus.RESOLVED.dbValue()) {
            throw new TicketException(TicketException.Code.ILLEGAL_STATE, "仅已解决的工单可确认关闭");
        }
        Instant now = Instant.now();
        t.setStatus(TicketStatus.CLOSED.dbValue());
        t.setClosedAt(now);
        t.setCloseReason("用户确认已解决");
        support.touch(t, now);
        ticketMapper.updateById(t);
    }

    @Override
    @Transactional
    public void reopen(long ticketId) {
        TicketTicket t = requireTicket(ticketId);
        requireOwner(t);
        // ② 审计对象名：加载后即登记工单号（业务前，失败也留意图）
        AuditObjects.add(null, String.valueOf(ticketId), t.getTicketNo());
        int st = t.getStatus();
        if (st == TicketStatus.CLOSED.dbValue()) {
            ensureReopenable(t);
            t.setClosedAt(null);
            t.setCloseReason(null);
        } else if (st != TicketStatus.RESOLVED.dbValue()) {
            throw new TicketException(TicketException.Code.ILLEGAL_STATE, "仅已解决/已关闭(7天内)的工单可重新打开");
        }
        Instant now = Instant.now();
        t.setStatus(TicketStatus.PROCESSING.dbValue());
        t.setResolvedAt(null);
        support.touch(t, now);
        ticketMapper.updateById(t);
        notifyAssigneeOnCustomerReply(t);
    }

    @Override
    @Transactional
    public TicketAttachmentView addAttachment(long ticketId, Long messageId, String fileName,
                                              String mimeType, long sizeBytes, InputStream content) {
        TicketTicket t = requireTicket(ticketId);
        requireOwnerOrViewAll(t);
        // ② 审计对象名：加载后即登记工单号（业务前，失败也留意图）
        AuditObjects.add(null, String.valueOf(ticketId), t.getTicketNo());
        TicketAttachment saved = support.addAttachment(t, messageId, UserContext.require().userId(),
                fileName, mimeType, sizeBytes, content);
        Instant now = Instant.now();
        support.touch(t, now);
        ticketMapper.updateById(t);
        return support.toAttachmentView(saved);
    }

    @Override
    public String attachmentDownloadUrl(long ticketId, long attachmentId) {
        TicketTicket t = requireTicket(ticketId);
        requireOwnerOrViewAll(t);
        return support.downloadUrl(t, attachmentId);
    }

    // ===================== 内部 =====================

    private TicketPage pageOf(LambdaQueryWrapper<TicketTicket> w, int p, int s) {
        Page<TicketTicket> result = ticketMapper.selectPage(new Page<>(p, s), w);
        Instant now = Instant.now();
        List<TicketSummary> items = result.getRecords().stream()
                .map(t -> support.toSummary(t, null, now)).toList();
        return new TicketPage(items, result.getTotal(), p, s);
    }

    private TicketMessage insertMessage(long tenantId, long ticketId, long authorId, String authorName,
                                        TicketAuthorSide side, String content, LocalDateTime now) {
        TicketMessage m = new TicketMessage();
        m.setTenantId(tenantId);
        m.setTicketId(ticketId);
        m.setAuthorId(authorId);
        m.setAuthorName(authorName);
        m.setAuthorSide(side.dbValue());
        m.setContent(content);
        m.setCreatedAt(now);
        m.setCreatedBy(authorId);
        messageMapper.insert(m);
        return m;
    }

    private void notifyAssigneeOnCustomerReply(TicketTicket t) {
        if (t.getAssigneeId() != null) {
            support.notify(TenantContext.PLATFORM_TENANT_ID, List.of(t.getAssigneeId()), "base.ticket.customer_replied",
                    Map.of("ticket_no", t.getTicketNo(),
                            "submitter_name", t.getSubmitterName() == null ? "" : t.getSubmitterName()),
                    t.getId());
        }
    }

    private TicketTicket requireTicket(long ticketId) {
        TicketTicket t = ticketMapper.selectById(ticketId);
        if (t == null) {
            throw new TicketException(TicketException.Code.TICKET_NOT_FOUND, "工单不存在：" + ticketId);
        }
        return t;
    }

    private void requireOwner(TicketTicket t) {
        long userId = UserContext.require().userId();
        if (!t.getSubmitterId().equals(userId)) {
            throw new TicketException(TicketException.Code.FORBIDDEN, "无权操作该工单");
        }
    }

    private void requireOwnerOrViewAll(TicketTicket t) {
        UserPrincipal me = UserContext.require();
        if (t.getSubmitterId().equals(me.userId()) || me.tenantOwner()) {
            return;
        }
        if (!permissionResolver.has(me.tenantId(), me.userId(), VIEW_ALL)) {
            throw new TicketException(TicketException.Code.FORBIDDEN, "无权查看该工单");
        }
    }

    private void ensureReopenable(TicketTicket t) {
        Instant closedAt = t.getClosedAt();
        if (closedAt != null && closedAt.isBefore(Instant.now().minus(REOPEN_WINDOW_DAYS, ChronoUnit.DAYS))) {
            throw new TicketException(TicketException.Code.ILLEGAL_STATE, "工单关闭已超过 7 天，无法重新打开");
        }
    }
}
