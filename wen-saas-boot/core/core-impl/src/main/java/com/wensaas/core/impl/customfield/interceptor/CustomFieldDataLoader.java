package com.wensaas.core.impl.customfield.interceptor;

import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldQuota;
import com.wensaas.core.api.customfield.CustomFieldType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.wensaas.core.impl.customfield.def.SysCustomFieldDef;
import com.wensaas.core.impl.customfield.def.SysCustomFieldDefMapper;
import com.wensaas.core.impl.customfield.quota.CustomFieldQuotaManager;
import com.wensaas.core.impl.customfield.quota.SysCustomFieldQuota;
import com.wensaas.core.impl.customfield.quota.SysCustomFieldQuotaMapper;

/**
 * 自定义字段装载器。给定 {@code (tenantId, objectCode)} 装载未软删字段列表；
 * 给定 {@code tenantId} 装载额度（不存在返 {@link Optional#empty()}，由 {@link CustomFieldQuotaManager} lazy init）。
 *
 * <p>查询走 MP TenantLine 自动注入 {@code tenant_id} 过滤（自定义字段是租户级数据，无平台预置层）。
 * 这与字典框架不同 —— 字典需合并 {@code tenant_id IN (0, X)}，自定义字段无此需求。
 */
public class CustomFieldDataLoader {

    private final SysCustomFieldDefMapper defMapper;
    private final SysCustomFieldQuotaMapper quotaMapper;

    public CustomFieldDataLoader(SysCustomFieldDefMapper defMapper,
                                   SysCustomFieldQuotaMapper quotaMapper) {
        this.defMapper = defMapper;
        this.quotaMapper = quotaMapper;
    }

    /** 列出某 (租户, 对象) 下未软删字段。 */
    public List<CustomFieldDef> loadDefs(long tenantId, String objectCode) {
        List<SysCustomFieldDef> rows = defMapper.listByTenantAndObject(tenantId, objectCode);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<CustomFieldDef> out = new ArrayList<>(rows.size());
        for (SysCustomFieldDef r : rows) {
            out.add(toApi(r));
        }
        return out;
    }

    /** 取当前租户的额度行（不存在返 empty；调用方负责 init）。 */
    public Optional<CustomFieldQuota> loadQuota(long tenantId) {
        SysCustomFieldQuota row = quotaMapper.selectById(tenantId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toApi(row));
    }

    public static CustomFieldDef toApi(SysCustomFieldDef r) {
        return new CustomFieldDef(
                r.getId(),
                r.getTenantId(),
                r.getObjectCode(),
                r.getFieldCode(),
                r.getFieldName(),
                CustomFieldType.fromCode(r.getFieldType()),
                Boolean.TRUE.equals(r.getIsRequired()),
                Boolean.TRUE.equals(r.getIsIndexed()),
                r.getDefaultValue(),
                r.getRefDictCode(),
                r.getRefObjectCode(),
                r.getDisplayOrder() == null ? 0 : r.getDisplayOrder());
    }

    public static CustomFieldQuota toApi(SysCustomFieldQuota r) {
        return new CustomFieldQuota(
                r.getTenantId(),
                r.getFieldQuota() == null ? 0 : r.getFieldQuota(),
                r.getFieldUsed() == null ? 0 : r.getFieldUsed(),
                r.getIndexQuota() == null ? 0 : r.getIndexQuota(),
                r.getIndexUsed() == null ? 0 : r.getIndexUsed());
    }
}
