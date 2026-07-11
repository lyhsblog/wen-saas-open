package com.wensaas.core.impl.exports;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.compliance.ComplianceException;
import com.wensaas.core.api.compliance.ComplianceService;
import com.wensaas.core.api.compliance.export.ExportObjectRule;
import com.wensaas.core.api.compliance.export.ExportRequestView;
import com.wensaas.core.api.exports.ExportException;
import com.wensaas.core.api.exports.ExportService;
import com.wensaas.core.api.exports.spi.ExportDataProvider;
import com.wensaas.core.api.task.SubmitRequest;
import com.wensaas.core.api.task.TaskService;
import com.wensaas.core.api.task.TaskType;
import com.wensaas.core.impl.permission.eval.PermissionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 业务数据导出服务实现。发起时按合规策略（导出对象审批开关）分流：无需审批直投任务中心，
 * 需审批落 {@code sys_export_request}（执行参数快照随台账走）；审批通过经
 * {@link ExportTaskSubmitter#submitApproved} 以申请人身份投递同一执行任务。
 */
public class ExportServiceImpl implements ExportService, ExportTaskSubmitter {

    private static final Logger log = LoggerFactory.getLogger(ExportServiceImpl.class);

    private final Map<String, ExportDataProvider> providers;
    private final ComplianceService complianceService;
    private final TaskService taskService;
    private final PermissionResolver permissionResolver;

    public ExportServiceImpl(List<ExportDataProvider> providers,
                             ComplianceService complianceService,
                             TaskService taskService,
                             PermissionResolver permissionResolver) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(ExportDataProvider::objectCode, Function.identity(), (a, b) -> a));
        this.complianceService = complianceService;
        this.taskService = taskService;
        this.permissionResolver = permissionResolver;
    }

    @Override
    public List<ExportObjectOption> listObjects() {
        UserPrincipal me = UserContext.require();
        // 一次取全量权限点内存过滤（避免逐 provider 各查一次）；owner 放行全部
        Set<String> myPerms = me.tenantOwner()
                ? null
                : permissionResolver.resolve(me.tenantId(), me.userId()).permissions();
        Map<String, ExportObjectRule> rules = complianceService.getPolicy().objects().stream()
                .collect(Collectors.toMap(ExportObjectRule::code, Function.identity(), (a, b) -> a));
        return providers.values().stream()
                .filter(p -> permitted(myPerms, p.requiredPermission()))
                .map(p -> {
                    ExportObjectRule rule = rules.get(p.objectCode());
                    return new ExportObjectOption(
                            p.objectCode(),
                            rule != null ? rule.name() : p.objectCode(),
                            rule != null ? rule.module() : null,
                            rule == null || rule.requiresApproval());
                })
                .sorted((a, b) -> a.code().compareTo(b.code()))
                .toList();
    }

    @Override
    public SubmitExportResult submit(SubmitExportCommand cmd) {
        UserPrincipal me = UserContext.require();
        ExportDataProvider provider = providers.get(cmd.objectCode());
        if (provider == null) {
            throw new ExportException(ExportException.Code.OBJECT_UNKNOWN,
                    "导出对象未注册：" + cmd.objectCode());
        }
        if (!hasPermission(me, provider.requiredPermission())) {
            throw new ExportException(ExportException.Code.FORBIDDEN,
                    "无权导出该对象：" + cmd.objectCode());
        }

        Map<String, String> filters = cmd.filters() == null ? Map.of() : cmd.filters();
        long estimated = provider.estimateCount(filters);
        boolean requiresApproval = complianceService.getPolicy().objects().stream()
                .filter(r -> r.code().equals(cmd.objectCode()))
                .map(ExportObjectRule::requiresApproval)
                .findFirst()
                .orElse(true); // 未声明对象按需审批兜底（安全侧，与合规服务一致）

        if (requiresApproval) {
            if (cmd.reason() == null || cmd.reason().isBlank()) {
                throw new ExportException(ExportException.Code.REASON_REQUIRED,
                        "该导出对象需审批，请填写申请理由");
            }
            try {
                ExportRequestView view = complianceService.submit(new ComplianceService.SubmitCommand(
                        null, cmd.objectCode(), cmd.filterSummary(),
                        (int) Math.min(estimated, Integer.MAX_VALUE), cmd.reason(), filters));
                log.info("[export] 导出转审批 object={} requestId={} applicant={}",
                        cmd.objectCode(), view.id(), me.userId());
                return new SubmitExportResult(SubmitExportResult.Status.PENDING_APPROVAL, null, view.id());
            } catch (ComplianceException e) {
                if (e.code() != ComplianceException.Code.EXPORT_NOT_REQUIRED) {
                    throw e;
                }
                // 开关并发翻转：合规侧已判定无需审批，落到直接执行
            }
        }

        String objectName = objectName(cmd.objectCode());
        Long taskId = taskService.submit(new SubmitRequest(
                TaskType.EXPORT, "导出" + objectName, ExportTaskHandler.HANDLER_CODE,
                ExportTaskHandler.buildParams(cmd.objectCode(), objectName, filters, estimated,
                        me.userId(), me.username(), me.tenantOwner(), me.mainDeptId())));
        log.info("[export] 导出直接执行 object={} taskId={} operator={}",
                cmd.objectCode(), taskId, me.userId());
        return new SubmitExportResult(SubmitExportResult.Status.SUBMITTED, taskId, null);
    }

    @Override
    public Long submitApproved(ApprovedExport req) {
        String objectName = req.objectName() != null && !req.objectName().isBlank()
                ? req.objectName() : objectName(req.objectCode());
        UserPrincipal applicant = new UserPrincipal(req.applicantId(),
                com.wensaas.core.api.tenant.TenantContext.require(), req.applicantUsername(),
                "export-approved:" + req.requestId(), req.applicantTenantOwner(),
                req.applicantMainDeptId(), false, null);
        AtomicReference<Long> taskId = new AtomicReference<>();
        UserContext.runAs(applicant, () -> taskId.set(taskService.submit(new SubmitRequest(
                TaskType.EXPORT, "导出" + objectName, ExportTaskHandler.HANDLER_CODE,
                ExportTaskHandler.buildParams(req.objectCode(), objectName, req.filters(),
                        req.estimatedCount(), req.applicantId(), req.applicantUsername(),
                        req.applicantTenantOwner(), req.applicantMainDeptId())))));
        log.info("[export] 审批通过投递执行 requestId={} taskId={} applicant={}",
                req.requestId(), taskId.get(), req.applicantId());
        return taskId.get();
    }

    // ===================== 内部 =====================

    private String objectName(String objectCode) {
        return complianceService.getPolicy().objects().stream()
                .filter(r -> r.code().equals(objectCode))
                .map(ExportObjectRule::name)
                .findFirst()
                .orElse(objectCode);
    }

    private boolean hasPermission(UserPrincipal me, String permission) {
        if (permission == null || me.tenantOwner()) {
            return true;
        }
        return permissionResolver.has(me.tenantId(), me.userId(), permission);
    }

    private static boolean permitted(Set<String> myPerms, String required) {
        return myPerms == null || required == null || myPerms.contains(required);
    }
}
