package com.wensaas.core.impl.customfield.resolver;

import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldQuota;

import java.util.List;
import com.wensaas.core.impl.customfield.interceptor.CustomFieldDataLoader;

/**
 * 自定义字段解析器。{@link CustomFieldDataLoader} 提供"读 DB"语义，本接口在其上叠加缓存语义。
 *
 * <p>语义与 {@link com.wensaas.core.impl.dict.resolver.DictResolver} 对齐。
 */
public interface CustomFieldResolver {

    /** 取某 (租户, 对象) 下未软删字段；命中缓存直接返回，未命中先 load 再缓存。 */
    List<CustomFieldDef> resolveDefs(long tenantId, String objectCode);

    /** 取某租户额度；不存在则由调用方（QuotaManager）init 默认值。 */
    CustomFieldQuota resolveQuota(long tenantId, int defaultFieldQuota, int defaultIndexQuota);

    /** 失效某 (租户, 对象) 的字段定义缓存。 */
    void evictDefs(long tenantId, String objectCode);

    /** 失效某租户的额度缓存。 */
    void evictQuota(long tenantId);

    /** 失效某租户所有缓存（字段定义 + 额度，跨全部 object）。 */
    void evictAll(long tenantId);
}
