package com.wensaas.core.api.ticket;

/** 沟通记录作者方：区分租户提交方与运营客服，工单详情沟通流按此分侧展示。 */
public enum TicketAuthorSide {

    /** 租户提交方（提交人 / 本租户成员）。 */
    TENANT(0),
    /** 运营客服。 */
    OPS(1);

    private final int dbValue;

    TicketAuthorSide(int dbValue) {
        this.dbValue = dbValue;
    }

    public int dbValue() {
        return dbValue;
    }

    public static TicketAuthorSide fromDbValue(int value) {
        for (TicketAuthorSide s : values()) {
            if (s.dbValue == value) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知作者方 db value: " + value);
    }
}
