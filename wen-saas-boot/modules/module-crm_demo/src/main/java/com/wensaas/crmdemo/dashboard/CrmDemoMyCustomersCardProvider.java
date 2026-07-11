package com.wensaas.crmdemo.dashboard;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.cache.CacheRegion;
import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.spi.DashboardCardProvider;
import com.wensaas.core.api.spi.def.DashboardCardData;
import com.wensaas.crmdemo.entity.CrmDemoCustomer;
import com.wensaas.crmdemo.entity.CustomerStatus;
import com.wensaas.crmdemo.repository.CrmDemoCustomerMapper;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 工作台卡片 {@code crm_demo.my_customers}「我的客户」（LIST 形态）。
 *
 * <p>统计当前登录成员名下（owner=self）客户的 潜在 / 跟进中 / 本周成交 计数。
 * 「本周成交」以 {@code status=WON && updated_at >= 本周一} 近似（WON 为终态、成交后不再更新，
 * demo 不为此单加 won_at 列）。租户线由拦截器自动注入。
 *
 * <p>统计经 {@link CacheRegion} 短 TTL 缓存（每次进工作台都实时数库→天然缓存场景；
 * key 按 租户:用户 隔离，60s 内的计数漂移可接受）。
 */
@Component
public class CrmDemoMyCustomersCardProvider implements DashboardCardProvider {

    private final CrmDemoCustomerMapper mapper;
    private final CacheRegion<DashboardCardData> cache;

    public CrmDemoMyCustomersCardProvider(CrmDemoCustomerMapper mapper, CacheRegionFactory cacheFactory) {
        this.mapper = mapper;
        this.cache = cacheFactory.create("crm_demo:card:my_customers", Duration.ofSeconds(60),
                JSON::toJSONString, s -> JSON.parseObject(s, DashboardCardData.class));
    }

    @Override
    public DashboardCardData render() {
        long userId = UserContext.require().userId();
        return cache.getOrLoad(TenantContext.require() + ":" + userId, () -> compute(userId));
    }

    private DashboardCardData compute(long userId) {
        long lead = countMine(userId, w -> w.eq(CrmDemoCustomer::getStatus, CustomerStatus.LEAD.name()));
        long following = countMine(userId, w -> w.eq(CrmDemoCustomer::getStatus, CustomerStatus.FOLLOWING.name()));

        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        long wonThisWeek = countMine(userId, w -> w
                .eq(CrmDemoCustomer::getStatus, CustomerStatus.WON.name())
                .ge(CrmDemoCustomer::getUpdatedAt, weekStart));

        return DashboardCardData.list(List.of(
                new DashboardCardData.Item("潜在", String.valueOf(lead), lead > 0 ? "warning" : "muted"),
                new DashboardCardData.Item("跟进中", String.valueOf(following), following > 0 ? "danger" : "muted"),
                new DashboardCardData.Item("本周成交", String.valueOf(wonThisWeek), "success")));
    }

    private long countMine(long userId, Consumer<LambdaQueryWrapper<CrmDemoCustomer>> extra) {
        LambdaQueryWrapper<CrmDemoCustomer> w = new LambdaQueryWrapper<CrmDemoCustomer>()
                .eq(CrmDemoCustomer::getOwnerId, userId);
        extra.accept(w);
        return mapper.selectCount(w);
    }
}
