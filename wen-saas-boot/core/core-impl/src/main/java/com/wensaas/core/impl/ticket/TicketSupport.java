package com.wensaas.core.impl.ticket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.user.UserQueryService;
import com.wensaas.core.api.auth.user.OpsAccountQuery;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.ticket.TicketAttachmentView;
import com.wensaas.core.api.ticket.TicketException;
import com.wensaas.core.api.ticket.TicketAuthorSide;
import com.wensaas.core.api.ticket.TicketDetailView;
import com.wensaas.core.api.ticket.TicketMessageView;
import com.wensaas.core.api.ticket.TicketPriority;
import com.wensaas.core.api.ticket.TicketStatus;
import com.wensaas.core.api.ticket.TicketSummary;
import com.wensaas.core.api.ticket.TicketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工单读模型组装 + 通知发送的共享支撑（租户侧与运营侧 Service 复用）。
 *
 * <p>通知按目标方上下文发送：发给提交人用其租户上下文，发给客服用平台上下文（tenant_id=0）；
 * 收件人按用户 id 直投（{@link RecipientSpec#member}），通道/优先级跟随模板默认。
 */
@Component
public class TicketSupport {

    private static final Logger log = LoggerFactory.getLogger(TicketSupport.class);

    static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
    static final long MAX_TICKET_TOTAL_BYTES = 50L * 1024 * 1024;

    private final TicketMessageMapper messageMapper;
    private final TicketAttachmentMapper attachmentMapper;
    private final UserQueryService userQuery;
    private final OpsAccountQuery opsAccounts;
    private final FileService fileService;
    private final ObjectProvider<MessageService> messageServiceProvider;

    public TicketSupport(TicketMessageMapper messageMapper,
                         TicketAttachmentMapper attachmentMapper,
                         UserQueryService userQuery,
                         OpsAccountQuery opsAccounts,
                         FileService fileService,
                         ObjectProvider<MessageService> messageServiceProvider) {
        this.messageMapper = messageMapper;
        this.attachmentMapper = attachmentMapper;
        this.userQuery = userQuery;
        this.opsAccounts = opsAccounts;
        this.fileService = fileService;
        this.messageServiceProvider = messageServiceProvider;
    }

    // ===================== 附件（租户侧/运营侧共享） =====================

    /** 上传附件并关联工单：校验单附件≤10MB、单工单累计≤50MB；business-only。 */
    public TicketAttachment addAttachment(TicketTicket t, Long messageId, long userId, String fileName,
                                          String mimeType, long sizeBytes, java.io.InputStream content) {
        if (sizeBytes > MAX_ATTACHMENT_BYTES) {
            throw new TicketException(TicketException.Code.ATTACHMENT_TOO_LARGE, "单附件不可超过 10MB");
        }
        long used = attachmentMapper.selectList(new LambdaQueryWrapper<TicketAttachment>()
                        .select(TicketAttachment::getSizeBytes)
                        .eq(TicketAttachment::getTicketId, t.getId())).stream()
                .mapToLong(a -> a.getSizeBytes() == null ? 0L : a.getSizeBytes()).sum();
        if (used + sizeBytes > MAX_TICKET_TOTAL_BYTES) {
            throw new TicketException(TicketException.Code.ATTACHMENT_QUOTA_EXCEEDED, "单工单累计附件不可超过 50MB");
        }
        FileUploadResult result = fileService.uploadDirect(new FileUploadRequest(
                fileName, mimeType, sizeBytes, content,
                FileVisibility.BUSINESS_ONLY, "ticket", "Ticket", t.getId(), null));
        TicketAttachment a = new TicketAttachment();
        a.setTenantId(t.getTenantId());
        a.setTicketId(t.getId());
        a.setMessageId(messageId);
        a.setFileId(result.fileId());
        a.setFileName(result.fileName());
        a.setSizeBytes(result.sizeBytes());
        a.setCreatedAt(LocalDateTime.now());
        a.setCreatedBy(userId);
        attachmentMapper.insert(a);
        return a;
    }

    /** 校验附件归属工单并返回下载签名 URL（5 分钟）。 */
    public String downloadUrl(TicketTicket t, long attachmentId) {
        TicketAttachment a = attachmentMapper.selectById(attachmentId);
        if (a == null || !a.getTicketId().equals(t.getId())) {
            throw new TicketException(TicketException.Code.TICKET_NOT_FOUND, "附件不存在");
        }
        return fileService.signedDownloadUrl(a.getFileId(), java.time.Duration.ofMinutes(5));
    }

    // ===================== 读模型组装 =====================

    /** 衍生 SLA 状态：normal / warning（≥80%）/ breach（已过截止）。终态工单恒 normal。 */
    public String slaState(TicketTicket t, Instant now) {
        Integer st = t.getStatus();
        if (st != null && (st == TicketStatus.RESOLVED.dbValue() || st == TicketStatus.CLOSED.dbValue())) {
            return "normal";
        }
        Instant due = t.getSlaDueAt();
        if (due == null) {
            return "normal";
        }
        if (Boolean.TRUE.equals(t.getSlaBreached()) || !now.isBefore(due)) {
            return "breach";
        }
        return !now.isBefore(warnAt(t)) ? "warning" : "normal";
    }

    /** SLA 临近阈值时刻 = 截止时间 - 20% SLA 时长。 */
    public Instant warnAt(TicketTicket t) {
        TicketPriority p = TicketPriority.fromDbValue(t.getPriority());
        long warnMinutes = Math.round(p.slaHours() * 60 * 0.2);
        return t.getSlaDueAt().minus(warnMinutes, ChronoUnit.MINUTES);
    }

    public TicketSummary toSummary(TicketTicket t, String tenantName, Instant now) {
        return new TicketSummary(
                t.getId(), t.getTicketNo(), t.getTitle(),
                TicketType.fromDbValue(t.getType()),
                TicketPriority.fromDbValue(t.getPriority()),
                TicketStatus.fromDbValue(t.getStatus()),
                t.getTenantId(), tenantName,
                t.getSubmitterId(), t.getSubmitterName(),
                t.getAssigneeId(), t.getAssigneeName(),
                t.getSlaDueAt(), slaState(t, now),
                t.getLastActivityAt(), instant(t.getCreatedAt()));
    }

    public TicketDetailView toDetail(TicketTicket t, String tenantName, Instant now) {
        List<TicketAttachment> allAtt = attachmentMapper.selectList(
                new LambdaQueryWrapper<TicketAttachment>()
                        .eq(TicketAttachment::getTicketId, t.getId())
                        .orderByAsc(TicketAttachment::getCreatedAt));
        List<TicketMessage> msgs = messageMapper.selectList(
                new LambdaQueryWrapper<TicketMessage>()
                        .eq(TicketMessage::getTicketId, t.getId())
                        .orderByAsc(TicketMessage::getCreatedAt));

        List<TicketMessageView> messageViews = new ArrayList<>(msgs.size());
        List<TicketAttachmentView> ticketLevel = new ArrayList<>();
        for (TicketAttachment a : allAtt) {
            if (a.getMessageId() == null) {
                ticketLevel.add(toAttachmentView(a));
            }
        }
        for (TicketMessage m : msgs) {
            List<TicketAttachmentView> own = new ArrayList<>();
            for (TicketAttachment a : allAtt) {
                if (m.getId().equals(a.getMessageId())) {
                    own.add(toAttachmentView(a));
                }
            }
            messageViews.add(new TicketMessageView(
                    m.getId(), m.getAuthorId(), m.getAuthorName(),
                    TicketAuthorSide.fromDbValue(m.getAuthorSide()),
                    m.getContent(), instant(m.getCreatedAt()), own));
        }
        return new TicketDetailView(
                toSummary(t, tenantName, now),
                t.getResolvedAt(), t.getClosedAt(), t.getCloseReason(),
                messageViews, ticketLevel);
    }

    /** 单条消息视图（不含附件，用于刚追加回复后回显）。 */
    public TicketMessageView toMessageView(TicketMessage m) {
        return new TicketMessageView(m.getId(), m.getAuthorId(), m.getAuthorName(),
                TicketAuthorSide.fromDbValue(m.getAuthorSide()),
                m.getContent(), instant(m.getCreatedAt()), List.of());
    }

    public TicketAttachmentView toAttachmentView(TicketAttachment a) {
        return new TicketAttachmentView(a.getId(), a.getMessageId(), a.getFileId(),
                a.getFileName(), a.getSizeBytes() == null ? 0L : a.getSizeBytes(), instant(a.getCreatedAt()));
    }

    // ===================== 名称解析 =====================

    public String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userQuery.realName(userId);
    }

    /** 全部启用运营账号 id（tenant_id=0）。 */
    public List<Long> opsUserIds() {
        return opsAccounts.activeOpsUserIds();
    }

    // ===================== 通知 =====================

    /** 在指定租户上下文里向若干用户发模板通知（通道/优先级跟随模板）。失败不影响主流程。 */
    public void notify(long ctxTenant, List<Long> userIds, String templateCode,
                       Map<String, Object> vars, long ticketId) {
        MessageService ms = messageServiceProvider.getIfAvailable();
        if (ms == null) {
            return;
        }
        List<Long> targets = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (targets.isEmpty()) {
            return;
        }
        try {
            TenantContext.callAs(ctxTenant, () -> {
                List<RecipientSpec> recipients = targets.stream().map(RecipientSpec::member).toList();
                ms.send(MessageSendRequest.builder(templateCode)
                        .variables(vars)
                        .recipients(recipients)
                        .category(MessageCategory.BUSINESS)
                        .related("Ticket", String.valueOf(ticketId))
                        .link("/tickets/" + ticketId)
                        .build());
                return null;
            });
        } catch (Exception e) {
            log.warn("[ticket] 通知发送失败 template={} ticketId={}：{}", templateCode, ticketId, e.getMessage());
        }
    }

    public void touch(TicketTicket t, Instant now) {
        t.setLastActivityAt(now);
        t.setUpdatedAt(LocalDateTime.now());
    }

    private static Instant instant(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();
    }
}
