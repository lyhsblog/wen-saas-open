package com.wensaas.core.impl.customfield.resolver;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.TypeReference;
import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldQuota;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import com.wensaas.core.impl.customfield.CustomFieldProperties;
import com.wensaas.core.impl.customfield.interceptor.CustomFieldDataLoader;
import com.wensaas.core.impl.customfield.quota.CustomFieldQuotaManager;

/**
 * Redis 缓存版 {@link CustomFieldResolver}。
 *
 * <p>Key 形如：
 * <ul>
 *   <li>字段定义：{@code wen-saas-boot:customfield:defs:{tenantId}:{objectCode}}</li>
 *   <li>额度：{@code wen-saas-boot:customfield:quota:{tenantId}}</li>
 * </ul>
 *
 * <p>字段定义"空列表"按真值缓存（fastjson2 {@code "[]"}），不需 NULL marker；
 * 额度首次未命中由 {@link CustomFieldQuotaManager} 写默认行后再装载。
 */
public class RedisCustomFieldResolver implements CustomFieldResolver {

    static final String DEFS_PREFIX = "wen-saas-boot:customfield:defs:";
    static final String QUOTA_PREFIX = "wen-saas-boot:customfield:quota:";
    static final String TENANT_PATTERN = "wen-saas-boot:customfield:*:";

    private final StringRedisTemplate redis;
    private final CustomFieldDataLoader loader;
    private final CustomFieldProperties props;

    public RedisCustomFieldResolver(StringRedisTemplate redis,
                                      CustomFieldDataLoader loader,
                                      CustomFieldProperties props) {
        this.redis = redis;
        this.loader = loader;
        this.props = props;
    }

    @Override
    public List<CustomFieldDef> resolveDefs(long tenantId, String objectCode) {
        String key = defsKey(tenantId, objectCode);
        String raw = redis.opsForValue().get(key);
        if (raw != null) {
            return deserializeDefs(raw);
        }
        List<CustomFieldDef> loaded = loader.loadDefs(tenantId, objectCode);
        redis.opsForValue().set(key, serializeDefs(loaded), props.getCacheTtl());
        return loaded;
    }

    @Override
    public CustomFieldQuota resolveQuota(long tenantId, int defaultFieldQuota, int defaultIndexQuota) {
        String key = quotaKey(tenantId);
        String raw = redis.opsForValue().get(key);
        if (raw != null) {
            return deserializeQuota(raw);
        }
        CustomFieldQuota q = loader.loadQuota(tenantId).orElseGet(() -> new CustomFieldQuota(
                tenantId, defaultFieldQuota, 0, defaultIndexQuota, 0));
        redis.opsForValue().set(key, serializeQuota(q), props.getCacheTtl());
        return q;
    }

    @Override
    public void evictDefs(long tenantId, String objectCode) {
        redis.delete(defsKey(tenantId, objectCode));
    }

    @Override
    public void evictQuota(long tenantId) {
        redis.delete(quotaKey(tenantId));
    }

    @Override
    public void evictAll(long tenantId) {
        scanAndDelete(DEFS_PREFIX + tenantId + ":*");
        redis.delete(quotaKey(tenantId));
    }

    private void scanAndDelete(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                redis.delete(cursor.next());
            }
        }
    }

    static String defsKey(long tenantId, String objectCode) {
        return DEFS_PREFIX + tenantId + ":" + objectCode;
    }

    static String quotaKey(long tenantId) {
        return QUOTA_PREFIX + tenantId;
    }

    static String serializeDefs(List<CustomFieldDef> defs) {
        return JSON.toJSONString(defs,
                JSONWriter.Feature.WriteNulls,
                JSONWriter.Feature.WriteEnumUsingToString);
    }

    static List<CustomFieldDef> deserializeDefs(String json) {
        try {
            return JSON.parseObject(json, new TypeReference<List<CustomFieldDef>>() {}.getType(),
                    JSONReader.Feature.SupportSmartMatch);
        } catch (RuntimeException e) {
            throw new IllegalStateException("CustomFieldDef[] JSON 反序列化失败：" + json, e);
        }
    }

    static String serializeQuota(CustomFieldQuota q) {
        return JSON.toJSONString(q, JSONWriter.Feature.WriteNulls);
    }

    static CustomFieldQuota deserializeQuota(String json) {
        try {
            return JSON.parseObject(json, CustomFieldQuota.class,
                    JSONReader.Feature.SupportSmartMatch);
        } catch (RuntimeException e) {
            throw new IllegalStateException("CustomFieldQuota JSON 反序列化失败：" + json, e);
        }
    }
}
