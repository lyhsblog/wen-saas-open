package com.wensaas.core.impl.exports;

import java.util.Map;

/**
 * 审批通过 → 导出执行任务投递的衔接口（合规模块调用，避免直接依赖导出框架实现细节）。
 *
 * <p>实现方（{@code ExportServiceImpl}）以<b>申请人</b>身份（{@code UserContext.runAs}）调
 * {@code TaskService.submit}，使 {@code sys_task.creator_id} = 申请人——任务中心按 creator
 * 隔离，申请人在「导入导出」页可见可下载。
 */
public interface ExportTaskSubmitter {

    /**
     * 投递一条已批准的导出执行任务。
     *
     * @return 任务中心任务 ID（回写台账 {@code task_id}）
     */
    Long submitApproved(ApprovedExport req);

    /**
     * 已批准导出的执行要素（来自 {@code sys_export_request.execute_params} 快照）。
     *
     * @param applicantMainDeptId 申请人主部门（可空）；与 tenantOwner 一起还原数据权限语境
     */
    record ApprovedExport(long requestId, String requestNo, String objectCode, String objectName,
                          Map<String, String> filters, int estimatedCount,
                          long applicantId, String applicantUsername,
                          boolean applicantTenantOwner, Long applicantMainDeptId) {
    }
}
