package com.wensaas.core.impl.notification.template;

import java.util.Optional;

/**
 * 通知模板解析器抽象。
 *
 * <p>实现：{@link RedisTemplateResolver} 提供 Redis 缓存层；测试可直接用 {@link TemplateDataLoader} 装载。
 */
public interface TemplateResolver {

    Optional<TemplateSnapshot> resolve(long tenantId, String templateCode);

    void evict(long tenantId, String templateCode);

    /**
     * 失效某模板在<b>所有租户</b>下的缓存。
     *
     * <p>因每个租户的快照都内嵌 tenant_id=0 默认文案，运营侧改动默认模板后须连带刷新各租户缓存
     * （否则旧文案残留至 TTL 过期）。
     */
    void evictCodeAllTenants(String templateCode);

    void evictAll();
}
