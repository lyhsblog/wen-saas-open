package com.wensaas.core.api.ticket;

/** 工单类型：报障 / 咨询 / 建议 / 投诉。 */
public enum TicketType {

    BUG(0, "报障"),
    CONSULT(1, "咨询"),
    SUGGESTION(2, "建议"),
    COMPLAINT(3, "投诉");

    private final int dbValue;
    private final String label;

    TicketType(int dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public int dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static TicketType fromDbValue(int value) {
        for (TicketType t : values()) {
            if (t.dbValue == value) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知工单类型 db value: " + value);
    }
}
