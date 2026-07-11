package com.wensaas.core.api.cache;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 领域缓存区：单一命名空间下的 {@code String key → V value} 缓存，多实例一致。
 *
 * <p>底座默认实现基于 Redisson RLocalCachedMap（L1 进程内本地缓存 + L2 Redis + Redis pub/sub
 * 自动跨节点失效）。调用方面向本接口编程，不直接耦合 Redisson；缓存失效是本区自身能力，不依赖业务 MQ。
 *
 * <p>约定：
 * <ul>
 *   <li>value 非 null——「无数据」用空集合等空值对象表达，由 {@code loader} 返回；本区不缓存 null。</li>
 *   <li>key 由调用方组装（如 {@code tenantId + ":" + userId}）；区名提供命名空间隔离。</li>
 * </ul>
 */
public interface CacheRegion<V> {

    /** 读缓存；未命中返回空 Optional（不回源）。 */
    Optional<V> get(String key);

    /** 读缓存；miss 时单飞回源并写回。{@code loader} 必须返回非 null。 */
    V getOrLoad(String key, Supplier<V> loader);

    /** 写入/覆盖一个条目（自动同步本地 + Redis 并广播）。 */
    void put(String key, V value);

    /** 失效单个 key：清本区 L1 + L2 并广播给其他节点。 */
    void evict(String key);

    /** 清空本区全部条目（仅本命名空间一个结构；非跨区、非跨租户 SCAN）。 */
    void evictAll();
}
