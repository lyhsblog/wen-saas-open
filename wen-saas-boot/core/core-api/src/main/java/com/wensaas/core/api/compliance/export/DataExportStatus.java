package com.wensaas.core.api.compliance.export;

/** 全量数据导出状态。 */
public enum DataExportStatus {

    /** 打包中。 */
    PACKAGING("打包中"),
    /** 已完成（可下载）。 */
    COMPLETED("已完成"),
    /** 打包失败。 */
    FAILED("失败");

    private final String label;

    DataExportStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 容错解析；未知 / 空值回退 {@link #PACKAGING}。 */
    public static DataExportStatus of(String raw) {
        if (raw == null) {
            return PACKAGING;
        }
        try {
            return valueOf(raw);
        } catch (IllegalArgumentException e) {
            return PACKAGING;
        }
    }
}
