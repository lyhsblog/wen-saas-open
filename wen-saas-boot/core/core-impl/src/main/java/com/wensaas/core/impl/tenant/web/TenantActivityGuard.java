package com.wensaas.core.impl.tenant.web;

import com.wensaas.core.api.cache.CacheRegion;
import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.api.tenant.TenantActivityException;
import com.wensaas.core.api.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.tenant.TenantProperties;

/**
 * 租户活跃度校验：按租户状态判定暂停 / 到期租户，供写请求拦截。
 *
 * <p>租户状态（tenantId → status）经统一 {@link CacheRegion} 缓存（本地 L1 + Redis L2 + pub/sub
 * 跨节点失效），写状态变更后调 {@link #evict} 即清本地并广播给其他节点——失效是缓存层自身能力，不经业务 MQ。
 *
 * <p>豁免规则：{@code tenantId == 0}（PLATFORM_TENANT_ID）及 {@code tenantId == null} 永远放行。
 */
public class TenantActivityGuard {

    private static final Logger log = LoggerFactory.getLogger(TenantActivityGuard.class);

    /** status=0 暂停 / 1 正常 / 2 已过期。 */
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_SUSPENDED = 0;

    private final CacheRegion<Integer> statusCache;
    private final SysTenantMapper tenantMapper;

    public TenantActivityGuard(SysTenantMapper tenantMapper, CacheRegionFactory cacheRegionFactory,
                                TenantProperties props) {
        this.tenantMapper = tenantMapper;
        this.statusCache = cacheRegionFactory.create("tenant:activity",
                Duration.ofSeconds(props.getActivityGuard().getCacheTtlSeconds()),
                String::valueOf, Integer::valueOf);
    }

    /**
     * Web 拦截器调用：当前 {@link TenantContext} 里的租户必须处于正常状态，否则抛出。
     * 平台租户（id=0）直接放行。
     */
    public void requireActive() {
        Long tenantId = TenantContext.get();
        if (tenantId == null || tenantId == TenantContext.PLATFORM_TENANT_ID) {
            return;
        }
        requireActive(tenantId);
    }

    /**
     * MQ 消费者调用：tenantId 明确传入。
     * null 或 0 直接放行（平台消息 / 匿名调用）。
     */
    public void requireActive(Long tenantId) {
        if (tenantId == null || tenantId == TenantContext.PLATFORM_TENANT_ID) {
            return;
        }
        int status = resolveStatus(tenantId);
        if (status == STATUS_ACTIVE) {
            return;
        }
        if (status == STATUS_SUSPENDED) {
            throw new TenantActivityException(TenantActivityException.Code.TENANT_SUSPENDED,
                    "租户已暂停，禁止写操作");
        }
        throw new TenantActivityException(TenantActivityException.Code.TENANT_EXPIRED,
                "租户已到期，禁止写操作");
    }

    /**
     * 不抛出版本，专供 MQ 消费者做 skip 判断：返回 false 表示需要跳过。
     * null 或 0 直接返回 true（放行）。
     */
    public boolean isActive(Long tenantId) {
        if (tenantId == null || tenantId == TenantContext.PLATFORM_TENANT_ID) {
            return true;
        }
        return resolveStatus(tenantId) == STATUS_ACTIVE;
    }

    /**
     * 租户状态变更后调用：清缓存条目，统一缓存原语经 pub/sub 自动广播给其他节点。
     */
    public void evict(long tenantId) {
        statusCache.evict(String.valueOf(tenantId));
        log.debug("tenant.activity.cache.evicted tenantId={}", tenantId);
    }

    private int resolveStatus(long tenantId) {
        return statusCache.getOrLoad(String.valueOf(tenantId), () -> {
            SysTenant tenant = tenantMapper.selectById(tenantId);
            // 找不到租户视为已过期（不存在 = 无法访问）
            return (tenant != null && tenant.getStatus() != null) ? tenant.getStatus() : 2;
        });
    }
}
