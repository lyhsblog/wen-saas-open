package com.wensaas.core.api.ticket;

/**
 * 工单优先级。
 *
 * <p>每档对应固定 SLA 时长（产品既定）：紧急 24h / 重要 48h / 普通 72h / 低 7 天。
 */
public enum TicketPriority {

    LOW(0, "低", 7 * 24),
    NORMAL(1, "普通", 72),
    HIGH(2, "重要", 48),
    URGENT(3, "紧急", 24);

    private final int dbValue;
    private final String label;
    private final int slaHours;

    TicketPriority(int dbValue, String label, int slaHours) {
        this.dbValue = dbValue;
        this.label = label;
        this.slaHours = slaHours;
    }

    public int dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    /** SLA 时长（小时）。 */
    public int slaHours() {
        return slaHours;
    }

    public static TicketPriority fromDbValue(int value) {
        for (TicketPriority p : values()) {
            if (p.dbValue == value) {
                return p;
            }
        }
        throw new IllegalArgumentException("未知工单优先级 db value: " + value);
    }
}
