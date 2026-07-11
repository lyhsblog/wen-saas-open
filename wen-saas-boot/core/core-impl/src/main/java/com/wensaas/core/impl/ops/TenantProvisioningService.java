package com.wensaas.core.impl.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.ops.TenantOpsException;
import com.wensaas.core.api.ops.TenantOpsService.CreateTenantCommand;
import com.wensaas.core.api.ops.TenantOpsService.CreatedTenantResult;
import com.wensaas.core.api.ops.TenantOpsService.IssuedResetLink;
import com.wensaas.core.api.ops.TenantOpsService.OpsUserItem;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.auth.password.PasswordResetTokenService;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.user.UserCommandService;
import com.wensaas.core.impl.permission.role.SysRole;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.role.SysUserRole;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.pkg.SysPackage;
import com.wensaas.core.impl.pkg.SysPackageMapper;
import com.wensaas.core.api.tenant.TenantPlan;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 运营租户「开通」协作类：开通新租户、重发密码重置链接、运营账号候选人查询。
 *
 * <p>由 {@code TenantOpsServiceImpl}（M18 God Class）拆出，方法体与原实现逐字等价。
 * 标 {@code @CrossTenantQuery}：开通时无租户上下文，绕过 MP TenantLine。
 */
@CrossTenantQuery("TenantProvisioningService: 运营开通，无租户上下文")
public class TenantProvisioningService {

    private final TenantOpsSupport support;
    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;
    private final UserCommandService userCommandService;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysTenantSeatAddonMapper seatAddonMapper;
    private final SysPackageMapper packageMapper;
    private final PasswordResetTokenService resetTokenService;
    private final PasswordHasher passwordHasher;
    private final com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore defaultScopeStore;
    private final com.wensaas.core.impl.tenant.TenantProperties tenantProperties;
    private final ActivationEmailSender activationEmailSender;

    public TenantProvisioningService(TenantOpsSupport support,
                                     SysTenantMapper tenantMapper,
                                     SysUserMapper userMapper,
                                     UserCommandService userCommandService,
                                     SysRoleMapper roleMapper,
                                     SysUserRoleMapper userRoleMapper,
                                     SysTenantSeatAddonMapper seatAddonMapper,
                                     SysPackageMapper packageMapper,
                                     PasswordResetTokenService resetTokenService,
                                     PasswordHasher passwordHasher,
                                     com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore defaultScopeStore,
                                     com.wensaas.core.impl.tenant.TenantProperties tenantProperties,
                                     ActivationEmailSender activationEmailSender) {
        this.support = support;
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.userCommandService = userCommandService;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.seatAddonMapper = seatAddonMapper;
        this.packageMapper = packageMapper;
        this.resetTokenService = resetTokenService;
        this.passwordHasher = passwordHasher;
        this.defaultScopeStore = defaultScopeStore;
        this.tenantProperties = tenantProperties;
        this.activationEmailSender = activationEmailSender;
    }

    @Transactional
    public CreatedTenantResult createTenant(CreateTenantCommand cmd) {
        long operatorId = UserContext.require().userId();
        LocalDateTime now = LocalDateTime.now();

        // 1. 唯一性校验（格式与长度由 DTO @Pattern 拦在前；短子域名前缀由 5-16 长度天然规避）
        if (tenantMapper.selectOne(Wrappers.<SysTenant>lambdaQuery()
                .eq(SysTenant::getCode, cmd.tenantCode())) != null) {
            throw new TenantOpsException(TenantOpsException.Code.DUPLICATE_TENANT_CODE,
                    "租户标识已存在：" + cmd.tenantCode());
        }
        if (userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, cmd.adminUsername())) != null) {
            throw new TenantOpsException(TenantOpsException.Code.DUPLICATE_ADMIN_USERNAME,
                    "管理员账号已存在：" + cmd.adminUsername());
        }

        // 2. 验证套餐（base 包必须存在且 active）
        String basePackageCode = "base";
        SysPackage basePackage = packageMapper.selectOne(Wrappers.<SysPackage>lambdaQuery()
                .eq(SysPackage::getCode, basePackageCode));
        if (basePackage == null) {
            throw new TenantOpsException(TenantOpsException.Code.PACKAGE_NOT_FOUND,
                    "base 套餐不存在，请先初始化套餐数据");
        }
        if (basePackage.getStatus() == null || basePackage.getStatus() != 1) {
            throw new TenantOpsException(TenantOpsException.Code.PACKAGE_NOT_ACTIVE,
                    "base 套餐未启用");
        }

        // 2b. 校验客户经理（非必填；指定时必须是有效运营账号 tenant_id=0 且 status=1）
        if (cmd.csmUserId() != null) {
            requireOpsUser(cmd.csmUserId());
        }

        // 3. 构建 plans（base 永久 + 业务包按 defaultTtlDays）
        List<TenantPlan> plans = buildPlans(basePackage, cmd.packageCodes(), now);

        // 4. 创建租户
        String tenantNo = generateTenantNo();
        SysTenant tenant = new SysTenant();
        tenant.setCode(cmd.tenantCode());
        tenant.setName(cmd.tenantName());
        tenant.setTenantNo(tenantNo);
        tenant.setRemark(cmd.tenantRemark());
        tenant.setStatus(1);
        tenant.setPlans(plans);
        tenant.setCsmUserId(cmd.csmUserId());
        tenant.setMemberCap(cmd.memberCap());
        tenant.setStorageCap(cmd.storageCap());
        tenant.setTimezone(cmd.timezone());
        tenant.setExpireAt(cmd.expireAt());
        tenant.setFullName(cmd.fullName());
        tenant.setUscc(cmd.uscc());
        tenant.setIndustry(cmd.industry());
        tenant.setScale(cmd.scale());
        tenant.setRegion(cmd.region());
        tenant.setTags(cmd.tags());
        tenant.setCreatedAt(now);
        tenant.setCreatedBy(operatorId);
        tenant.setUpdatedAt(now);
        tenant.setUpdatedBy(operatorId);
        tenantMapper.insert(tenant);
        Long tenantId = tenant.getId();

        // 5. 创建主管理员（status=0，未激活；password_hash 占位）
        String placeholderHash = passwordHasher.hash(UUID.randomUUID().toString());
        SysUser adminUser = new SysUser();
        adminUser.setTenantId(tenantId);
        adminUser.setUsername(cmd.adminUsername());
        adminUser.setPasswordHash(placeholderHash);
        adminUser.setRealName(cmd.adminRealName() != null ? cmd.adminRealName() : cmd.adminUsername());
        adminUser.setMobile(cmd.adminMobile());
        adminUser.setEmail(cmd.adminEmail());
        adminUser.setStatus(0);
        adminUser.setCreatedAt(now);
        adminUser.setCreatedBy(operatorId);
        adminUser.setUpdatedAt(now);
        adminUser.setUpdatedBy(operatorId);
        userCommandService.insert(adminUser);
        Long adminUserId = adminUser.getId();

        // 6. 回写 main_admin_user_id
        SysTenant updateTenant = new SysTenant();
        updateTenant.setId(tenantId);
        updateTenant.setMainAdminUserId(adminUserId);
        tenantMapper.updateById(updateTenant);

        // 7. 创建 3 个系统预置角色
        SysRole ownerRole = createRole(tenantId, "SYSTEM_OWNER", "主管理员", 1, now, operatorId);
        createRole(tenantId, "SYSTEM_ADMIN", "管理员", 1, now, operatorId);
        createRole(tenantId, "SYSTEM_MEMBER", "普通成员", 3, now, operatorId);

        // 8. 绑定主管理员到 SYSTEM_OWNER 角色
        SysUserRole userRole = new SysUserRole();
        userRole.setTenantId(tenantId);
        userRole.setUserId(adminUserId);
        userRole.setRoleId(ownerRole.getId());
        userRole.setCreatedAt(now);
        userRole.setCreatedBy(operatorId);
        userRoleMapper.insert(userRole);

        // 9. 加购席位记录（qty=0 也写入，表示"未加购"）
        SysTenantSeatAddon addon = new SysTenantSeatAddon();
        addon.setTenantId(tenantId);
        addon.setQuantity(cmd.seatAddonQty());
        addon.setRemark(cmd.seatAddonRemark());
        addon.setOperatorId(operatorId);
        seatAddonMapper.insert(addon);

        // 10. 签发密码重置 token
        PasswordResetTokenService.IssuedToken issued = resetTokenService.issue(
                adminUserId, tenantId, cmd.tenantCode(), cmd.adminUsername(), "init_admin");

        // 11. 事务提交后把激活链接直投主管理员邮箱（提交前发会因回滚而误发；页面仍展示链接兜底）
        String activationUrl = buildActivationUrl(cmd.tenantCode(), issued.token());
        long expireHours = Math.max(1,
                java.time.Duration.between(java.time.Instant.now(), issued.expiresAt()).toHours());
        String tenantName = cmd.tenantName();
        String adminUsername = cmd.adminUsername();
        String adminEmail = cmd.adminEmail();
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            activationEmailSender.sendActivationLink(tenantId, adminUserId, adminEmail,
                                    tenantName, adminUsername, activationUrl, (int) expireHours);
                        }
                    });
        } else {
            activationEmailSender.sendActivationLink(tenantId, adminUserId, adminEmail,
                    tenantName, adminUsername, activationUrl, (int) expireHours);
        }

        return new CreatedTenantResult(tenantId, cmd.tenantCode(), tenantNo,
                cmd.adminUsername(), issued.token(), issued.expiresAt());
    }

    public IssuedResetLink reissueResetLink(Long tenantId) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new TenantOpsException(TenantOpsException.Code.TENANT_NOT_FOUND,
                    "租户不存在：id=" + tenantId);
        }
        // 数据范围写守卫：OPS_OWNED 仅可为名下租户重发密码重置链接
        support.requireScope(tenant);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_TENANT, String.valueOf(tenantId), tenant.getName());
        if (tenant.getMainAdminUserId() == null) {
            throw new TenantOpsException(TenantOpsException.Code.NO_MAIN_ADMIN,
                    "租户尚未指定主管理员：tenantId=" + tenantId);
        }
        SysUser admin = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getId, tenant.getMainAdminUserId()));
        if (admin == null) {
            throw new TenantOpsException(TenantOpsException.Code.NO_MAIN_ADMIN,
                    "租户主管理员账号不存在或已删除：tenantId=" + tenantId);
        }

        PasswordResetTokenService.IssuedToken issued = resetTokenService.issue(
                admin.getId(), tenantId, tenant.getCode(), admin.getUsername(), "reset_admin");

        return new IssuedResetLink(issued.token(), issued.expiresAt(), admin.getUsername());
    }

    public List<OpsUserItem> listOpsUsers(String keyword) {
        LambdaQueryWrapper<SysUser> query = Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getTenantId, 0L)
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getUsername);
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword));
        }
        query.last("LIMIT 50");
        return userMapper.selectList(query).stream()
                .map(u -> new OpsUserItem(String.valueOf(u.getId()), u.getUsername(), u.getRealName()))
                .toList();
    }

    // ===================== 工具方法 =====================

    private SysRole createRole(Long tenantId, String code, String name, int dataScope,
                                LocalDateTime now, long operatorId) {
        SysRole role = new SysRole();
        role.setTenantId(tenantId);
        role.setCode(code);
        role.setName(name);
        role.setStatus(1);
        role.setCreatedAt(now);
        role.setCreatedBy(operatorId);
        role.setUpdatedAt(now);
        role.setUpdatedBy(operatorId);
        roleMapper.insert(role);
        // 角色默认范围落 sys_role_data_scope NULL-action 行（sys_role.data_scope 已退役）
        defaultScopeStore.put(tenantId, role.getId(),
                com.wensaas.core.impl.permission.datascope.DataScope.fromCode(dataScope));
        return role;
    }

    private List<TenantPlan> buildPlans(SysPackage basePackage, List<String> extraCodes,
                                        LocalDateTime now) {
        List<TenantPlan> plans = new ArrayList<>();
        java.time.OffsetDateTime grantedOdt = now.atOffset(ZoneOffset.of("+08:00"));
        String grantedAt = grantedOdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        // base 包：永久（expiresAt=null）
        plans.add(new TenantPlan(basePackage.getCode(), grantedAt, null, null));
        // 业务包：开通向导勾选的额外套餐，按各自 defaultTtlDays 计算到期（与 assignPlan 同口径）
        if (extraCodes != null) {
            Set<String> seen = new HashSet<>();
            seen.add(basePackage.getCode());
            for (String code : extraCodes) {
                if (code == null || code.isBlank() || !seen.add(code)) {
                    continue;
                }
                SysPackage pkg = packageMapper.selectOne(Wrappers.<SysPackage>lambdaQuery()
                        .eq(SysPackage::getCode, code));
                if (pkg == null) {
                    throw new TenantOpsException(TenantOpsException.Code.PACKAGE_NOT_FOUND,
                            "套餐包不存在：" + code);
                }
                if (pkg.getStatus() == null || pkg.getStatus() != 1) {
                    throw new TenantOpsException(TenantOpsException.Code.PACKAGE_NOT_ACTIVE,
                            "套餐包未启用：" + code);
                }
                String expiresAt = null;
                Integer ttlDays = pkg.getDefaultTtlDays();
                if (ttlDays != null && ttlDays > 0) {
                    expiresAt = grantedOdt.toInstant().plusSeconds((long) ttlDays * 86400L).toString();
                }
                plans.add(new TenantPlan(code, grantedAt, expiresAt, null));
            }
        }
        return plans;
    }

    /** 校验客户经理是有效运营账号（tenant_id=0 且 status=1）。 */
    private void requireOpsUser(Long userId) {
        SysUser u = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getTenantId, 0L));
        if (u == null || u.getStatus() == null || u.getStatus() != 1) {
            throw new TenantOpsException(TenantOpsException.Code.CSM_NOT_FOUND,
                    "客户经理不是有效运营账号：userId=" + userId);
        }
    }

    /**
     * 拼租户前端完整设密码链接：租户 code 作为子域前缀到裸域 origin（与 ops-web tenantWebUrl 同口径），
     * 形如 {@code https://{tenantCode}.example.com/reset-password/{token}}。
     */
    private String buildActivationUrl(String tenantCode, String token) {
        URI base = URI.create(tenantProperties.getWebBaseUrl());
        String port = base.getPort() > 0 ? ":" + base.getPort() : "";
        return base.getScheme() + "://" + tenantCode + "." + base.getHost() + port
                + "/reset-password/" + token;
    }

    private static String generateTenantNo() {
        // "T" + 8位随机十六进制（UUID前8位），全局唯一性极高
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
