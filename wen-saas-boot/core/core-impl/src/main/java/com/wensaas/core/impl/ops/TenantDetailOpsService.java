package com.wensaas.core.impl.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.audit.Sensitives;
import com.wensaas.core.api.auth.user.CapabilitiesService;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.ops.TenantOpsException;
import com.wensaas.core.api.ops.TenantOpsService.LoginTrendPoint;
import com.wensaas.core.api.ops.TenantOpsService.MainAdmin;
import com.wensaas.core.api.ops.TenantOpsService.SeatAddonHistoryItem;
import com.wensaas.core.api.ops.TenantOpsService.SeatAddonSummary;
import com.wensaas.core.api.ops.TenantOpsService.TenantAuditLogItem;
import com.wensaas.core.api.ops.TenantOpsService.TenantAuditLogPage;
import com.wensaas.core.api.ops.TenantOpsService.TenantBasic;
import com.wensaas.core.api.ops.TenantOpsService.TenantDetail;
import com.wensaas.core.api.ops.TenantOpsService.TenantEventItem;
import com.wensaas.core.api.ops.TenantOpsService.TenantEventPage;
import com.wensaas.core.api.ops.TenantOpsService.TenantListItem;
import com.wensaas.core.api.ops.TenantOpsService.TenantPage;
import com.wensaas.core.api.ops.TenantOpsService.UpdateTenantNameCommand;
import com.wensaas.core.api.ops.TenantOpsService.UsageStats;
import com.wensaas.core.api.ops.access.OpsAccessContext;
import com.wensaas.core.api.ops.access.OpsAccessContextResolver;
import com.wensaas.core.api.ops.access.OpsDataScope;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.audit.log.SysOperationLog;
import com.wensaas.core.impl.audit.log.SysOperationLogMapper;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.namesource.EntityNameResolver;
import com.wensaas.core.api.tenant.TenantPlan;
import com.wensaas.core.impl.pkg.TenantPlansResolver;
import com.wensaas.core.impl.pkg.TenantCacheEvictor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 运营租户「详情」协作类：列表 / 详情 / 使用情况 / 变更历史 / 操作日志（读视图），
 * 以及租户名 / 备注 / 状态等基本信息写操作。
 *
 * <p>由 {@code TenantOpsServiceImpl}（M18 God Class）拆出，方法体与原实现逐字等价。
 * 标 {@code @CrossTenantQuery}：运营操作无租户上下文，绕过 MP TenantLine。
 */
@CrossTenantQuery("TenantDetailOpsService: 运营详情/基本信息操作，无租户上下文")
public class TenantDetailOpsService {

    // 租户「套餐与账号」变更历史 + ops 租户动态白名单。
    // 注意：与 TenantOpsController 各端点 @Auditable 的中文 action 严格对齐
    // （审计 action 已统一为中文、无英文枚举，见 memory audit_action_chinese）。
    private static final Set<String> EVENT_ACTION_WHITELIST = Set.of(
            "开通租户", "暂停租户", "恢复租户", "修改名称", "编辑备注",
            "勾选套餐", "取消套餐", "续费套餐", "加购席位",
            "更换主管理员", "重发密码链接", "解除登录锁定"
    );

    private final TenantOpsSupport support;
    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;
    private final SysTenantSeatAddonMapper seatAddonMapper;
    private final SysOperationLogMapper operationLogMapper;
    private final TenantPlansResolver plansResolver;
    private final OpsAccessContextResolver accessResolver;
    private final EntityNameResolver entityNameResolver;
    private final TenantCacheEvictor tenantCacheEvictor;

    public TenantDetailOpsService(TenantOpsSupport support,
                                  SysTenantMapper tenantMapper,
                                  SysUserMapper userMapper,
                                  SysTenantSeatAddonMapper seatAddonMapper,
                                  SysOperationLogMapper operationLogMapper,
                                  TenantPlansResolver plansResolver,
                                  OpsAccessContextResolver accessResolver,
                                  EntityNameResolver entityNameResolver,
                                  TenantCacheEvictor tenantCacheEvictor) {
        this.support = support;
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.seatAddonMapper = seatAddonMapper;
        this.operationLogMapper = operationLogMapper;
        this.plansResolver = plansResolver;
        this.accessResolver = accessResolver;
        this.entityNameResolver = entityNameResolver;
        this.tenantCacheEvictor = tenantCacheEvictor;
    }

    public TenantPage listTenants(int page, int pageSize, String keyword,
                                   Integer status, Integer expiringWithinDays, Boolean hasSeatAddon,
                                   List<String> packageCodes, String sort) {
        LambdaQueryWrapper<SysTenant> query = Wrappers.<SysTenant>lambdaQuery();
        // 运营数据范围：OWNED（客户经理）仅可见 csm_user_id=self 的租户；ALL（超管）不限
        OpsAccessContext ctx = accessResolver.require();
        if (ctx.dataScope() == OpsDataScope.OPS_OWNED) {
            query.eq(SysTenant::getCsmUserId, ctx.userId());
        }
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(SysTenant::getName, keyword)
                    .or().like(SysTenant::getCode, keyword)
                    .or().like(SysTenant::getTenantNo, keyword));
        }
        if (status != null) {
            query.eq(SysTenant::getStatus, status);
        }
        if (expiringWithinDays != null && expiringWithinDays > 0) {
            query.apply("EXISTS (SELECT 1 FROM jsonb_array_elements(plans) AS elem"
                    + " WHERE (elem->>'revokedAt') IS NULL"
                    + " AND (elem->>'expiresAt') IS NOT NULL"
                    + " AND (elem->>'expiresAt')::timestamptz BETWEEN now() AND now() + make_interval(days => {0}))",
                    expiringWithinDays);
        }
        if (Boolean.TRUE.equals(hasSeatAddon)) {
            query.apply("EXISTS (SELECT 1 FROM sys_tenant_seat_addon sta"
                    + " WHERE sta.tenant_id = id AND sta.quantity > 0)");
        } else if (Boolean.FALSE.equals(hasSeatAddon)) {
            query.apply("NOT EXISTS (SELECT 1 FROM sys_tenant_seat_addon sta"
                    + " WHERE sta.tenant_id = id AND sta.quantity > 0)");
        }
        List<String> filterCodes = packageCodes == null ? List.of()
                : packageCodes.stream().filter(c -> c != null && !c.isBlank()).distinct().toList();
        if (!filterCodes.isEmpty()) {
            // 逐个 OR EXISTS，每个值独立参数化（防注入）
            query.and(w -> {
                for (int i = 0; i < filterCodes.size(); i++) {
                    if (i > 0) {
                        w.or();
                    }
                    w.apply("EXISTS (SELECT 1 FROM jsonb_array_elements(plans) AS pe"
                            + " WHERE pe->>'revokedAt' IS NULL AND pe->>'packageCode' = {0})",
                            filterCodes.get(i));
                }
            });
        }
        // 先算 total（仅 WHERE，无 ORDER BY，避开 MP count 优化器无法解析派生排序子查询的问题），
        // 再用手写 ORDER BY + LIMIT/OFFSET 取当前页，绕过分页拦截器对 SQL 的二次解析。
        long total = tenantMapper.selectCount(query);
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safeSize;
        query.last(buildOrderByClause(sort) + " LIMIT " + safeSize + " OFFSET " + offset);
        List<SysTenant> rows = tenantMapper.selectList(query);

        // 一次查所有主管理员 + 客户经理 user，避免 N+1
        List<Long> userIds = rows.stream()
                .flatMap(t -> java.util.stream.Stream.of(t.getMainAdminUserId(), t.getCsmUserId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SysUser> userById = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser u : userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                    .in(SysUser::getId, userIds))) {
                userById.put(u.getId(), u);
            }
        }

        List<Long> tenantIds = rows.stream().map(SysTenant::getId).toList();
        // 一次查每个租户的最后活跃时间 = MAX(成员 last_login_at)
        Map<Long, Instant> lastActiveByTenant = loadLastActiveByTenant(tenantIds);
        // 一次查每个租户的累计加购席位
        Map<Long, Integer> seatTotalByTenant = loadSeatAddonTotalByTenant(tenantIds);

        List<TenantListItem> items = rows.stream().map(t -> {
            SysUser admin = t.getMainAdminUserId() != null ? userById.get(t.getMainAdminUserId()) : null;
            SysUser csm = t.getCsmUserId() != null ? userById.get(t.getCsmUserId()) : null;
            String activationStatus = deriveActivationStatus(t, admin);
            Instant createdAt = t.getCreatedAt() != null
                    ? t.getCreatedAt().toInstant(ZoneOffset.of("+08:00")) : null;
            List<TenantPlan> active = activePlans(t.getPlans());
            return new TenantListItem(
                    String.valueOf(t.getId()), t.getCode(), t.getName(), t.getTenantNo(),
                    admin != null ? admin.getUsername() : null,
                    t.getCsmUserId() != null ? String.valueOf(t.getCsmUserId()) : null,
                    csm != null ? TenantOpsSupport.csmDisplayName(csm) : null,
                    t.getStatus(), activationStatus, createdAt,
                    lastActiveByTenant.get(t.getId()),
                    active.size(),
                    active.stream().map(TenantPlan::packageCode).toList(),
                    nearestExpireAt(active),
                    seatTotalByTenant.getOrDefault(t.getId(), 0));
        }).toList();

        return new TenantPage(items, total, safePage, safeSize);
    }

    public TenantDetail getTenantDetail(Long tenantId) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new TenantOpsException(TenantOpsException.Code.TENANT_NOT_FOUND,
                    "租户不存在：id=" + tenantId);
        }
        // 运营数据范围：OWNED（客户经理）越权访问非名下租户详情 → 当作不存在（不泄露存在性，亦不返数据）
        OpsAccessContext ctx = accessResolver.require();
        if (ctx.dataScope() == OpsDataScope.OPS_OWNED
                && (tenant.getCsmUserId() == null || tenant.getCsmUserId() != ctx.userId())) {
            throw new TenantOpsException(TenantOpsException.Code.TENANT_NOT_FOUND,
                    "租户不存在：id=" + tenantId);
        }

        SysUser admin = tenant.getMainAdminUserId() != null
                ? userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getId, tenant.getMainAdminUserId()))
                : null;
        SysUser csm = tenant.getCsmUserId() != null
                ? userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getId, tenant.getCsmUserId()))
                : null;
        Instant lastActiveAt = loadLastActiveByTenant(List.of(tenant.getId())).get(tenant.getId());

        TenantBasic basic = new TenantBasic(
                String.valueOf(tenant.getId()),
                tenant.getCode(),
                tenant.getName(),
                tenant.getTenantNo(),
                tenant.getRemark(),
                tenant.getCsmUserId() != null ? String.valueOf(tenant.getCsmUserId()) : null,
                csm != null ? TenantOpsSupport.csmDisplayName(csm) : null,
                tenant.getMemberCap(),
                tenant.getStorageCap(),
                tenant.getExpireAt(),
                tenant.getTimezone(),
                tenant.getFullName(),
                tenant.getUscc(),
                tenant.getIndustry(),
                tenant.getScale(),
                tenant.getRegion(),
                tenant.getTags(),
                tenant.getStatus(),
                deriveActivationStatus(tenant, admin),
                tenant.getCreatedAt() != null
                        ? tenant.getCreatedAt().toInstant(ZoneOffset.of("+08:00")) : null,
                lastActiveAt
        );

        MainAdmin mainAdmin = admin != null
                ? new MainAdmin(String.valueOf(admin.getId()), admin.getUsername(),
                        admin.getRealName(), Sensitives.maskPhone(admin.getMobile()),
                        Sensitives.maskEmail(admin.getEmail()), admin.getStatus())
                : null;

        List<CapabilitiesService.PackageView> plans = plansResolver.resolve(tenantId).packageViews();

        List<SysTenantSeatAddon> addons = seatAddonMapper.selectList(
                Wrappers.<SysTenantSeatAddon>lambdaQuery()
                        .eq(SysTenantSeatAddon::getTenantId, tenantId)
                        .orderByDesc(SysTenantSeatAddon::getCreatedAt));
        int total = addons.stream()
                .mapToInt(a -> a.getQuantity() != null ? a.getQuantity() : 0)
                .sum();
        // 操作人 id → 展示名（运营平台用户，本类 @CrossTenantQuery 已绕过租户线，可直接批量查）
        Map<Long, String> operatorNames = resolveOperatorNames(
                addons.stream().map(SysTenantSeatAddon::getOperatorId).toList());
        List<SeatAddonHistoryItem> history = addons.stream()
                .map(a -> new SeatAddonHistoryItem(
                        String.valueOf(a.getId()),
                        a.getQuantity() != null ? a.getQuantity() : 0,
                        a.getRemark(),
                        a.getOperatorId() != null ? String.valueOf(a.getOperatorId()) : null,
                        a.getOperatorId() != null ? operatorNames.get(a.getOperatorId()) : null,
                        a.getCreatedAt()))
                .toList();
        SeatAddonSummary seatAddons = new SeatAddonSummary(total, history);

        return new TenantDetail(basic, mainAdmin, plans, seatAddons);
    }

    public void updateStatus(long tenantId, int newStatus, Long operatorUserId) {
        if (newStatus != 0 && newStatus != 1 && newStatus != 2) {
            throw new TenantOpsException(TenantOpsException.Code.INVALID_STATUS,
                    "非法 status 值：" + newStatus + "（允许 0=暂停 / 1=正常 / 2=已过期）");
        }
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new TenantOpsException(TenantOpsException.Code.TENANT_NOT_FOUND,
                    "租户不存在：id=" + tenantId);
        }
        // 数据范围写守卫：OPS_OWNED 仅可改名下租户状态
        support.requireScope(tenant);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图；暂停含 reason 描述时不冲突）
        AuditObjects.add(EntityTypes.SYS_TENANT, String.valueOf(tenantId), tenant.getName());
        SysTenant update = new SysTenant();
        update.setId(tenantId);
        update.setStatus(newStatus);
        if (operatorUserId != null) {
            update.setUpdatedBy(operatorUserId);
            update.setUpdatedAt(LocalDateTime.now());
        }
        tenantMapper.updateById(update);
        tenantCacheEvictor.evictTenant(tenantId);
    }

    @Transactional
    public void updateTenantName(long tenantId, UpdateTenantNameCommand cmd) {
        SysTenant tenant = support.requireTenantInScope(tenantId);
        // ② 审计对象名：加载后即登记「旧名/新名」（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_TENANT, String.valueOf(tenantId), tenant.getName(), cmd.newName());
        SysTenant update = new SysTenant();
        update.setId(tenantId);
        update.setName(cmd.newName());
        update.setUpdatedAt(LocalDateTime.now());
        update.setUpdatedBy(UserContext.require().userId());
        tenantMapper.updateById(update);
    }

    @Transactional
    public void updateTenantRemark(long tenantId, String remark) {
        SysTenant tenant = support.requireTenantInScope(tenantId);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_TENANT, String.valueOf(tenantId), tenant.getName());
        SysTenant update = new SysTenant();
        update.setId(tenantId);
        update.setRemark(remark);
        update.setUpdatedAt(LocalDateTime.now());
        update.setUpdatedBy(UserContext.require().userId());
        tenantMapper.updateById(update);
    }

    public TenantEventPage listTenantEvents(long tenantId, int page, int pageSize) {
        support.requireTenantInScope(tenantId);
        IPage<SysOperationLog> result = operationLogMapper.selectPage(
                new Page<>(page, pageSize),
                Wrappers.<SysOperationLog>lambdaQuery()
                        .eq(SysOperationLog::getTargetTenantId, tenantId)
                        .eq(SysOperationLog::getModule, "__core__")
                        .in(SysOperationLog::getAction, EVENT_ACTION_WHITELIST)
                        .orderByDesc(SysOperationLog::getCreatedAt));
        List<TenantEventItem> items = result.getRecords().stream()
                .map(log -> new TenantEventItem(
                        String.valueOf(log.getId()),
                        log.getAction(),
                        log.getObjectType(),
                        entityNameResolver.displayLabel(log.getObjectType()),
                        log.getObjectId(),
                        log.getObjectDesc(),
                        log.getOperatorId() != null ? String.valueOf(log.getOperatorId()) : null,
                        log.getOperatorName(),
                        log.getCreatedAt() != null ? log.getCreatedAt().toInstant(ZoneOffset.of("+08:00")) : null))
                .toList();
        return new TenantEventPage(items, result.getTotal(), page, pageSize);
    }

    public TenantAuditLogPage listTenantAuditLogs(long tenantId, int page, int pageSize) {
        support.requireTenantInScope(tenantId);
        IPage<SysOperationLog> result = operationLogMapper.selectPage(
                new Page<>(page, pageSize),
                Wrappers.<SysOperationLog>lambdaQuery()
                        .eq(SysOperationLog::getTargetTenantId, tenantId)
                        .orderByDesc(SysOperationLog::getCreatedAt));
        List<TenantAuditLogItem> items = result.getRecords().stream()
                .map(log -> new TenantAuditLogItem(
                        String.valueOf(log.getId()),
                        log.getAction(),
                        log.getObjectType(),
                        entityNameResolver.displayLabel(log.getObjectType()),
                        log.getObjectId(),
                        log.getObjectDesc(),
                        log.getOperatorId() != null ? String.valueOf(log.getOperatorId()) : null,
                        log.getOperatorName(),
                        log.getRequestId(),
                        log.getStatusCode(),
                        log.getCreatedAt() != null ? log.getCreatedAt().toInstant(ZoneOffset.of("+08:00")) : null))
                .toList();
        return new TenantAuditLogPage(items, result.getTotal(), page, pageSize);
    }

    public UsageStats getTenantUsage(long tenantId) {
        support.requireTenantInScope(tenantId);
        long memberCount = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getTenantId, tenantId));
        long activeMembers30d = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getTenantId, tenantId)
                .apply("last_login_at >= now() - interval '30 day'"));
        // 近 30 天每日登录次数（来自 sys_operation_log action=login，按北京时区分日）
        List<Map<String, Object>> rows = operationLogMapper.selectMaps(Wrappers.<SysOperationLog>query()
                .select("to_char(created_at AT TIME ZONE 'Asia/Shanghai', 'YYYY-MM-DD') AS d", "count(*) AS c")
                .eq("tenant_id", tenantId)
                .eq("action", "login")
                .apply("created_at >= now() - interval '30 day'")
                .groupBy("d")
                .orderByAsc("d"));
        List<LoginTrendPoint> trend = rows.stream()
                .map(r -> new LoginTrendPoint(
                        String.valueOf(r.get("d")),
                        ((Number) r.get("c")).longValue()))
                .toList();
        return new UsageStats(memberCount, activeMembers30d, trend);
    }

    // ===================== 工具方法 =====================

    private static String deriveActivationStatus(SysTenant tenant, SysUser mainAdmin) {
        Integer ts = tenant.getStatus();
        if (ts != null && ts == 0) {
            return "paused";
        }
        if (ts != null && ts == 2) {
            return "expired";
        }
        if (mainAdmin == null) {
            return "unknown";
        }
        Integer us = mainAdmin.getStatus();
        if (us != null && us == 1) {
            return "activated";
        }
        return "pending_activation";
    }

    /** 批量取每个租户的最后活跃时间 = MAX(成员 last_login_at)。 */
    private Map<Long, Instant> loadLastActiveByTenant(List<Long> tenantIds) {
        Map<Long, Instant> result = new HashMap<>();
        if (tenantIds == null || tenantIds.isEmpty()) {
            return result;
        }
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getTenantId, SysUser::getLastLoginAt)
                .in(SysUser::getTenantId, tenantIds)
                .isNotNull(SysUser::getLastLoginAt));
        for (SysUser u : users) {
            if (u.getTenantId() == null || u.getLastLoginAt() == null) {
                continue;
            }
            Instant cur = u.getLastLoginAt().toInstant(ZoneOffset.of("+08:00"));
            result.merge(u.getTenantId(), cur, (a, b) -> a.isAfter(b) ? a : b);
        }
        return result;
    }

    /** 批量解析操作人 id → 展示名（真实姓名优先、回退账号）；已删除 / 未命中的 id 不出现在结果中。 */
    private Map<Long, String> resolveOperatorNames(Collection<Long> operatorIds) {
        List<Long> ids = operatorIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (SysUser u : userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .in(SysUser::getId, ids))) {
            result.put(u.getId(), TenantOpsSupport.csmDisplayName(u));
        }
        return result;
    }

    /** 批量取每个租户的累计加购席位（sum quantity）。 */
    private Map<Long, Integer> loadSeatAddonTotalByTenant(List<Long> tenantIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (tenantIds == null || tenantIds.isEmpty()) {
            return result;
        }
        for (SysTenantSeatAddon a : seatAddonMapper.selectList(Wrappers.<SysTenantSeatAddon>lambdaQuery()
                .in(SysTenantSeatAddon::getTenantId, tenantIds))) {
            if (a.getTenantId() == null) {
                continue;
            }
            result.merge(a.getTenantId(), a.getQuantity() != null ? a.getQuantity() : 0, Integer::sum);
        }
        return result;
    }

    /** 取未撤销（active）套餐列表。 */
    private static List<TenantPlan> activePlans(List<TenantPlan> plans) {
        if (plans == null) {
            return List.of();
        }
        return plans.stream().filter(p -> p.revokedAt() == null).toList();
    }

    /** active 套餐中最早的 expiresAt（null=都永久或无套餐）。 */
    private static Instant nearestExpireAt(List<TenantPlan> activePlans) {
        Instant min = null;
        for (TenantPlan p : activePlans) {
            if (p.expiresAt() == null) {
                continue;
            }
            Instant e = Instant.parse(p.expiresAt());
            if (min == null || e.isBefore(min)) {
                min = e;
            }
        }
        return min;
    }

    /**
     * 列表排序 ORDER BY 片段。sort 形如 {@code field:dir}；field 仅允许
     * lastActiveAt / createdAt / nearestExpireAt，dir 仅 asc/desc，非法值回落默认，杜绝注入。
     */
    private static String buildOrderByClause(String sort) {
        String field = "lastActiveAt";
        String dir = null;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(":", 2);
            field = parts[0].trim();
            if (parts.length > 1) {
                dir = parts[1].trim().toLowerCase();
            }
        }
        return switch (field) {
            case "createdAt" -> "ORDER BY sys_tenant.created_at " + dir(dir, "DESC") + ", sys_tenant.id DESC";
            case "nearestExpireAt" -> "ORDER BY (SELECT MIN((elem->>'expiresAt')::timestamptz)"
                    + " FROM jsonb_array_elements(sys_tenant.plans) elem"
                    + " WHERE elem->>'revokedAt' IS NULL AND elem->>'expiresAt' IS NOT NULL) "
                    + dir(dir, "ASC") + " NULLS LAST, sys_tenant.id DESC";
            default -> "ORDER BY (SELECT MAX(u.last_login_at) FROM sys_user u"
                    + " WHERE u.tenant_id = sys_tenant.id AND u.deleted_at IS NULL) "
                    + dir(dir, "DESC") + " NULLS LAST, sys_tenant.id DESC";
        };
    }

    private static String dir(String dir, String def) {
        if ("asc".equals(dir)) return "ASC";
        if ("desc".equals(dir)) return "DESC";
        return def;
    }
}
