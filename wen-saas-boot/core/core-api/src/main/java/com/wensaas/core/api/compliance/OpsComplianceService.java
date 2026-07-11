package com.wensaas.core.api.compliance;

import com.wensaas.core.api.compliance.export.ExportRequestPage;
import com.wensaas.core.api.compliance.export.ExportRequestStatus;
import com.wensaas.core.api.compliance.export.ExportRequestView;
/**
 * 运营侧数据隐私与合规审计服务（运营跨租户只读）。
 *
 * <p>运营对超量导出审批做合规监控：跨租户查看各租户的导出申请与审批结论；按运营
 * dataScope（OPS_OWNED 仅看自己负责的租户）收敛。本切片只读，不代租户审批。
 */
public interface OpsComplianceService {

    /** 跨租户导出审批审计分页。 */
    ExportRequestPage list(OpsExportQuery query);

    /** 跨租户导出申请详情。 */
    ExportRequestView detail(long id);

    /**
     * 运营审计查询条件。
     *
     * @param tenantId 限定租户（可空）
     * @param status   限定状态（可空）
     * @param keyword  申请编号 / 导出对象关键字（可空）
     */
    record OpsExportQuery(Long tenantId, ExportRequestStatus status, String keyword, int page, int size) {
    }
}
