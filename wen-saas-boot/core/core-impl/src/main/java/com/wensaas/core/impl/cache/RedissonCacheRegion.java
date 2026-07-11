package com.wensaas.core.impl.cache;

import com.wensaas.core.api.cache.CacheRegion;
import com.wensaas.core.api.lock.DistributedLock;
import org.redisson.api.RLocalCachedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 基于 Redisson {@link RLocalCachedMap} 的领域缓存区实现。
 *
 * <p>底层 map 以 StringCodec 存「String → String」：value 由本类用调用方提供的 serializer/deserializer
 * 编解码（沿用 RedisDictResolver 的 fastjson2 思路），避开 Redisson 默认 codec 对泛型 / record 的坑。
 *
 * <p>miss 回源经 {@link DistributedLock} 单飞（仅 miss 时取锁，写少故开销可忽略）：拿到锁者回源并写回，
 * 其余等待后复用结果；等待窗口内未拿到锁则降级直连 {@code loader}（不写缓存），保证不被锁长时间阻塞。
 * 失效用 {@code fastRemove}，自动清 L1 + L2 并经 {@code SyncStrategy.INVALIDATE} 广播给其他节点。
 *
 * <p><b>失效时机</b>：{@link #evict}/{@link #evictAll} 在事务内调用时
 * 自动延到 <b>提交后</b>（{@code afterCommit}）执行，无事务则立即执行——由本层封装，业务层只管调 evict。
 * 这样消除「写事务内 evict、commit 前并发读回填旧值并卡到 TTL」的竞态；事务回滚则不失效（数据未变）。
 */
public class RedissonCacheRegion<V> implements CacheRegion<V> {

    private static final Logger log = LoggerFactory.getLogger(RedissonCacheRegion.class);

    private final RLocalCachedMap<String, String> map;
    private final DistributedLock lock;
    private final String name;
    private final Duration singleFlightWait;
    private final Function<V, String> serializer;
    private final Function<String, V> deserializer;

    public RedissonCacheRegion(RLocalCachedMap<String, String> map, DistributedLock lock, String name,
                                Duration singleFlightWait,
                                Function<V, String> serializer, Function<String, V> deserializer) {
        this.map = map;
        this.lock = lock;
        this.name = name;
        this.singleFlightWait = singleFlightWait;
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    @Override
    public Optional<V> get(String key) {
        String raw = map.get(key);
        return raw == null ? Optional.empty() : Optional.of(deserializer.apply(raw));
    }

    @Override
    public V getOrLoad(String key, Supplier<V> loader) {
        String raw = map.get(key);
        if (raw != null) {
            return deserializer.apply(raw);
        }
        // miss → 单飞：仅 miss 时取锁，拿到才回源写回，其余等待后复用
        Optional<V> rebuilt = lock.tryExecuteWithLock(lockKey(key), singleFlightWait, () -> rebuild(key, loader));
        if (rebuilt.isPresent()) {
            return rebuilt.get();
        }
        // 等待窗口内未拿到锁（回源较慢）：再读一次（持锁者可能已写回），仍无则降级直连加载（不写缓存）
        String after = map.get(key);
        if (after != null) {
            return deserializer.apply(after);
        }
        log.debug("cache.region.singleflight.degrade region={} key={}", name, key);
        return loader.get();
    }

    private V rebuild(String key, Supplier<V> loader) {
        // 双重检查：先到的持锁线程可能已重建，避免重复回源
        String cached = map.get(key);
        if (cached != null) {
            return deserializer.apply(cached);
        }
        V value = loader.get();
        map.put(key, serializer.apply(value));
        return value;
    }

    @Override
    public void put(String key, V value) {
        map.put(key, serializer.apply(value));
    }

    @Override
    public void evict(String key) {
        afterCommitOrNow(() -> map.fastRemove(key));
    }

    @Override
    public void evictAll() {
        afterCommitOrNow(map::clear);
    }

    /**
     * 有活动事务则把失效动作延到提交后执行（{@code afterCommit}，回滚则不执行）；否则立即执行。
     * 把「读己之写」之外的跨事务脏回填竞态挡在底层，业务层无需手动处理失效时机。
     */
    private static void afterCommitOrNow(Runnable evictAction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictAction.run();
                }
            });
        } else {
            evictAction.run();
        }
    }

    /** 单飞重建锁 key：区名 + 业务 key（DistributedLock 实现再叠加 wen-saas-boot:lock: 命名空间）。 */
    private String lockKey(String key) {
        return "cache:" + name + ":" + key;
    }
}
