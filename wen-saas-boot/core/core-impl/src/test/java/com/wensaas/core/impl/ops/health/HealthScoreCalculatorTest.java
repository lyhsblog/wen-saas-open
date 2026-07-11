package com.wensaas.core.impl.ops.health;

import com.wensaas.core.api.auth.user.MemberCountQuery;
import com.wensaas.core.api.ops.health.HealthLevel;
import com.wensaas.core.api.ops.health.OpsHealthService.RiskSignal;
import com.wensaas.core.impl.audit.log.SysLoginLogMapper;
import com.wensaas.core.impl.audit.log.SysOperationLogMapper;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.ops.monitor.SysTenantRenewalLogMapper;
import com.wensaas.core.api.tenant.TenantPlan;
import com.wensaas.core.impl.ticket.TicketTicketMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 健康度评分计算器单测：验证「命中风险信号 → 扣分 → 归档」核心逻辑。
 */
class HealthScoreCalculatorTest {

    private final SysLoginLogMapper loginLogMapper = mock(SysLoginLogMapper.class);
    private final MemberCountQuery memberCount = mock(MemberCountQuery.class);
    private final SysOperationLogMapper opLogMapper = mock(SysOperationLogMapper.class);
    private final TicketTicketMapper ticketMapper = mock(TicketTicketMapper.class);
    private final SysTenantRenewalLogMapper renewalLogMapper = mock(SysTenantRenewalLogMapper.class);

    /** 用真实门面 + 空 provider → 全部走编译期默认权重/阈值（与初始化种子一致）。 */
    private HealthScoreCalculator newCalculator() {
        @SuppressWarnings("unchecked")
        ObjectProvider<com.wensaas.core.api.ops.setting.OpsSettingService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        HealthScoreSettings settings = new HealthScoreSettings(provider);
        return new HealthScoreCalculator(
                loginLogMapper, memberCount, opLogMapper, ticketMapper, renewalLogMapper, settings);
    }

    @Test
    void dangerTenant_firesFourSignals_scoreClampedAndLevelDanger() {
        Instant now = Instant.now();
        // 窗口内 0 次成功登录 → 长期无登录(-25)
        when(loginLogMapper.selectCount(any())).thenReturn(0L);
        // distinctActiveMembers：本月=1 / 上月=10 / 近30天=1 → 月活下降(-10)
        when(loginLogMapper.selectObjs(any())).thenReturn(
                objList(1), objList(10), objList(1));
        when(memberCount.countMembers(anyLong())).thenReturn(50L);
        // 模块访问：近30天 12 / 前30天 30 → 环比 -60%
        when(opLogMapper.selectCount(any())).thenReturn(12L, 30L);

        SysTenant tenant = new SysTenant();
        tenant.setId(1001L);
        tenant.setName("ABC 公司");
        tenant.setExpireAt(now.plus(5, ChronoUnit.DAYS)); // 距到期 5 天 → 临近(-20)
        tenant.setPlans(null);                            // 无任何套餐 → 仅基础包(-10)

        HealthScoreCalculator.Computation c = newCalculator().compute(tenant, Set.of("base"), now);

        // 100 - 25 - 10 - 20 - 10 = 35
        assertThat(c.score()).isEqualTo(35);
        assertThat(c.level()).isEqualTo(HealthLevel.DANGER);
        Map<String, Integer> byCode = c.signals().stream()
                .collect(Collectors.toMap(RiskSignal::code, RiskSignal::weight));
        assertThat(byCode).containsKeys("no_login_30d", "mau_drop", "expiring_soon", "only_basic");
        assertThat(byCode.get("no_login_30d")).isEqualTo(25);
        assertThat(byCode.get("expiring_soon")).isEqualTo(20);
        assertThat(c.metrics().totalMembers()).isEqualTo(50);
        assertThat(c.metrics().moduleAccessChangePct()).isEqualTo(-60);
    }

    @Test
    void healthyTenant_noSignals_score100() {
        Instant now = Instant.now();
        when(loginLogMapper.selectCount(any())).thenReturn(8L);          // 有登录
        when(loginLogMapper.selectObjs(any())).thenReturn(
                objList(10), objList(10), objList(10));                  // 月活平稳
        when(memberCount.countMembers(anyLong())).thenReturn(20L);
        when(opLogMapper.selectCount(any())).thenReturn(100L, 100L);

        SysTenant tenant = new SysTenant();
        tenant.setId(1002L);
        tenant.setName("XYZ 公司");
        tenant.setExpireAt(now.plus(200, ChronoUnit.DAYS));             // 到期远
        // active 的非系统业务包 → 不算仅基础包
        tenant.setPlans(List.of(new TenantPlan("biz", "2026-01-01T00:00:00Z", null, null)));

        HealthScoreCalculator.Computation c = newCalculator().compute(tenant, Set.of("base"), now);

        assertThat(c.score()).isEqualTo(100);
        assertThat(c.level()).isEqualTo(HealthLevel.HEALTHY);
        assertThat(c.signals()).isEmpty();
    }

    @Test
    void onlyBasicPackage_firesSignal() {
        Instant now = Instant.now();
        when(loginLogMapper.selectCount(any())).thenReturn(8L);
        when(loginLogMapper.selectObjs(any())).thenReturn(objList(5), objList(5), objList(5));
        when(memberCount.countMembers(anyLong())).thenReturn(10L);
        when(opLogMapper.selectCount(any())).thenReturn(0L, 0L);

        SysTenant tenant = new SysTenant();
        tenant.setId(1003L);
        tenant.setName("仅基础包公司");
        tenant.setExpireAt(now.plus(200, ChronoUnit.DAYS));
        // 仅持有系统/基础包 → 命中
        tenant.setPlans(List.of(new TenantPlan("base", "2026-01-01T00:00:00Z", null, null)));

        HealthScoreCalculator.Computation c = newCalculator().compute(tenant, Set.of("base"), now);

        assertThat(c.score()).isEqualTo(90);
        assertThat(c.level()).isEqualTo(HealthLevel.HEALTHY);
        assertThat(c.signals()).extracting(RiskSignal::code).containsExactly("only_basic");
    }

    @Test
    void openTickets_fireUrgentAndVolumeSignals() {
        Instant now = Instant.now();
        when(loginLogMapper.selectCount(any())).thenReturn(8L);
        when(loginLogMapper.selectObjs(any())).thenReturn(objList(5), objList(5), objList(5));
        when(memberCount.countMembers(anyLong())).thenReturn(10L);
        when(opLogMapper.selectCount(any())).thenReturn(0L, 0L);
        // compute 内顺序：先 countOpenTickets(总数=6 ≥ 阈值5) 再 countOpenUrgentTickets(紧急=2)
        when(ticketMapper.selectCount(any())).thenReturn(6L, 2L);

        SysTenant tenant = new SysTenant();
        tenant.setId(2001L);
        tenant.setName("工单堆积公司");
        tenant.setExpireAt(now.plus(200, ChronoUnit.DAYS));
        tenant.setPlans(List.of(new TenantPlan("biz", "2026-01-01T00:00:00Z", null, null)));

        HealthScoreCalculator.Computation c = newCalculator().compute(tenant, Set.of("base"), now);

        // 100 - 15(urgent_ticket) - 10(ticket_volume) = 75
        assertThat(c.score()).isEqualTo(75);
        assertThat(c.signals()).extracting(RiskSignal::code)
                .contains("urgent_ticket", "ticket_volume");
        assertThat(c.metrics().ticketCount()).isEqualTo(6);
    }

    @Test
    void renewalGap_expiredWithPriorRenewal_firesSignalAndSetsDataGrowth() {
        Instant now = Instant.now();
        when(loginLogMapper.selectCount(any())).thenReturn(8L);          // 有登录
        when(loginLogMapper.selectObjs(any())).thenReturn(objList(5), objList(5), objList(5));
        when(memberCount.countMembers(anyLong())).thenReturn(10L);
        // opLog 三次：模块访问 30d / 前 30d / 建档量(create)
        when(opLogMapper.selectCount(any())).thenReturn(100L, 100L, 42L);
        // 曾续费过的老客户
        when(renewalLogMapper.selectCount(any())).thenReturn(1L);

        SysTenant tenant = new SysTenant();
        tenant.setId(3001L);
        tenant.setName("断档公司");
        tenant.setExpireAt(now.minus(10, ChronoUnit.DAYS));             // 已过期 10 天
        tenant.setPlans(List.of(new TenantPlan("biz", "2026-01-01T00:00:00Z", null, null)));

        HealthScoreCalculator.Computation c = newCalculator().compute(tenant, Set.of("base"), now);

        // 100 - 10(renewal_gap) = 90
        assertThat(c.score()).isEqualTo(90);
        assertThat(c.signals()).extracting(RiskSignal::code).contains("renewal_gap");
        // 数据增长指标接 create 建档量代理
        assertThat(c.metrics().dataGrowth()).isEqualTo(42L);
    }

    @Test
    void renewalGap_expiredButNeverRenewed_doesNotFire() {
        Instant now = Instant.now();
        when(loginLogMapper.selectCount(any())).thenReturn(8L);
        when(loginLogMapper.selectObjs(any())).thenReturn(objList(5), objList(5), objList(5));
        when(memberCount.countMembers(anyLong())).thenReturn(10L);
        when(opLogMapper.selectCount(any())).thenReturn(100L, 100L, 0L);
        when(renewalLogMapper.selectCount(any())).thenReturn(0L);        // 从未续费

        SysTenant tenant = new SysTenant();
        tenant.setId(3002L);
        tenant.setName("试用未转付公司");
        tenant.setExpireAt(now.minus(10, ChronoUnit.DAYS));
        tenant.setPlans(List.of(new TenantPlan("biz", "2026-01-01T00:00:00Z", null, null)));

        HealthScoreCalculator.Computation c = newCalculator().compute(tenant, Set.of("base"), now);

        assertThat(c.signals()).extracting(RiskSignal::code).doesNotContain("renewal_gap");
    }

    @Test
    void mauDrop_notFired_whenLastMonthBaseBelowMinBase() {
        Instant now = Instant.now();
        when(loginLogMapper.selectCount(any())).thenReturn(8L);          // 有登录 → 不触发长期无登录
        // 上月=1 / 本月=0 / 近30天=0：上月基数 1 < mauMinBase(3) → 不判「月活下降」（原会误报 100%）
        when(loginLogMapper.selectObjs(any())).thenReturn(objList(0), objList(1), objList(0));
        when(memberCount.countMembers(anyLong())).thenReturn(2L);
        when(opLogMapper.selectCount(any())).thenReturn(0L, 0L);

        SysTenant tenant = new SysTenant();
        tenant.setId(1004L);
        tenant.setName("待激活公司");
        tenant.setExpireAt(now.plus(200, ChronoUnit.DAYS));
        tenant.setPlans(List.of(new TenantPlan("biz", "2026-01-01T00:00:00Z", null, null)));

        HealthScoreCalculator.Computation c = newCalculator().compute(tenant, Set.of("base"), now);

        assertThat(c.signals()).extracting(RiskSignal::code).doesNotContain("mau_drop");
    }

    private static List<Object> objList(int size) {
        Function<Integer, Object> id = i -> (long) i;
        return java.util.stream.IntStream.range(0, size).boxed().map(id::apply).collect(Collectors.toList());
    }
}
