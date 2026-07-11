package com.wensaas.crmdemo.dashboard;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.cache.CacheRegion;
import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.api.spi.DashboardCardProvider;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.spi.def.DashboardCardData;
import com.wensaas.crmdemo.entity.CrmDemoCustomer;
import com.wensaas.crmdemo.repository.CrmDemoCustomerMapper;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工作台卡片 {@code crm_demo.weekly_new}「本周新增客户」（NUMBER_TREND 形态）。
 *
 * <p>本周（周一起）建档客户数 + 与上周同长度区间的环比。全租户口径（管理视角），
 * 行级可见性不叠加——工作台卡片是汇总数字、不外泄明细。
 */
@Component
public class CrmDemoWeeklyNewCardProvider implements DashboardCardProvider {

    private final CrmDemoCustomerMapper mapper;
    private final CacheRegion<DashboardCardData> cache;

    public CrmDemoWeeklyNewCardProvider(CrmDemoCustomerMapper mapper, CacheRegionFactory cacheFactory) {
        this.mapper = mapper;
        // 全租户口径统计，key 按租户隔离；60s TTL 的漂移可接受
        this.cache = cacheFactory.create("crm_demo:card:weekly_new", Duration.ofSeconds(60),
                JSON::toJSONString, s -> JSON.parseObject(s, DashboardCardData.class));
    }

    @Override
    public DashboardCardData render() {
        return cache.getOrLoad(String.valueOf(TenantContext.require()), this::compute);
    }

    private DashboardCardData compute() {
        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime lastWeekStart = weekStart.minusDays(7);

        long thisWeek = countCreatedBetween(weekStart, null);
        long lastWeek = countCreatedBetween(lastWeekStart, weekStart);

        long diff = thisWeek - lastWeek;
        String delta = diff == 0 ? "与上周持平" : String.format("较上周%+d", diff);
        String tone = diff > 0 ? "up" : diff < 0 ? "down" : "muted";
        return DashboardCardData.numberTrend(String.valueOf(thisWeek), "个", delta, tone);
    }

    private long countCreatedBetween(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        LambdaQueryWrapper<CrmDemoCustomer> w = new LambdaQueryWrapper<CrmDemoCustomer>()
                .ge(CrmDemoCustomer::getCreatedAt, fromInclusive);
        if (toExclusive != null) {
            w.lt(CrmDemoCustomer::getCreatedAt, toExclusive);
        }
        return mapper.selectCount(w);
    }
}
