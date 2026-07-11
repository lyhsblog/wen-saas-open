package com.wensaas.core.impl.customfield.quota;

import com.wensaas.core.api.customfield.CustomFieldException;
import com.wensaas.core.api.customfield.CustomFieldQuota;

import java.time.LocalDateTime;
import com.wensaas.core.impl.customfield.CustomFieldProperties;
import com.wensaas.core.impl.customfield.interceptor.CustomFieldDataLoader;

/**
 * 自定义字段额度管理。负责惰性 init + 原子增减。
 *
 * <p><b>并发安全</b>：tryInc 走 atomic {@code UPDATE … SET used = used + 1 WHERE used &lt; quota}，
 * 通过 affectedRows 判断是否超额。dec 同理。
 */
public class CustomFieldQuotaManager {

    private final SysCustomFieldQuotaMapper mapper;
    private final CustomFieldProperties props;

    public CustomFieldQuotaManager(SysCustomFieldQuotaMapper mapper, CustomFieldProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    /**
     * 取或惰性 init 某租户的额度行。
     * <p>不存在时按 {@link CustomFieldProperties#getDefaultFieldQuota()} / {@code DefaultIndexQuota} 写入。
     */
    public CustomFieldQuota getOrInit(long tenantId) {
        SysCustomFieldQuota row = mapper.selectById(tenantId);
        if (row != null) {
            return CustomFieldDataLoader.toApi(row);
        }
        SysCustomFieldQuota init = new SysCustomFieldQuota();
        init.setTenantId(tenantId);
        init.setFieldQuota(props.getDefaultFieldQuota());
        init.setFieldUsed(0);
        init.setIndexQuota(props.getDefaultIndexQuota());
        init.setIndexUsed(0);
        init.setUpdatedAt(LocalDateTime.now());
        try {
            mapper.insert(init);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // 并发首次 init，被另一线程抢先写入；重新读取即可
            row = mapper.selectById(tenantId);
            if (row != null) {
                return CustomFieldDataLoader.toApi(row);
            }
            throw dup;
        }
        return CustomFieldDataLoader.toApi(init);
    }

    /** 字段 used++ ；超额抛 {@link CustomFieldException.Code#FIELD_QUOTA_EXCEEDED}。 */
    public void incFieldUsageOrThrow(long tenantId) {
        getOrInit(tenantId);
        int affected = mapper.tryIncFieldUsed(tenantId);
        if (affected == 0) {
            throw new CustomFieldException(CustomFieldException.Code.FIELD_QUOTA_EXCEEDED,
                    "租户 " + tenantId + " 字段额度已耗尽");
        }
    }

    /** 索引 used++ ；超额抛 {@link CustomFieldException.Code#INDEX_QUOTA_EXCEEDED}。 */
    public void incIndexUsageOrThrow(long tenantId) {
        getOrInit(tenantId);
        int affected = mapper.tryIncIndexUsed(tenantId);
        if (affected == 0) {
            throw new CustomFieldException(CustomFieldException.Code.INDEX_QUOTA_EXCEEDED,
                    "租户 " + tenantId + " 索引额度已耗尽");
        }
    }

    /** 字段 used--（不抛错；下溢被 DB CHECK 约束兜住）。 */
    public void decFieldUsage(long tenantId) {
        mapper.decFieldUsed(tenantId);
    }

    /** 索引 used--（不抛错）。 */
    public void decIndexUsage(long tenantId) {
        mapper.decIndexUsed(tenantId);
    }
}
