package com.wensaas.core.impl.compliance.export;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.compliance.ComplianceException;
import com.wensaas.core.api.compliance.export.DataExportMode;
import com.wensaas.core.api.compliance.export.DataExportPage;
import com.wensaas.core.api.compliance.export.DataExportService;
import com.wensaas.core.api.compliance.export.DataExportStatus;
import com.wensaas.core.api.compliance.export.DataExportView;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.task.SubmitRequest;
import com.wensaas.core.api.task.TaskService;
import com.wensaas.core.api.task.TaskType;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.compliance.export.DataExportTaskHandler;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import com.wensaas.core.impl.compliance.ComplianceSupport;

/**
 * 客户全量数据导出（GDPR-style）服务实现（租户上下文）。
 *
 * <p>治理规则在 {@link #submit} 集中校验：仅主管理员（tenantOwner）、每租户月限 1 次、必填理由
 * （Web 层 Bean Validation 兜底）。校验通过后落 PACKAGING 台账并投递任务中心异步打包任务
 * （{@link DataExportTaskHandler}）；真实 zip 归集 / 文件落库 / 下载令牌 / 完成通知由该 handler 完成。
 *
 * <p>非事务：申请行须先提交可见，任务消费者（异步线程）方能读到；任务投递在行提交之后。
 */
public class DataExportServiceImpl implements DataExportService {

    /** 固定导出范围（组织 / 成员 / 字典 / 文件 / 操作日志 / 业务数据）。 */
    private static final String SCOPE_SUMMARY = "组织架构与成员、字典、文件中心、操作日志、业务模块数据";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final DataExportRequestMapper requestMapper;
    private final ComplianceSupport support;
    private final TaskService taskService;
    private final FileService fileService;

    public DataExportServiceImpl(DataExportRequestMapper requestMapper,
                                 ComplianceSupport support,
                                 TaskService taskService,
                                 FileService fileService) {
        this.requestMapper = requestMapper;
        this.support = support;
        this.taskService = taskService;
        this.fileService = fileService;
    }

    @Override
    public DataExportView submit(SubmitCommand cmd) {
        long tenantId = TenantContext.require();
        UserPrincipal me = UserContext.require();
        if (!me.tenantOwner()) {
            throw new ComplianceException(ComplianceException.Code.FORBIDDEN, "仅主管理员可发起全量数据导出");
        }
        if (hasThisMonth()) {
            throw new ComplianceException(ComplianceException.Code.ILLEGAL_STATE,
                    "本月已发起过全量数据导出，每月限 1 次");
        }
        DataExportMode mode = cmd.dataMode() == null ? DataExportMode.MASKED : cmd.dataMode();
        Instant now = Instant.now();
        LocalDateTime nowLdt = LocalDateTime.now();

        DataExportRequest r = new DataExportRequest();
        r.setTenantId(tenantId);
        r.setRequestNo(nextRequestNo());
        r.setReason(cmd.reason());
        r.setDataMode(mode.name());
        r.setScopeSummary(SCOPE_SUMMARY);
        r.setStatus(DataExportStatus.PACKAGING.name());
        r.setApplicantId(me.userId());
        r.setApplicantName(support.userName(me.userId()));
        r.setRequestedAt(now);
        r.setCreatedAt(nowLdt);
        r.setCreatedBy(me.userId());
        r.setUpdatedAt(nowLdt);
        requestMapper.insert(r);

        // 投递异步打包任务（在申请行提交后，消费者方能读到）。
        Long taskId = taskService.submit(new SubmitRequest(
                TaskType.EXPORT,
                "全量数据导出 " + r.getRequestNo(),
                DataExportTaskHandler.HANDLER_CODE,
                Map.of("requestId", String.valueOf(r.getId()))));
        r.setTaskId(taskId);
        requestMapper.updateById(r);

        return support.toDataExportView(r);
    }

    @Override
    public DataExportPage list(int page, int size) {
        int p = page <= 0 ? 1 : page;
        int s = size <= 0 ? 20 : Math.min(size, 200);
        Page<DataExportRequest> result = requestMapper.selectPage(new Page<>(p, s),
                new LambdaQueryWrapper<DataExportRequest>().orderByDesc(DataExportRequest::getRequestedAt));
        List<DataExportView> items = result.getRecords().stream().map(support::toDataExportView).toList();
        return new DataExportPage(items, result.getTotal(), p, s);
    }

    @Override
    public DataExportView detail(long id) {
        DataExportRequest r = requestMapper.selectById(id);
        if (r == null) {
            throw new ComplianceException(ComplianceException.Code.REQUEST_NOT_FOUND, "全量导出申请不存在：" + id);
        }
        UserPrincipal me = UserContext.require();
        if (!me.tenantOwner() && !r.getApplicantId().equals(me.userId())) {
            throw new ComplianceException(ComplianceException.Code.FORBIDDEN, "无权查看该全量导出申请");
        }
        return support.toDataExportView(r);
    }

    @Override
    public String downloadUrl(long id) {
        DataExportRequest r = requestMapper.selectById(id);
        if (r == null) {
            throw new ComplianceException(ComplianceException.Code.REQUEST_NOT_FOUND, "全量导出申请不存在：" + id);
        }
        UserPrincipal me = UserContext.require();
        if (!me.tenantOwner() && !r.getApplicantId().equals(me.userId())) {
            throw new ComplianceException(ComplianceException.Code.FORBIDDEN, "无权下载该全量导出包");
        }
        if (!DataExportStatus.COMPLETED.name().equals(r.getStatus()) || r.getFileId() == null) {
            throw new ComplianceException(ComplianceException.Code.ILLEGAL_STATE, "导出尚未完成，暂不可下载");
        }
        if (r.getExpiresAt() != null && r.getExpiresAt().isBefore(Instant.now())) {
            throw new ComplianceException(ComplianceException.Code.ILLEGAL_STATE,
                    "下载链接已过期（完成后 7 天有效），请重新发起");
        }
        return fileService.signedDownloadUrl(r.getFileId(), Duration.ofMinutes(5));
    }

    // ===================== 内部 =====================

    /** 当前租户本月（北京时间）是否已有未失败的全量导出申请。 */
    private boolean hasThisMonth() {
        Instant monthStart = LocalDate.now(SHANGHAI).withDayOfMonth(1).atStartOfDay(SHANGHAI).toInstant();
        return requestMapper.selectCount(new LambdaQueryWrapper<DataExportRequest>()
                .ge(DataExportRequest::getRequestedAt, monthStart)
                .ne(DataExportRequest::getStatus, DataExportStatus.FAILED.name())) > 0;
    }

    /** 生成 {@code DX-YYYYMMDD-NNNN}：当日（北京时间）当前租户已有申请数 +1。 */
    private String nextRequestNo() {
        LocalDate today = LocalDate.now(SHANGHAI);
        long todayCount = requestMapper.selectCount(new LambdaQueryWrapper<DataExportRequest>()
                .ge(DataExportRequest::getCreatedAt, today.atStartOfDay()));
        return String.format("DX-%s-%04d", today.toString().replace("-", ""), todayCount + 1);
    }
}
