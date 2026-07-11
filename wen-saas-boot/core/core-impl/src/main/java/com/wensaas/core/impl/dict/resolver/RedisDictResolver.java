package com.wensaas.core.impl.dict.resolver;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.wensaas.core.api.cache.CacheRegion;
import com.wensaas.core.api.cache.CacheRegionFactory;

import java.util.Optional;
import com.wensaas.core.impl.dict.DictProperties;

/**
 * 缓存版 {@link DictResolver}。
 *
 * <p>经统一 {@link CacheRegion} 缓存（本地 L1 + Redis L2 + pub/sub 跨节点失效），region 名 {@code dict}，
 * region 内 key = {@code tenantId:groupCode}，value 为 fastjson2 JSON。分组不存在亦缓存空标记
 * （{@link #NULL_MARKER}）以避免反复穿透 DB。单飞防击穿由 {@link CacheRegion#getOrLoad} 内置；
 * 写后 {@link #evict} 清条目并自动广播——不经业务 MQ，也不再有自管的全表 SCAN。
 */
public class RedisDictResolver implements DictResolver {

    private static final String NULL_MARKER = "\"__NULL__\"";

    private final CacheRegion<String> region;
    private final DictDataLoader loader;

    public RedisDictResolver(CacheRegionFactory cacheRegionFactory, DictDataLoader loader, DictProperties props) {
        this.loader = loader;
        this.region = cacheRegionFactory.create("dict", props.getCacheTtl(),
                java.util.function.Function.identity(), java.util.function.Function.identity());
    }

    @Override
    public Optional<DictDataLoader.DictGroupSnapshot> resolve(long tenantId, String groupCode) {
        String raw = region.getOrLoad(keyOf(tenantId, groupCode), () -> produce(tenantId, groupCode));
        if (NULL_MARKER.equals(raw)) {
            return Optional.empty();
        }
        return Optional.of(deserialize(raw));
    }

    /** 重建生产者：装载并序列化；分组不存在返回空标记（缓存防穿透）。 */
    private String produce(long tenantId, String groupCode) {
        Optional<DictDataLoader.DictGroupSnapshot> loaded = loader.load(tenantId, groupCode);
        return loaded.isEmpty() ? NULL_MARKER : serialize(loaded.get());
    }

    @Override
    public void evict(long tenantId, String groupCode) {
        region.evict(keyOf(tenantId, groupCode));
    }

    @Override
    public void evictAll() {
        region.evictAll();
    }

    /** region 内 key（CacheRegion 实现再叠加 {@code wen-saas-boot:cache:dict:} 命名空间）。 */
    static String keyOf(long tenantId, String groupCode) {
        return tenantId + ":" + groupCode;
    }

    static String serialize(DictDataLoader.DictGroupSnapshot snapshot) {
        return JSON.toJSONString(snapshot,
                JSONWriter.Feature.WriteNulls,
                JSONWriter.Feature.WriteEnumUsingToString);
    }

    static DictDataLoader.DictGroupSnapshot deserialize(String json) {
        try {
            return JSON.parseObject(json, DictDataLoader.DictGroupSnapshot.class,
                    JSONReader.Feature.SupportSmartMatch);
        } catch (RuntimeException e) {
            throw new IllegalStateException("DictGroupSnapshot JSON 反序列化失败：" + json, e);
        }
    }
}
