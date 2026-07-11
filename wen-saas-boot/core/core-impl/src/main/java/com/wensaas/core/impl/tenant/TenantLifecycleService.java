package com.wensaas.core.impl.tenant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.pkg.TenantCacheEvictor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户生命周期写入口（tenant domain 私有，系统内部触发、无权限门控）。
 *
 * <p>收口 {@code sys_tenant} 的生命周期写：把「写库 + 缓存失效」绑在一起，调用方（调度 / MQ 等）
 * 无从漏失效——这正是本规范第五节立写服务的动机（参见 {@code transferMainAdmin} 反例）。
 * 跨租户扫描标 {@link CrossTenantQuery} 绕过 MP TenantLine（系统调度无环境租户上下文）。
 */
public class TenantLifecycleService {

    private final SysTenantMapper tenantMapper;
    private final TenantCacheEvictor tenantCacheEvictor;

    public TenantLifecycleService(SysTenantMapper tenantMapper, TenantCacheEvictor tenantCacheEvictor) {
        this.tenantMapper = tenantMapper;
        this.tenantCacheEvictor = tenantCacheEvictor;
    }

    /**
     * 扫 {@code expire_at < now 且 status != 2} 的租户，批量置 status=2（已过期）并逐个失效其缓存。
     *
     * @param batchSize 单次最多处理条数
     * @return 实际置过期的条数
     */
    @CrossTenantQuery("TenantLifecycleService.markExpiredTenants: 系统调度扫全租户，无租户上下文")
    public int markExpiredTenants(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        List<SysTenant> expired = tenantMapper.selectList(
                Wrappers.<SysTenant>lambdaQuery()
                        .isNotNull(SysTenant::getExpireAt)
                        .lt(SysTenant::getExpireAt, Instant.now())
                        .ne(SysTenant::getStatus, 2)
                        .last("LIMIT " + batchSize));
        if (expired.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (SysTenant t : expired) {
            SysTenant update = new SysTenant();
            update.setId(t.getId());
            update.setStatus(2);
            update.setUpdatedAt(now);
            tenantMapper.updateById(update);
            tenantCacheEvictor.evictTenant(t.getId());
            count++;
        }
        return count;
    }
}
