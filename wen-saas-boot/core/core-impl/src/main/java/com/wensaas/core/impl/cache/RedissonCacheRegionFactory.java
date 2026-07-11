package com.wensaas.core.impl.cache;

import com.wensaas.core.api.cache.CacheRegion;
import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.api.lock.DistributedLock;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.LocalCachedMapOptions;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.function.Function;

/**
 * Redisson 版 {@link CacheRegionFactory}（统一缓存原语）。
 *
 * <p>每个区 = 一个 {@link RLocalCachedMap}（一个 Redis 结构），命名空间 {@code wen-saas-boot:cache:<name>}。
 * 既定选项：
 * <ul>
 *   <li>{@code LOCALCACHE_REDIS}：本地 L1 + Redis L2 双存，冷节点可从 L2 暖；</li>
 *   <li>{@code INVALIDATE}：写后广播变更 key 失效信号（Redis pub/sub），多节点一致——不依赖业务 MQ；</li>
 *   <li>{@code LRU} + 本地 TTL 兜底：本地缓存有上限、防漏清；</li>
 *   <li>{@code CLEAR}：Redis 重连后清本地缓存，避免断连期间的脏副本。</li>
 * </ul>
 * value 以 StringCodec 存（由调用方提供 serializer/deserializer），仅用 Redisson Community 免费能力，不碰 PRO。
 */
public class RedissonCacheRegionFactory implements CacheRegionFactory {

    static final String KEY_PREFIX = "wen-saas-boot:cache:";

    private final RedissonClient redisson;
    private final DistributedLock lock;
    private final int localCacheSize;
    private final Duration singleFlightWait;

    public RedissonCacheRegionFactory(RedissonClient redisson, DistributedLock lock,
                                       int localCacheSize, Duration singleFlightWait) {
        this.redisson = redisson;
        this.lock = lock;
        this.localCacheSize = localCacheSize;
        this.singleFlightWait = singleFlightWait;
    }

    @Override
    public <V> CacheRegion<V> create(String name, Duration ttl,
                                     Function<V, String> serializer, Function<String, V> deserializer) {
        LocalCachedMapOptions<String, String> options = LocalCachedMapOptions.<String, String>name(KEY_PREFIX + name)
                .codec(StringCodec.INSTANCE)
                .storeMode(LocalCachedMapOptions.StoreMode.LOCALCACHE_REDIS)
                .syncStrategy(LocalCachedMapOptions.SyncStrategy.INVALIDATE)
                .reconnectionStrategy(LocalCachedMapOptions.ReconnectionStrategy.CLEAR)
                .evictionPolicy(LocalCachedMapOptions.EvictionPolicy.LRU)
                .cacheSize(localCacheSize)
                .timeToLive(ttl);
        RLocalCachedMap<String, String> map = redisson.getLocalCachedMap(options);
        return new RedissonCacheRegion<>(map, lock, name, singleFlightWait, serializer, deserializer);
    }
}
