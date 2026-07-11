package com.wensaas.core.api.compliance.export;

/**
 * 超量导出审批状态。
 *
 * <p>以字符串形态落库 {@code sys_export_request.status}，对前端按 REST 契约暴露字符串枚举。
 */
public enum ExportRequestStatus {

    /** 审批中（等待审批人决策）。 */
    PENDING("审批中"),
    /** 已批准（进入导出执行 / 下载，本切片只落审批结论）。 */
    APPROVED("已批准"),
    /** 已拒绝。 */
    REJECTED("已拒绝");

    private final String label;

    ExportRequestStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ExportRequestStatus of(String raw) {
        return raw == null ? null : ExportRequestStatus.valueOf(raw);
    }
}
