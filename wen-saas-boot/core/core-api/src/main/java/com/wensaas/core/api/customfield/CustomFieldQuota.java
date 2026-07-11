package com.wensaas.core.api.customfield;

/**
 * 租户自定义字段额度对外查询契约。
 *
 * @param tenantId   租户 ID
 * @param fieldQuota 字段总额度（基础 20 + 加购）
 * @param fieldUsed  已用字段数
 * @param indexQuota 索引总额度（基础 5 + 加购）
 * @param indexUsed  已用索引数
 */
public record CustomFieldQuota(Long tenantId,
                                int fieldQuota,
                                int fieldUsed,
                                int indexQuota,
                                int indexUsed) {

    public int fieldRemaining() {
        return fieldQuota - fieldUsed;
    }

    public int indexRemaining() {
        return indexQuota - indexUsed;
    }
}
