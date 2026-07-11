package com.wensaas.core.impl.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.audit.Sensitives;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.ops.TenantOpsException;
import com.wensaas.core.api.ops.TenantOpsService.ContactReveal;
import com.wensaas.core.api.ops.TenantOpsService.TenantMemberItem;
import com.wensaas.core.api.ops.TenantOpsService.TenantMemberPage;
import com.wensaas.core.api.ops.TenantOpsService.TransferMainAdminCommand;
import com.wensaas.core.api.ops.TenantOpsService.TransferMainAdminResult;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.auth.login.LoginAttemptGuard;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 运营租户「成员 / 主管理员」协作类：成员分页、解密联系方式、解锁登录、更换主管理员。
 *
 * <p>由 {@code TenantOpsServiceImpl}（M18 God Class）拆出，方法体与原实现逐字等价。
 * 标 {@code @CrossTenantQuery}：运营操作无租户上下文，绕过 MP TenantLine。
 */
@CrossTenantQuery("TenantMemberOpsService: 运营成员/主管理员操作，无租户上下文")
public class TenantMemberOpsService {

    private final TenantOpsSupport support;
    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;
    private final UserCommandService userCommandService;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordHasher passwordHasher;
    private final PasswordResetTokenService resetTokenService;
    private final LoginAttemptGuard loginAttemptGuard;

    public TenantMemberOpsService(TenantOpsSupport support,
                                  SysTenantMapper tenantMapper,
                                  SysUserMapper userMapper,
                                  UserCommandService userCommandService,
                                  SysRoleMapper roleMapper,
                                  SysUserRoleMapper userRoleMapper,
                                  PasswordHasher passwordHasher,
                                  PasswordResetTokenService resetTokenService,
                                  LoginAttemptGuard loginAttemptGuard) {
        this.support = support;
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.userCommandService = userCommandService;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordHasher = passwordHasher;
        this.resetTokenService = resetTokenService;
        this.loginAttemptGuard = loginAttemptGuard;
    }

    public void unlockLoginAttempt(long tenantId, long userId) {
        // 数据范围写守卫：OPS_OWNED 仅可解锁名下租户的成员
        support.requireScope(tenantId);
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Long.valueOf(tenantId).equals(user.getTenantId())) {
            throw new TenantOpsException(TenantOpsException.Code.MEMBER_NOT_FOUND, "成员不存在");
        }
        // ② 审计对象名：加载后即登记当前姓名（realName 缺失回退账号；业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(userId), TenantOpsSupport.csmDisplayName(user));
        loginAttemptGuard.unlock(tenantId, user.getUsername());
    }

    public ContactReveal revealMemberContact(long tenantId, long userId) {
        // 数据范围守卫：明文联系方式属敏感读，OPS_OWNED 仅可查看名下租户成员
        support.requireScope(tenantId);
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Long.valueOf(tenantId).equals(user.getTenantId())) {
            throw new TenantOpsException(TenantOpsException.Code.MEMBER_NOT_FOUND, "成员不存在");
        }
        // ② 审计对象名：加载后即登记当前姓名（realName 缺失回退账号；业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(userId), TenantOpsSupport.csmDisplayName(user));
        return new ContactReveal(user.getMobile(), user.getEmail());
    }

    @Transactional
    public TransferMainAdminResult transferMainAdmin(long tenantId, TransferMainAdminCommand cmd) {
        SysTenant tenant = support.requireTenantInScope(tenantId);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_TENANT, String.valueOf(tenantId), tenant.getName());
        long operatorId = UserContext.require().userId();
        LocalDateTime now = LocalDateTime.now();

        Long currentAdminId = tenant.getMainAdminUserId();

        SysRole ownerRole = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getTenantId, tenantId)
                .eq(SysRole::getCode, "SYSTEM_OWNER")
                .isNull(SysRole::getDeletedAt));

        Long newAdminUserId;
        String newAdminUsername;
        String resetToken = null;
        Instant resetExpiresAt = null;

        if ("existing".equals(cmd.mode())) {
            long existingUserId = Long.parseLong(cmd.userId());
            if (currentAdminId != null && currentAdminId == existingUserId) {
                throw new TenantOpsException(TenantOpsException.Code.MAIN_ADMIN_SAME_AS_CURRENT,
                        "指定的成员已是主管理员：userId=" + existingUserId);
            }
            SysUser newAdmin = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getId, existingUserId)
                    .eq(SysUser::getTenantId, tenantId));
            if (newAdmin == null || newAdmin.getStatus() != 1) {
                throw new TenantOpsException(TenantOpsException.Code.MEMBER_NOT_FOUND,
                        "成员不存在或已停用：userId=" + existingUserId);
            }
            newAdminUserId = existingUserId;
            newAdminUsername = newAdmin.getUsername();
        } else {
            // mode=new：创建新账号。姓名必填（与开通租户、加成员口径一致）。
            if (cmd.realName() == null || cmd.realName().isBlank()) {
                throw new TenantOpsException(TenantOpsException.Code.NEW_ADMIN_NAME_REQUIRED,
                        "请输入新主管理员姓名");
            }
            if (userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getUsername, cmd.username())) != null) {
                throw new TenantOpsException(TenantOpsException.Code.DUPLICATE_ADMIN_USERNAME,
                        "账号已存在：" + cmd.username());
            }
            SysUser newAdmin = new SysUser();
            newAdmin.setTenantId(tenantId);
            newAdmin.setUsername(cmd.username());
            newAdmin.setPasswordHash(passwordHasher.hash(UUID.randomUUID().toString()));
            newAdmin.setRealName(cmd.realName());
            newAdmin.setMobile(cmd.mobile());
            newAdmin.setEmail(cmd.email());
            newAdmin.setStatus(0);
            newAdmin.setCreatedAt(now);
            newAdmin.setCreatedBy(operatorId);
            newAdmin.setUpdatedAt(now);
            newAdmin.setUpdatedBy(operatorId);
            userCommandService.insert(newAdmin);
            newAdminUserId = newAdmin.getId();
            newAdminUsername = cmd.username();

            PasswordResetTokenService.IssuedToken issued = resetTokenService.issue(
                    newAdminUserId, tenantId, tenant.getCode(), cmd.username(), "init_admin");
            resetToken = issued.token();
            resetExpiresAt = issued.expiresAt();
        }

        // 处理旧主管理员
        if (currentAdminId != null) {
            handleOldAdmin(currentAdminId, tenantId, cmd.oldAdminHandling(), ownerRole, operatorId, now);
        }

        // 给新主管理员绑 SYSTEM_OWNER
        if (ownerRole != null) {
            SysUserRole newBinding = new SysUserRole();
            newBinding.setTenantId(tenantId);
            newBinding.setUserId(newAdminUserId);
            newBinding.setRoleId(ownerRole.getId());
            newBinding.setCreatedAt(now);
            newBinding.setCreatedBy(operatorId);
            userRoleMapper.insert(newBinding);
        }

        // 更新 sys_tenant.main_admin_user_id
        SysTenant update = new SysTenant();
        update.setId(tenantId);
        update.setMainAdminUserId(newAdminUserId);
        update.setUpdatedAt(now);
        update.setUpdatedBy(operatorId);
        tenantMapper.updateById(update);

        return new TransferMainAdminResult(String.valueOf(newAdminUserId), newAdminUsername,
                resetToken, resetExpiresAt);
    }

    private void handleOldAdmin(Long oldAdminId, long tenantId, String handling,
                                  SysRole ownerRole, long operatorId, LocalDateTime now) {
        // 移除旧 SYSTEM_OWNER 绑定
        if (ownerRole != null) {
            userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                    .eq(SysUserRole::getTenantId, tenantId)
                    .eq(SysUserRole::getUserId, oldAdminId)
                    .eq(SysUserRole::getRoleId, ownerRole.getId()));
        }

        if ("disable".equals(handling)) {
            SysUser disableUpdate = new SysUser();
            disableUpdate.setId(oldAdminId);
            disableUpdate.setStatus(0);
            disableUpdate.setUpdatedAt(now);
            disableUpdate.setUpdatedBy(operatorId);
            userCommandService.updateById(disableUpdate);
        } else {
            // demote_admin → SYSTEM_ADMIN, demote_member → SYSTEM_MEMBER
            String targetRoleCode = "demote_member".equals(handling) ? "SYSTEM_MEMBER" : "SYSTEM_ADMIN";
            SysRole targetRole = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                    .eq(SysRole::getTenantId, tenantId)
                    .eq(SysRole::getCode, targetRoleCode)
                    .isNull(SysRole::getDeletedAt));
            if (targetRole != null) {
                // 检查是否已有绑定，没有则 insert
                boolean already = userRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getTenantId, tenantId)
                        .eq(SysUserRole::getUserId, oldAdminId)
                        .eq(SysUserRole::getRoleId, targetRole.getId())) > 0;
                if (!already) {
                    SysUserRole binding = new SysUserRole();
                    binding.setTenantId(tenantId);
                    binding.setUserId(oldAdminId);
                    binding.setRoleId(targetRole.getId());
                    binding.setCreatedAt(now);
                    binding.setCreatedBy(operatorId);
                    userRoleMapper.insert(binding);
                }
            }
        }
    }

    public TenantMemberPage listTenantMembers(long tenantId, String keyword, int page, int pageSize) {
        SysTenant tenant = support.requireTenantInScope(tenantId);
        Long mainAdminId = tenant.getMainAdminUserId();

        LambdaQueryWrapper<SysUser> query = Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getTenantId, tenantId)
                .orderByDesc(SysUser::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword));
        }

        IPage<SysUser> pageResult = userMapper.selectPage(new Page<>(page, pageSize), query);
        List<TenantMemberItem> items = pageResult.getRecords().stream()
                .map(u -> new TenantMemberItem(
                        String.valueOf(u.getId()),
                        u.getUsername(),
                        u.getRealName(),
                        Sensitives.maskPhone(u.getMobile()),
                        Sensitives.maskEmail(u.getEmail()),
                        u.getStatus(),
                        mainAdminId != null && mainAdminId.equals(u.getId()),
                        u.getLastLoginAt() != null
                                ? u.getLastLoginAt().toInstant(ZoneOffset.of("+08:00")) : null))
                .toList();
        return new TenantMemberPage(items, pageResult.getTotal(), page, pageSize);
    }
}
