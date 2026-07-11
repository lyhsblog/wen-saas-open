package com.wensaas.core.api.cache;

import java.time.Duration;
import java.util.function.Function;

/**
 * 缓存区工厂：按区名 + 既定本地缓存选项产出 {@link CacheRegion}。
 *
 * <p>value 的序列化/反序列化由调用方提供：底座默认实现把 value 以 String 存 Redis（StringCodec），
 * 由调用方用 fastjson2 等自行编解码，避开 Redisson 默认 codec 对泛型 / record 的坑（如 Long 反序列化成
 * Integer）。空值对象（如空集合）同样要能被 serializer 编码。
 */
public interface CacheRegionFactory {

    /**
     * @param name         区名（命名空间，实现会加统一前缀）
     * @param ttl          本地缓存 TTL 兜底基准（精确失效靠写后 evict 广播，TTL 仅防漏清）
     * @param serializer   V → String
     * @param deserializer String → V
     */
    <V> CacheRegion<V> create(String name, Duration ttl,
                              Function<V, String> serializer,
                              Function<String, V> deserializer);
}
