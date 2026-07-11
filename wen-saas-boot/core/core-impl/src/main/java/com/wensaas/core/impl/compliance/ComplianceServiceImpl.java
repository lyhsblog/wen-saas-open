package com.wensaas.core.impl.compliance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.compliance.ComplianceException;
import com.wensaas.core.api.compliance.ComplianceOverview;
import com.wensaas.core.api.compliance.ComplianceService;
import com.wensaas.core.api.compliance.export.ExportObjectRule;
import com.wensaas.core.api.compliance.export.ExportPolicyView;
import com.wensaas.core.api.compliance.export.ExportRequestPage;
import com.wensaas.core.api.compliance.export.ExportRequestStatus;
import com.wensaas.core.api.compliance.export.ExportRequestView;
import com.wensaas.core.api.compliance.spi.ExportObjectDef;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;
import com.wensaas.core.impl.exports.ExportTaskSubmitter;
import com.wensaas.core.impl.permission.eval.PermissionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.wensaas.core.impl.compliance.export.ExportApprovalRule;
import com.wensaas.core.impl.compliance.export.ExportApprovalRuleMapper;
import com.wensaas.core.impl.compliance.export.ExportObjectRegistry;
import com.wensaas.core.impl.compliance.export.ExportPolicy;
import com.wensaas.core.impl.compliance.export.ExportPolicyMapper;
import com.wensaas.core.impl.compliance.export.ExportRequest;
import com.wensaas.core.impl.compliance.export.ExportRequestMapper;

/**
 * 租户侧数据隐私与合规服务实现（运行在租户上下文）。
 *
 * <p>审批触发由「各导出对象是否需审批」开关驱动（不再按数量阈值评估）：导出对象由
 * {@link ExportObjectRegistry} 聚合业务声明 + 内置项，开关默认值取声明 {@code defaultRequiresApproval}，
 * 主管理员可按租户落 {@code sys_export_approval_rule} 覆盖。
 */
public class ComplianceServiceImpl implements ComplianceService {

    static final String AUTH_MAIN_ADMIN = "MAIN_ADMIN";
    static final String AUTH_ROLE_PERMISSION = "ROLE_PERMISSION";
    private static final String APPROVE_PERMISSION = "base.compliance.export.approve";
    private static final Logger log = LoggerFactory.getLogger(ComplianceServiceImpl.class);

    private final ExportRequestMapper requestMapper;
    private final ExportPolicyMapper policyMapper;
    private final ExportApprovalRuleMapper ruleMapper;
    private final ExportObjectRegistry objectRegistry;
    private final ComplianceSupport support;
    private final TenantQueryService tenantQuery;
    private final PermissionResolver permissionResolver;
    private final ObjectProvider<ExportTaskSubmitter> exportTaskSubmitterProvider;

    public ComplianceServiceImpl(ExportRequestMapper requestMapper,
                                 ExportPolicyMapper policyMapper,
                                 ExportApprovalRuleMapper ruleMapper,
                                 ExportObjectRegistry objectRegistry,
                                 ComplianceSupport support,
                                 TenantQueryService tenantQuery,
                                 PermissionResolver permissionResolver,
                                 ObjectProvider<ExportTaskSubmitter> exportTaskSubmitterProvider) {
        this.requestMapper = requestMapper;
        this.policyMapper = policyMapper;
        this.ruleMapper = ruleMapper;
        this.objectRegistry = objectRegistry;
        this.support = support;
        this.tenantQuery = tenantQuery;
        this.permissionResolver = permissionResolver;
        this.exportTaskSubmitterProvider = exportTaskSubmitterProvider;
    }

    @Override
    @Transactional
    public ExportRequestView submit(SubmitCommand cmd) {
        long tenantId = TenantContext.require();
        UserPrincipal me = UserContext.require();

        ExportObjectDef def = objectRegistry.find(cmd.objectCode());
        if (!effectiveRequiresApproval(def, cmd.objectCode())) {
            throw new ComplianceException(ComplianceException.Code.EXPORT_NOT_REQUIRED,
                    "该导出对象无需审批，可直接导出");
        }

        LocalDateTime nowLdt = LocalDateTime.now();
        Instant now = Instant.now();

        ExportRequest r = new ExportRequest();
        r.setTenantId(tenantId);
        r.setRequestNo(support.nextRequestNo());
        r.setObjectType(def != null ? def.name() : cmd.objectType());
        r.setObjectCode(cmd.objectCode());
        r.setFilterSummary(cmd.filterSummary());
        r.setEstimatedCount(Math.max(0, cmd.estimatedCount()));
        r.setReason(cmd.reason());
        r.setStatus(ExportRequestStatus.PENDING.name());
        r.setApplicantId(me.userId());
        r.setApplicantName(support.userName(me.userId()));
        r.setAppliedAt(now);
        r.setExecuteParams(buildExecuteParams(cmd, me));
        r.setCreatedAt(nowLdt);
        r.setCreatedBy(me.userId());
        r.setUpdatedAt(nowLdt);
        requestMapper.insert(r);

        notifyApprovers(tenantId, r);
        return support.toView(r, null);
    }

    @Override
    public ExportRequestPage listRequests(ExportRequestStatus statusFilter, boolean mineOnly, int page, int size) {
        UserPrincipal me = UserContext.require();
        int p = page <= 0 ? 1 : page;
        int s = size <= 0 ? 20 : Math.min(size, 200);
        LambdaQueryWrapper<ExportRequest> w = new LambdaQueryWrapper<ExportRequest>()
                .eq(mineOnly, ExportRequest::getApplicantId, me.userId())
                .eq(statusFilter != null, ExportRequest::getStatus, statusFilter == null ? null : statusFilter.name())
                .orderByDesc(ExportRequest::getAppliedAt);
        Page<ExportRequest> result = requestMapper.selectPage(new Page<>(p, s), w);
        List<ExportRequestView> items = result.getRecords().stream().map(r -> support.toView(r, null)).toList();
        return new ExportRequestPage(items, result.getTotal(), p, s);
    }

    @Override
    public ExportRequestView detail(long id) {
        ExportRequest r = requireRequest(id);
        UserPrincipal me = UserContext.require();
        if (!r.getApplicantId().equals(me.userId()) && !canApprove(me)) {
            throw new ComplianceException(ComplianceException.Code.FORBIDDEN, "无权查看该导出申请");
        }
        return support.toView(r, null);
    }

    @Override
    @Transactional
    public void approve(long id, String comment) {
        decide(id, comment, true);
    }

    @Override
    @Transactional
    public void reject(long id, String comment) {
        decide(id, comment, false);
    }

    @Override
    public ExportPolicyView getPolicy() {
        ExportPolicy p = loadPolicy();
        return new ExportPolicyView(
                effectiveRules(),
                p.getApprovalAuthority() == null ? AUTH_MAIN_ADMIN : p.getApprovalAuthority(),
                p.getTimeoutHours());
    }

    @Override
    @Transactional
    public void updatePolicy(UpdatePolicyCommand cmd) {
        long tenantId = TenantContext.require();
        long operatorId = UserContext.require().userId();
        String authority = AUTH_ROLE_PERMISSION.equals(cmd.approvalAuthority())
                ? AUTH_ROLE_PERMISSION : AUTH_MAIN_ADMIN;

        ExportPolicy existing = policyMapper.selectOne(new LambdaQueryWrapper<>());
        if (existing == null) {
            ExportPolicy p = new ExportPolicy();
            p.setTenantId(tenantId);
            p.setApprovalAuthority(authority);
            p.setTimeoutHours(cmd.timeoutHours());
            p.setUpdatedAt(LocalDateTime.now());
            p.setUpdatedBy(operatorId);
            policyMapper.insert(p);
        } else {
            existing.setApprovalAuthority(authority);
            existing.setTimeoutHours(cmd.timeoutHours());
            existing.setUpdatedAt(LocalDateTime.now());
            existing.setUpdatedBy(operatorId);
            policyMapper.updateById(existing);
        }

        if (cmd.objects() != null) {
            for (ObjectToggle toggle : cmd.objects()) {
                if (toggle == null || objectRegistry.find(toggle.code()) == null) {
                    continue; // 仅接受已注册对象，忽略未知 code
                }
                upsertRule(tenantId, operatorId, toggle.code(), toggle.requiresApproval());
            }
        }
    }

    @Override
    public ComplianceOverview overview() {
        ExportPolicy policy = loadPolicy();
        Instant since30d = Instant.now().minus(30, ChronoUnit.DAYS);
        long pending = requestMapper.selectCount(new LambdaQueryWrapper<ExportRequest>()
                .eq(ExportRequest::getStatus, ExportRequestStatus.PENDING.name()));
        long approved30d = requestMapper.selectCount(new LambdaQueryWrapper<ExportRequest>()
                .eq(ExportRequest::getStatus, ExportRequestStatus.APPROVED.name())
                .ge(ExportRequest::getDecidedAt, since30d));
        long rejected30d = requestMapper.selectCount(new LambdaQueryWrapper<ExportRequest>()
                .eq(ExportRequest::getStatus, ExportRequestStatus.REJECTED.name())
                .ge(ExportRequest::getDecidedAt, since30d));
        long overtime = 0;
        if (policy.getTimeoutHours() != null) {
            Instant cutoff = Instant.now().minus(policy.getTimeoutHours(), ChronoUnit.HOURS);
            overtime = requestMapper.selectCount(new LambdaQueryWrapper<ExportRequest>()
                    .eq(ExportRequest::getStatus, ExportRequestStatus.PENDING.name())
                    .lt(ExportRequest::getAppliedAt, cutoff));
        }
        int approvalRequired = (int) effectiveRules().stream().filter(ExportObjectRule::requiresApproval).count();
        return new ComplianceOverview(pending, overtime, approved30d, rejected30d, approvalRequired,
                policy.getApprovalAuthority() == null ? AUTH_MAIN_ADMIN : policy.getApprovalAuthority(),
                policy.getTimeoutHours());
    }

    // ===================== 内部 =====================

    private void decide(long id, String comment, boolean approved) {
        ExportRequest r = requireRequest(id);
        UserPrincipal me = UserContext.require();
        if (!canApprove(me)) {
            throw new ComplianceException(ComplianceException.Code.FORBIDDEN, "无权审批导出申请");
        }
        if (!ExportRequestStatus.PENDING.name().equals(r.getStatus())) {
            throw new ComplianceException(ComplianceException.Code.ILLEGAL_STATE, "该申请已被处理，不能重复审批");
        }
        Instant now = Instant.now();
        r.setStatus((approved ? ExportRequestStatus.APPROVED : ExportRequestStatus.REJECTED).name());
        r.setApproverId(me.userId());
        r.setApproverName(support.userName(me.userId()));
        r.setApprovalComment(comment);
        r.setDecidedAt(now);
        r.setUpdatedAt(LocalDateTime.now());
        r.setUpdatedBy(me.userId());
        if (approved) {
            dispatchExecution(r);
        }
        requestMapper.updateById(r);

        support.notify(r.getTenantId(), List.of(r.getApplicantId()),
                approved ? "base.compliance.export.approved" : "base.compliance.export.rejected",
                Map.of("request_no", r.getRequestNo(),
                        "object_type", r.getObjectType() == null ? "" : r.getObjectType()),
                r.getId(),
                approved ? "/tasks" : "/tasks?tab=approvals");
    }

    /** 申请时落执行参数快照（filters + 申请人数据权限语境），审批通过后据此以申请人身份投递执行任务。 */
    private static Map<String, Object> buildExecuteParams(SubmitCommand cmd, UserPrincipal me) {
        Map<String, Object> operator = new HashMap<>();
        operator.put("userId", me.userId());
        operator.put("username", me.username());
        operator.put("tenantOwner", me.tenantOwner());
        if (me.mainDeptId() != null) {
            operator.put("mainDeptId", me.mainDeptId());
        }
        Map<String, Object> params = new HashMap<>();
        params.put("filters", cmd.filters() == null ? Map.of() : cmd.filters());
        params.put("operator", operator);
        return params;
    }

    /**
     * 审批通过 → 以申请人身份投递导出执行任务。导出框架未装配
     * （task 关闭的测试上下文）或无执行快照（历史数据 / 直发台账端点）时安静跳过，仅记日志；
     * 任务并发冲突（同租户已有导出在跑）转 {@code ILLEGAL_STATE} 提示稍后再批。
     */
    private void dispatchExecution(ExportRequest r) {
        ExportTaskSubmitter submitter = exportTaskSubmitterProvider.getIfAvailable();
        if (submitter == null) {
            log.info("[compliance] 导出执行框架未装配，跳过任务投递 requestId={}", r.getId());
            return;
        }
        Map<String, Object> params = r.getExecuteParams();
        if (params == null || !(params.get("operator") instanceof Map<?, ?> op)) {
            log.warn("[compliance] 申请缺执行快照，跳过任务投递 requestId={}", r.getId());
            return;
        }
        Map<String, String> filters = new HashMap<>();
        if (params.get("filters") instanceof Map<?, ?> f) {
            f.forEach((k, v) -> {
                if (k != null && v != null) {
                    filters.put(String.valueOf(k), String.valueOf(v));
                }
            });
        }
        try {
            Long taskId = submitter.submitApproved(new ExportTaskSubmitter.ApprovedExport(
                    r.getId(), r.getRequestNo(), r.getObjectCode(), r.getObjectType(), filters,
                    r.getEstimatedCount() == null ? 0 : r.getEstimatedCount(),
                    Long.parseLong(String.valueOf(op.get("userId"))),
                    String.valueOf(op.get("username")),
                    Boolean.parseBoolean(String.valueOf(op.get("tenantOwner"))),
                    op.get("mainDeptId") == null ? null : Long.valueOf(String.valueOf(op.get("mainDeptId")))));
            r.setTaskId(taskId);
        } catch (RuntimeException e) {
            throw new ComplianceException(ComplianceException.Code.ILLEGAL_STATE,
                    "导出任务投递失败（可能已有导出任务执行中），请稍后再批：" + e.getMessage());
        }
    }

    /** 当前用户能否审批：主管理员恒可；策略为 ROLE_PERMISSION 时持权限点的角色亦可。 */
    private boolean canApprove(UserPrincipal me) {
        if (me.tenantOwner()) {
            return true;
        }
        ExportPolicy policy = loadPolicy();
        if (!AUTH_ROLE_PERMISSION.equals(policy.getApprovalAuthority())) {
            return false;
        }
        return permissionResolver.has(me.tenantId(), me.userId(), APPROVE_PERMISSION);
    }

    private void notifyApprovers(long tenantId, ExportRequest r) {
        TenantInfo tenant = tenantQuery.tenantById(tenantId);
        if (tenant == null || tenant.mainAdminUserId() == null) {
            return;
        }
        support.notify(tenantId, List.of(tenant.mainAdminUserId()), "base.compliance.export.pending_approval",
                Map.of("request_no", r.getRequestNo(),
                        "object_type", r.getObjectType() == null ? "" : r.getObjectType(),
                        "applicant_name", r.getApplicantName() == null ? "" : r.getApplicantName(),
                        "estimated_count", String.valueOf(r.getEstimatedCount())),
                r.getId());
    }

    /** 合并业务声明的导出对象 + 当前租户开关覆盖，返回各对象生效的审批开关。 */
    private List<ExportObjectRule> effectiveRules() {
        Map<String, Boolean> overrides = new HashMap<>();
        for (ExportApprovalRule rule : ruleMapper.selectList(new LambdaQueryWrapper<>())) {
            if (rule.getObjectCode() != null && rule.getRequiresApproval() != null) {
                overrides.put(rule.getObjectCode(), rule.getRequiresApproval());
            }
        }
        return objectRegistry.all().stream()
                .map(def -> new ExportObjectRule(def.code(), def.name(), def.module(),
                        overrides.getOrDefault(def.code(), def.defaultRequiresApproval())))
                .toList();
    }

    /** 解析某导出对象的生效审批开关；未注册对象按需审批兜底（安全侧）。 */
    private boolean effectiveRequiresApproval(ExportObjectDef def, String objectCode) {
        ExportApprovalRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<ExportApprovalRule>()
                .eq(ExportApprovalRule::getObjectCode, objectCode));
        if (rule != null && rule.getRequiresApproval() != null) {
            return rule.getRequiresApproval();
        }
        return def == null || def.defaultRequiresApproval();
    }

    private void upsertRule(long tenantId, long operatorId, String objectCode, boolean requiresApproval) {
        ExportApprovalRule existing = ruleMapper.selectOne(new LambdaQueryWrapper<ExportApprovalRule>()
                .eq(ExportApprovalRule::getObjectCode, objectCode));
        if (existing == null) {
            ExportApprovalRule rule = new ExportApprovalRule();
            rule.setTenantId(tenantId);
            rule.setObjectCode(objectCode);
            rule.setRequiresApproval(requiresApproval);
            rule.setUpdatedAt(LocalDateTime.now());
            rule.setUpdatedBy(operatorId);
            ruleMapper.insert(rule);
        } else {
            existing.setRequiresApproval(requiresApproval);
            existing.setUpdatedAt(LocalDateTime.now());
            existing.setUpdatedBy(operatorId);
            ruleMapper.updateById(existing);
        }
    }

    /** 加载当前租户策略；缺失返回带默认值的瞬态对象（不落库）。 */
    private ExportPolicy loadPolicy() {
        ExportPolicy p = policyMapper.selectOne(new LambdaQueryWrapper<>());
        if (p != null) {
            return p;
        }
        ExportPolicy def = new ExportPolicy();
        def.setApprovalAuthority(AUTH_MAIN_ADMIN);
        return def;
    }

    private ExportRequest requireRequest(long id) {
        ExportRequest r = requestMapper.selectById(id);
        if (r == null) {
            throw new ComplianceException(ComplianceException.Code.REQUEST_NOT_FOUND, "导出申请不存在：" + id);
        }
        return r;
    }
}
