package com.wensaas.core.api.tenant;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 租户目录读服务：tenant domain 对外暴露的租户基础信息查询入口。
 *
 * <p>跨 domain 读取 {@code sys_tenant} 一律走本服务，<b>不得直接注入 {@code SysTenantMapper}</b>
 * （{@code SysTenant} 实体 + Mapper 归 tenant domain 私有）。
 *
 * <p>暴露范围：按 id / code 的单条 / 批量 lookup（{@link TenantInfo} 标量字段）+ 套餐授权
 * {@link #plansOf}。运营 dataScope 过滤聚合（dashboard/monitor/health 的时间窗 count/page）
 * 属 ops 报表查询、豁免不收口于本目录服务；写操作走各域写入口。
 */
public interface TenantQueryService {

    /** 按 id 取租户信息；不存在返回 {@code null}。 */
    TenantInfo tenantById(long tenantId);

    /** 按 code 取租户信息；不存在返回 {@code null}。 */
    TenantInfo tenantByCode(String code);

    /** 批量按 id 取租户信息；入参空或全部不存在返回空列表（顺序不保证、缺失 id 跳过）。 */
    List<TenantInfo> tenantsByIds(Collection<Long> tenantIds);

    /** 租户已授权套餐列表（{@code sys_tenant.plans}）；租户不存在或无 plans 返回空列表。 */
    List<TenantPlan> plansOf(long tenantId);

    /**
     * 正常状态（status=1）租户 id 列表。
     *
     * <p>供业务模块的跨租户定时任务遍历（逐租户 {@code TenantContext.runAs} 后走各自
     * TenantLine 隔离的 Mapper），暂停 / 已过期租户不参与周期性业务动作。
     */
    List<Long> activeTenantIds();

    /**
     * 归属指定 CSM 的租户 id 列表（{@code sys_tenant.csm_user_id = csmUserId}）。
     *
     * <p>供运营 dataScope=OPS_OWNED 的消费方做「我负责的租户」收敛：dataScope→csmUserId 的翻译
     * 由各 ops 消费方自理，本服务只回答按 csm 过滤的通用查询，不认识 OpsDataScope。
     */
    List<Long> tenantIdsByCsm(long csmUserId);

    /** 租户基础信息（不含 {@code plans}）。{@code csmUserId} = 归属 CSM 运营用户 id。 */
    record TenantInfo(Long id, String code, String name, Integer status,
                      Long mainAdminUserId, Instant expireAt, Long storageCap,
                      Boolean screenshotWatermarkEnabled, Long csmUserId) {}
}
