package com.wensaas.core.api.compliance;

import java.util.List;
import java.util.Map;
import com.wensaas.core.api.compliance.export.ExportPolicyView;
import com.wensaas.core.api.compliance.export.ExportRequestPage;
import com.wensaas.core.api.compliance.export.ExportRequestStatus;
import com.wensaas.core.api.compliance.export.ExportRequestView;

/**
 * 租户侧数据隐私与合规服务（运行在租户上下文），承接超量导出审批。
 *
 * <p>发起导出申请：任意租户成员；查看 / 审批：主管理员，或（当策略为 {@code ROLE_PERMISSION}）
 * 持 {@code base.compliance.export.approve} 权限的角色；策略读写：主管理员（{@code base.compliance.config}）。
 * 权限由 Web 层 {@code @RequirePermission} 与本服务内的主管理员校验共同守卫。
 *
 * <p>审批触发已由「各导出对象是否需审批」开关驱动（不再按数量阈值评估）：发起导出时按
 * {@code objectCode} 查生效开关，需审批者落 PENDING 并通知审批人；无需审批者业务侧直接执行、
 * 不入台账（本接口对该情形抛 {@code EXPORT_NOT_REQUIRED}）。
 */
public interface ComplianceService {

    /**
     * 发起一次导出申请。按 {@code objectCode} 查该导出对象的生效审批开关：需审批则落 PENDING
     * 并通知审批人；无需审批则抛 {@link ComplianceException.Code#EXPORT_NOT_REQUIRED}（调用方据此直接导出）。
     */
    ExportRequestView submit(SubmitCommand cmd);

    /** 导出申请分页（{@code mineOnly}=true 仅看本人发起的；否则看本租户全部，需审批权限）。 */
    ExportRequestPage listRequests(ExportRequestStatus statusFilter, boolean mineOnly, int page, int size);

    /** 申请详情（本人可见；他人需审批权限）。 */
    ExportRequestView detail(long id);

    /** 批准（仅 PENDING 可批；写审批人 / 意见 / 决策时间；通知申请人）。 */
    void approve(long id, String comment);

    /** 拒绝（仅 PENDING 可拒；通知申请人）。 */
    void reject(long id, String comment);

    /** 当前租户超量导出策略（各对象审批开关 + 审批权限 + 超时提醒；缺失回退声明默认 / MAIN_ADMIN）。 */
    ExportPolicyView getPolicy();

    /** 更新策略（仅主管理员）。 */
    void updatePolicy(UpdatePolicyCommand cmd);

    /** 合规总览指标（超量导出审批维度）。 */
    ComplianceOverview overview();

    /**
     * 发起导出命令（{@code objectCode} 必填，定位审批开关；{@code estimatedCount} 仅作申请信息；
     * {@code filters} 为导出执行参数快照——审批通过后以申请人身份投递执行任务时原样透传，可空）。
     */
    record SubmitCommand(String objectType, String objectCode, String filterSummary,
                         int estimatedCount, String reason, Map<String, String> filters) {
    }

    /** 更新策略命令（各对象审批开关 + 审批权限 + 超时提醒）。 */
    record UpdatePolicyCommand(List<ObjectToggle> objects, String approvalAuthority, Integer timeoutHours) {
    }

    /** 单个导出对象的审批开关设置。 */
    record ObjectToggle(String code, boolean requiresApproval) {
    }
}
