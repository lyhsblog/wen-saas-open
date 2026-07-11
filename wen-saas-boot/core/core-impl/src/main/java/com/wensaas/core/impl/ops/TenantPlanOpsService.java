package com.wensaas.core.impl.ops;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.ops.TenantOpsException;
import com.wensaas.core.api.ops.TenantOpsService.AddSeatAddonCommand;
import com.wensaas.core.api.ops.TenantOpsService.AssignPlanCommand;
import com.wensaas.core.api.ops.TenantOpsService.RenewPlanCommand;
import com.wensaas.core.api.ops.TenantOpsService.RevokePlanCommand;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.pkg.SysPackage;
import com.wensaas.core.impl.pkg.SysPackageMapper;
import com.wensaas.core.api.tenant.TenantPlan;
import com.wensaas.core.impl.pkg.TenantCacheEvictor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 运营租户「套餐 / 席位」协作类：勾选 / 取消 / 续费套餐包、加购席位，并写续费台账。
 *
 * <p>由 {@code TenantOpsServiceImpl}（M18 God Class）拆出。
 * 标 {@code @CrossTenantQuery}：运营操作无租户上下文，绕过 MP TenantLine。
 * 各写方法经 {@code TenantOpsSupport.requireTenantInScope} 做数据范围写守卫
 * （OPS_OWNED 仅可操作名下租户）。
 */
@CrossTenantQuery("TenantPlanOpsService: 运营套餐/席位操作，无租户上下文")
public class TenantPlanOpsService {

    private final TenantOpsSupport support;
    private final SysTenantMapper tenantMapper;
    private final SysPackageMapper packageMapper;
    private final SysTenantSeatAddonMapper seatAddonMapper;
    private final com.wensaas.core.impl.ops.monitor.SysTenantRenewalLogMapper renewalLogMapper;
    private final TenantCacheEvictor tenantCacheEvictor;

    public TenantPlanOpsService(TenantOpsSupport support,
                                SysTenantMapper tenantMapper,
                                SysPackageMapper packageMapper,
                                SysTenantSeatAddonMapper seatAddonMapper,
                                com.wensaas.core.impl.ops.monitor.SysTenantRenewalLogMapper renewalLogMapper,
                                TenantCacheEvictor tenantCacheEvictor) {
        this.support = support;
        this.tenantMapper = tenantMapper;
        this.packageMapper = packageMapper;
        this.seatAddonMapper = seatAddonMapper;
        this.renewalLogMapper = renewalLogMapper;
        this.tenantCacheEvictor = tenantCacheEvictor;
    }

    /** 写一条套餐续费台账（grant/renew/revoke），供平台监控看板续费率/趋势聚合。 */
    private void recordRenewalLog(long tenantId, String packageCode, String eventType,
                                  Instant oldExpireAt, Instant newExpireAt, Integer ttlDays) {
        com.wensaas.core.impl.ops.monitor.SysTenantRenewalLog row =
                new com.wensaas.core.impl.ops.monitor.SysTenantRenewalLog();
        row.setTenantId(tenantId);
        row.setPackageCode(packageCode);
        row.setEventType(eventType);
        row.setOldExpireAt(oldExpireAt);
        row.setNewExpireAt(newExpireAt);
        row.setTtlDays(ttlDays);
        row.setOperatorId(UserContext.require().userId());
        row.setCreatedAt(LocalDateTime.now());
        renewalLogMapper.insert(row);
    }

    @Transactional
    public void assignPlan(long tenantId, AssignPlanCommand cmd) {
        SysTenant tenant = support.requireTenantInScope(tenantId);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_TENANT, String.valueOf(tenantId), tenant.getName());

        SysPackage pkg = packageMapper.selectOne(Wrappers.<SysPackage>lambdaQuery()
                .eq(SysPackage::getCode, cmd.packageCode()));
        if (pkg == null) {
            throw new TenantOpsException(TenantOpsException.Code.PACKAGE_NOT_FOUND,
                    "套餐包不存在：" + cmd.packageCode());
        }
        if (pkg.getStatus() == null || pkg.getStatus() != 1) {
            throw new TenantOpsException(TenantOpsException.Code.PACKAGE_NOT_ACTIVE,
                    "套餐包未启用：" + cmd.packageCode());
        }

        List<TenantPlan> plans = tenant.getPlans() != null ? new ArrayList<>(tenant.getPlans()) : new ArrayList<>();
        // 不能勾选已存在且未 revoked 的同 code 包
        for (TenantPlan p : plans) {
            if (cmd.packageCode().equals(p.packageCode()) && p.revokedAt() == null) {
                throw new TenantOpsException(TenantOpsException.Code.PLAN_ALREADY_ACTIVE,
                        "该套餐包已勾选且未撤销：" + cmd.packageCode());
            }
        }

        Instant now = Instant.now();
        String grantedAt = now.toString();
        String expiresAt = null;
        int ttlDays = cmd.ttlDays() != null ? cmd.ttlDays()
                : (pkg.getDefaultTtlDays() != null ? pkg.getDefaultTtlDays() : 0);
        if (ttlDays > 0) {
            expiresAt = now.plusSeconds((long) ttlDays * 86400L).toString();
        }
        plans.add(new TenantPlan(cmd.packageCode(), grantedAt, expiresAt, null));

        SysTenant update = new SysTenant();
        update.setId(tenantId);
        update.setPlans(plans);
        update.setUpdatedAt(LocalDateTime.now());
        update.setUpdatedBy(UserContext.require().userId());
        tenantMapper.updateById(update);
        tenantCacheEvictor.evictTenant(tenantId);

        recordRenewalLog(tenantId, cmd.packageCode(),
                com.wensaas.core.impl.ops.monitor.SysTenantRenewalLog.EVENT_GRANT,
                null, expiresAt == null ? null : Instant.parse(expiresAt),
                ttlDays > 0 ? ttlDays : null);
    }

    @Transactional
    public void revokePlan(long tenantId, RevokePlanCommand cmd) {
        SysTenant tenant = support.requireTenantInScope(tenantId);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_TENANT, String.valueOf(tenantId), tenant.getName());

        List<TenantPlan> plans = tenant.getPlans() != null ? new ArrayList<>(tenant.getPlans()) : new ArrayList<>();
        int targetIdx = -1;
        for (int i = 0; i < plans.size(); i++) {
            TenantPlan p = plans.get(i);
            if (cmd.packageCode().equals(p.packageCode()) && p.revokedAt() == null) {
                targetIdx = i;
                break;
            }
        }
        if (targetIdx < 0) {
            throw new TenantOpsException(TenantOpsException.Code.PLAN_NOT_FOUND,
                    "未找到可撤销的套餐包授权：" + cmd.packageCode());
        }

        TenantPlan old = plans.get(targetIdx);
        plans.set(targetIdx, new TenantPlan(old.packageCode(), old.grantedAt(), old.expiresAt(), Instant.now().toString()));

        SysTenant update = new SysTenant();
        update.setId(tenantId);
        update.setPlans(plans);
        update.setUpdatedAt(LocalDateTime.now());
        update.setUpdatedBy(UserContext.require().userId());
        tenantMapper.updateById(update);
        tenantCacheEvictor.evictTenant(tenantId);

        recordRenewalLog(tenantId, cmd.packageCode(),
                com.wensaas.core.impl.ops.monitor.SysTenantRenewalLog.EVENT_REVOKE,
                old.expiresAt() == null ? null : Instant.parse(old.expiresAt()), null, null);
    }

    @Transactional
    public void renewPlan(long tenantId, RenewPlanCommand cmd) {
        SysTenant tenant = support.requireTenantInScope(tenantId);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_TENANT, String.valueOf(tenantId), tenant.getName());

        List<TenantPlan> plans = tenant.getPlans() != null ? new ArrayList<>(tenant.getPlans()) : new ArrayList<>();
        int targetIdx = -1;
        for (int i = 0; i < plans.size(); i++) {
            TenantPlan p = plans.get(i);
            if (cmd.packageCode().equals(p.packageCode()) && p.revokedAt() == null) {
                targetIdx = i;
                break;
            }
        }
        if (targetIdx < 0) {
            throw new TenantOpsException(TenantOpsException.Code.PLAN_NOT_FOUND,
                    "未找到可续费的套餐包授权：" + cmd.packageCode());
        }

        TenantPlan old = plans.get(targetIdx);
        Instant now = Instant.now();
        // 已过期则从 now 起算；否则在原 expiresAt 基础上追加
        Instant baseInstant = old.expiresAt() != null ? Instant.parse(old.expiresAt()) : null;
        Instant newExpiry;
        if (baseInstant == null || !baseInstant.isAfter(now)) {
            // 永久包或已过期：从 now 起算新周期
            newExpiry = now.plusSeconds((long) cmd.ttlDays() * 86400L);
        } else {
            newExpiry = baseInstant.plusSeconds((long) cmd.ttlDays() * 86400L);
        }
        plans.set(targetIdx, new TenantPlan(old.packageCode(), old.grantedAt(), newExpiry.toString(), null));

        SysTenant update = new SysTenant();
        update.setId(tenantId);
        update.setPlans(plans);
        update.setUpdatedAt(LocalDateTime.now());
        update.setUpdatedBy(UserContext.require().userId());
        tenantMapper.updateById(update);
        tenantCacheEvictor.evictTenant(tenantId);

        recordRenewalLog(tenantId, cmd.packageCode(),
                com.wensaas.core.impl.ops.monitor.SysTenantRenewalLog.EVENT_RENEW,
                old.expiresAt() == null ? null : Instant.parse(old.expiresAt()), newExpiry, cmd.ttlDays());
    }

    @Transactional
    public void addSeatAddon(long tenantId, AddSeatAddonCommand cmd) {
        SysTenant tenant = support.requireTenantInScope(tenantId);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_TENANT, String.valueOf(tenantId), tenant.getName());
        long operatorId = UserContext.require().userId();

        SysTenantSeatAddon addon = new SysTenantSeatAddon();
        addon.setTenantId(tenantId);
        addon.setQuantity(cmd.quantity());
        addon.setRemark(cmd.remark());
        addon.setOperatorId(operatorId);
        seatAddonMapper.insert(addon);
    }
}
