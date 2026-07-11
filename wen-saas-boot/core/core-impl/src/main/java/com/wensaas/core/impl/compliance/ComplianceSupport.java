package com.wensaas.core.impl.compliance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.user.UserQueryService;
import com.wensaas.core.api.compliance.export.DataExportMode;
import com.wensaas.core.api.compliance.export.DataExportStatus;
import com.wensaas.core.api.compliance.export.DataExportView;
import com.wensaas.core.api.compliance.export.ExportRequestStatus;
import com.wensaas.core.api.compliance.export.ExportRequestView;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.wensaas.core.impl.compliance.export.DataExportRequest;
import com.wensaas.core.impl.compliance.export.ExportRequest;
import com.wensaas.core.impl.compliance.export.ExportRequestMapper;

/**
 * 超量导出审批读模型组装 + 名称解析 + 申请编号生成 + 通知发送的共享支撑
 * （租户侧与运营侧 Service 复用）。
 */
@Component
public class ComplianceSupport {

    private static final Logger log = LoggerFactory.getLogger(ComplianceSupport.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final UserQueryService userQuery;
    private final ExportRequestMapper requestMapper;
    private final ObjectProvider<MessageService> messageServiceProvider;

    public ComplianceSupport(UserQueryService userQuery,
                             ExportRequestMapper requestMapper,
                             ObjectProvider<MessageService> messageServiceProvider) {
        this.userQuery = userQuery;
        this.requestMapper = requestMapper;
        this.messageServiceProvider = messageServiceProvider;
    }

    // ===================== 读模型 =====================

    public ExportRequestView toView(ExportRequest r, String tenantName) {
        return new ExportRequestView(
                r.getId(), r.getRequestNo(), r.getTenantId(), tenantName,
                r.getObjectType(), r.getObjectCode(), r.getFilterSummary(),
                r.getEstimatedCount() == null ? 0 : r.getEstimatedCount(),
                r.getReason(), ExportRequestStatus.of(r.getStatus()),
                r.getApplicantId(), r.getApplicantName(), r.getApplicantDeptName(),
                r.getApproverId(), r.getApproverName(), r.getApprovalComment(),
                r.getAppliedAt(), r.getDecidedAt(), r.getTaskId());
    }

    public DataExportView toDataExportView(DataExportRequest r) {
        return new DataExportView(
                r.getId(), r.getRequestNo(), r.getTenantId(), r.getReason(),
                DataExportMode.of(r.getDataMode()), r.getScopeSummary(),
                DataExportStatus.of(r.getStatus()), r.getItemSummary(),
                r.getFileName(), r.getFileSize(), r.getDownloadToken(), r.getExpiresAt(),
                r.getApplicantId() == null ? 0L : r.getApplicantId(), r.getApplicantName(),
                r.getRequestedAt(), r.getCompletedAt(), r.getErrorMessage());
    }

    // ===================== 名称解析 =====================

    public String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userQuery.realName(userId);
    }

    // ===================== 申请编号 =====================

    /**
     * 生成 {@code EX-YYYYMMDD-NNNN}：当日（北京时间）当前租户已有申请数 +1。
     * 调用方处于租户上下文，count 随多租户改写自动按 tenant 收敛；唯一索引兜底并发冲突。
     */
    public String nextRequestNo() {
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDateTime start = today.atStartOfDay();
        long todayCount = requestMapper.selectCount(new LambdaQueryWrapper<ExportRequest>()
                .ge(ExportRequest::getCreatedAt, start));
        return String.format("EX-%s-%04d",
                today.toString().replace("-", ""), todayCount + 1);
    }

    // ===================== 通知（best-effort） =====================

    /** 在指定租户上下文向若干用户发模板通知（通道/优先级跟随模板）。模板缺失 / 失败均不影响主流程。 */
    public void notify(long ctxTenant, List<Long> userIds, String templateCode,
                       Map<String, Object> vars, long requestId) {
        notify(ctxTenant, userIds, templateCode, vars, requestId, "/tasks?tab=approvals");
    }

    /** 同上，允许自定义站内信跳转链接（不同子页 / Tab）。 */
    public void notify(long ctxTenant, List<Long> userIds, String templateCode,
                       Map<String, Object> vars, long requestId, String link) {
        MessageService ms = messageServiceProvider.getIfAvailable();
        if (ms == null) {
            return;
        }
        List<Long> targets = userIds.stream().filter(Objects::nonNull).distinct().toList();
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
                        .related("ExportRequest", String.valueOf(requestId))
                        .link(link)
                        .build());
                return null;
            });
        } catch (Exception e) {
            log.warn("[compliance] 通知发送失败 template={} requestId={}：{}", templateCode, requestId, e.getMessage());
        }
    }
}
