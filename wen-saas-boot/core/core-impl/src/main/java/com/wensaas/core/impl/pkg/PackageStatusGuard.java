package com.wensaas.core.impl.pkg;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.cache.CacheRegion;
import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.api.pkg.PackageStatusException;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.tenant.TenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * 套餐状态校验。
 *
 * <p>租户套餐解析结果（tenantId → ResolvedPlans）经统一 {@link CacheRegion} 缓存（本地 L1 + Redis L2 +
 * pub/sub 跨节点失效）；套餐变更后调 {@link #evict} 清缓存并自动广播——失效是缓存层自身能力，不经业务 MQ。
 *
 * <p>豁免规则：{@code tenantId == 0}（PLATFORM_TENANT_ID）及 {@code tenantId == null} 永远放行。
 */
public class PackageStatusGuard {

    private static final Logger log = LoggerFactory.getLogger(PackageStatusGuard.class);

    private final CacheRegion<TenantPlansResolver.ResolvedPlans> plansCache;
    private final TenantPlansResolver resolver;

    public PackageStatusGuard(TenantPlansResolver resolver, CacheRegionFactory cacheRegionFactory,
                               TenantProperties props) {
        this.resolver = resolver;
        this.plansCache = cacheRegionFactory.create("pkg:plans",
                Duration.ofSeconds(props.getPackageStatus().getCacheTtlSeconds()),
                JSON::toJSONString,
                s -> JSON.parseObject(s, TenantPlansResolver.ResolvedPlans.class));
    }

    /**
     * 校验当前租户对指定业务模块的访问权限。
     *
     * @param tenantId   租户 ID（null 或 0 直接放行）
     * @param moduleCode 业务模块编码
     * @param isWrite    true = 写方法（POST/PUT/PATCH/DELETE）
     * @throws PackageStatusException 未授权（402）或只读写操作（403）时抛出
     */
    public void requireAccess(Long tenantId, String moduleCode, boolean isWrite) {
        if (tenantId == null || tenantId == TenantContext.PLATFORM_TENANT_ID) {
            return;
        }
        TenantPlansResolver.ResolvedPlans plans = resolvePlans(tenantId);
        if (plans.activeModuleCodes().contains(moduleCode)) {
            return;
        }
        if (plans.readonlyModuleCodes().contains(moduleCode)) {
            if (isWrite) {
                throw new PackageStatusException(PackageStatusException.Code.PACKAGE_READONLY, moduleCode,
                        "模块 [" + moduleCode + "] 套餐已到期，禁止写操作");
            }
            return;
        }
        throw new PackageStatusException(PackageStatusException.Code.PACKAGE_UNAUTHORIZED, moduleCode,
                "未购买模块 [" + moduleCode + "] 的套餐");
    }

    /**
     * 套餐变更后调用：清缓存条目，统一缓存原语经 pub/sub 自动广播给其他节点。
     */
    public void evict(long tenantId) {
        plansCache.evict(String.valueOf(tenantId));
        log.debug("pkg.plans.cache.evicted tenantId={}", tenantId);
    }

    private TenantPlansResolver.ResolvedPlans resolvePlans(long tenantId) {
        return plansCache.getOrLoad(String.valueOf(tenantId), () -> resolver.resolve(tenantId));
    }
}
