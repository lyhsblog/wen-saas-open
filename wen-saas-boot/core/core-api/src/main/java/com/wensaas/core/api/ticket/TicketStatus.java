package com.wensaas.core.api.ticket;

/**
 * 工单状态。
 *
 * <pre>
 * 待处理 → 处理中 → 已解决 → 已关闭
 *                      ↓
 *           用户确认 / 7 天未回应自动 → 已关闭
 *           用户重启（7 天内）        → 处理中
 * </pre>
 */
public enum TicketStatus {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    RESOLVED(2, "已解决"),
    CLOSED(3, "已关闭");

    private final int dbValue;
    private final String label;

    TicketStatus(int dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public int dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static TicketStatus fromDbValue(int value) {
        for (TicketStatus s : values()) {
            if (s.dbValue == value) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知工单状态 db value: " + value);
    }
}
