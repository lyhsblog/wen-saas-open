package com.wensaas.core.impl.pkg;

import com.wensaas.core.impl.tenant.web.TenantActivityGuard;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 租户级缓存失效器：租户状态 / 套餐变更时一并清两处按租户缓存的派生数据
 * —— {@link TenantActivityGuard}（活跃度）与 {@link PackageStatusGuard}（套餐）。
 *
 * <p>两个 guard 各走统一 CacheRegion，evict 经 Redis pub/sub 自动广播给其他节点失效，不经业务 MQ。
 * {@link PackageStatusGuard} 用 {@link ObjectProvider} 弱依赖（pkg 模块缺省时活跃度仍可单独失效）。
 */
public class TenantCacheEvictor {

    private final TenantActivityGuard activityGuard;
    private final ObjectProvider<PackageStatusGuard> packageStatusGuard;

    public TenantCacheEvictor(TenantActivityGuard activityGuard,
                               ObjectProvider<PackageStatusGuard> packageStatusGuard) {
        this.activityGuard = activityGuard;
        this.packageStatusGuard = packageStatusGuard;
    }

    /** 租户状态 / 套餐变更后调用：清该租户的活跃度 + 套餐缓存（各自经 pub/sub 广播）。 */
    public void evictTenant(long tenantId) {
        activityGuard.evict(tenantId);
        packageStatusGuard.ifAvailable(g -> g.evict(tenantId));
    }
}
