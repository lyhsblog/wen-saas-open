package com.wensaas.core.api.compliance.export;

/** 全量数据导出的敏感字段处理方式。 */
public enum DataExportMode {

    /** 敏感字段完整导出（手机 / 邮箱等明文）。 */
    FULL("完整"),
    /** 敏感字段脱敏导出（手机 / 邮箱等掩码）。 */
    MASKED("脱敏");

    private final String label;

    DataExportMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 容错解析；未知 / 空值回退 {@link #MASKED}（更安全的默认）。 */
    public static DataExportMode of(String raw) {
        if (raw == null) {
            return MASKED;
        }
        try {
            return valueOf(raw);
        } catch (IllegalArgumentException e) {
            return MASKED;
        }
    }
}
