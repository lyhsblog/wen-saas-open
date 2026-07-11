package com.wensaas.core.impl.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantPlan;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * {@link TenantQueryService} 实现。tenant domain 内对外服务租户基础信息查询，
 * 跨 domain 消费方经此入口而非裸查 {@link SysTenantMapper}。
 */
public class TenantQueryServiceImpl implements TenantQueryService {

    private final SysTenantMapper tenantMapper;

    public TenantQueryServiceImpl(SysTenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    @Override
    public TenantInfo tenantById(long tenantId) {
        return toInfo(tenantMapper.selectById(tenantId));
    }

    @Override
    public TenantInfo tenantByCode(String code) {
        return toInfo(tenantMapper.selectOne(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getCode, code)));
    }

    @Override
    public List<TenantInfo> tenantsByIds(Collection<Long> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return List.of();
        }
        return tenantMapper.selectBatchIds(tenantIds).stream().map(this::toInfo).filter(Objects::nonNull).toList();
    }

    @Override
    public List<TenantPlan> plansOf(long tenantId) {
        if (tenantId <= 0L) {
            return List.of();
        }
        SysTenant t = tenantMapper.selectById(tenantId);
        return (t == null || t.getPlans() == null) ? List.of() : t.getPlans();
    }

    @Override
    public List<Long> activeTenantIds() {
        return tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                        .select(SysTenant::getId)
                        .eq(SysTenant::getStatus, 1)).stream()
                .map(SysTenant::getId).toList();
    }

    @Override
    public List<Long> tenantIdsByCsm(long csmUserId) {
        return tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                        .select(SysTenant::getId)
                        .eq(SysTenant::getCsmUserId, csmUserId)).stream()
                .map(SysTenant::getId).toList();
    }

    private TenantInfo toInfo(SysTenant t) {
        return t == null ? null : new TenantInfo(t.getId(), t.getCode(), t.getName(), t.getStatus(),
                t.getMainAdminUserId(), t.getExpireAt(), t.getStorageCap(), t.getScreenshotWatermarkEnabled(),
                t.getCsmUserId());
    }
}
