package com.wensaas.core.impl.ops.team;

import com.wensaas.core.api.namesource.EntityTypes;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.audit.Sensitives;
import com.wensaas.core.api.auth.oauth.OAuthBindingService;
import com.wensaas.core.api.auth.permission.UserRoleQueryService;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.ops.team.OpsTeamAdminException;
import com.wensaas.core.api.ops.team.OpsTeamMemberAdminService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.auth.password.PasswordStrengthValidator;
import com.wensaas.core.impl.auth.password.SecurityPolicySettings;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.user.UserCommandService;
import com.wensaas.core.impl.auth.oauth.SysUserOAuthBinding;
import com.wensaas.core.impl.auth.oauth.SysUserOAuthBindingMapper;
import com.wensaas.core.impl.permission.role.SysRole;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.role.SysUserRole;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.security.SensitiveFieldCipher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运营团队成员管理实现。
 *
 * <p>所有 DB 操作显式按 {@code tenant_id=0} 过滤，且整段绕过 MP TenantLineInnerInterceptor
 * （运营账号无环境租户上下文）。
 *
 * <p>超管保护：目标成员持有 OPS_SUPER_ADMIN 角色时，编辑 / 重置密码 / 停启用 / 解绑 OAuth
 * 要求操作者本人也持有 OPS_SUPER_ADMIN；给成员授予 OPS_SUPER_ADMIN 角色（新建 / 编辑带
 * roleIds）同样仅限超管，防止普通运营账号借成员管理权限接管或自提为超管。
 */
public class OpsTeamMemberAdminServiceImpl implements OpsTeamMemberAdminService {

    private static final long OPS_TENANT = TenantContext.PLATFORM_TENANT_ID;
    private static final String SUPER_ADMIN_ROLE_CODE = "OPS_SUPER_ADMIN";
    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();

    private final SysUserMapper userMapper;
    private final UserCommandService userCommandService;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordHasher passwordHasher;
    private final SessionStore sessionStore;
    private final SecurityPolicySettings securityPolicy;
    private final SensitiveFieldCipher sensitiveFieldCipher;
    private final SysUserOAuthBindingMapper oauthBindingMapper;
    private final UserRoleQueryService userRoleQueryService;

    public OpsTeamMemberAdminServiceImpl(SysUserMapper userMapper,
                                         UserCommandService userCommandService,
                                         SysUserRoleMapper userRoleMapper,
                                         SysRoleMapper roleMapper,
                                         PasswordHasher passwordHasher,
                                         SessionStore sessionStore,
                                         SecurityPolicySettings securityPolicy,
                                         SensitiveFieldCipher sensitiveFieldCipher,
                                         SysUserOAuthBindingMapper oauthBindingMapper,
                                         UserRoleQueryService userRoleQueryService) {
        this.userMapper = userMapper;
        this.userCommandService = userCommandService;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordHasher = passwordHasher;
        this.sessionStore = sessionStore;
        this.securityPolicy = securityPolicy;
        this.sensitiveFieldCipher = sensitiveFieldCipher;
        this.oauthBindingMapper = oauthBindingMapper;
        this.userRoleQueryService = userRoleQueryService;
    }

    @Override
    public List<MemberListItem> listMembers(String keyword, Integer status) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getTenantId, OPS_TENANT)
                    .orderByDesc(SysUser::getCreatedAt);
            if (status != null) q.eq(SysUser::getStatus, status);
            if (keyword != null && !keyword.isBlank()) {
                String trimmed = keyword.trim();
                String like = "%" + trimmed + "%";
                // mobile/email 落库加密（确定性 AES），密文无法子串 LIKE：
                // 用户名 / 姓名仍按子串模糊，手机号 / 邮箱改为「整串等值」匹配（明文加密后比对密文）。
                String enc = sensitiveFieldCipher.encrypt(trimmed);
                q.and(w -> w.like(SysUser::getUsername, like)
                        .or().like(SysUser::getRealName, like)
                        .or().apply("email = {0}", enc)
                        .or().apply("mobile = {0}", enc));
            }
            List<SysUser> users = userMapper.selectList(q);
            if (users.isEmpty()) return List.of();
            List<Long> ids = users.stream().map(SysUser::getId).toList();
            Map<Long, List<String>> codesByUser = userRoleCodes(ids);
            List<MemberListItem> out = new ArrayList<>(users.size());
            for (SysUser u : users) {
                out.add(new MemberListItem(
                        u.getId(), u.getUsername(), u.getRealName(),
                        Sensitives.maskEmail(u.getEmail()), Sensitives.maskPhone(u.getMobile()),
                        u.getStatus() == null ? 0 : u.getStatus(),
                        codesByUser.getOrDefault(u.getId(), List.of()),
                        u.getLastLoginAt(), u.getCreatedAt()));
            }
            return out;
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public MemberDetail getMember(long userId) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysUser u = requireUser(userId);
            return toDetail(u);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public ContactReveal revealContact(long userId) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysUser u = requireUser(userId);
            // ② 审计对象名：加载后即登记当前姓名（realName 缺失回退账号；业务前，失败也留意图）
            AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(userId), memberAuditName(u));
            return new ContactReveal(u.getMobile(), u.getEmail());
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public MemberDetail createMember(CreateMemberCommand cmd) {
        if (cmd == null) throw new OpsTeamAdminException(OpsTeamAdminException.Code.MEMBER_NOT_FOUND, "请求体不能为空");
        if (cmd.username() == null || cmd.username().isBlank()) {
            throw new OpsTeamAdminException(OpsTeamAdminException.Code.USERNAME_CONFLICT, "用户名不能为空");
        }
        PasswordStrengthValidator.Result strength =
                PasswordStrengthValidator.evaluate(cmd.password(), securityPolicy.passwordMinLength());
        if (!strength.passed()) {
            throw new OpsTeamAdminException(OpsTeamAdminException.Code.WEAK_PASSWORD,
                    "密码不满足强度要求：至少 " + securityPolicy.passwordMinLength() + " 位且含大小写/数字/特殊符号中的 3 类");
        }
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            if (existsByField("username", cmd.username())) {
                throw new OpsTeamAdminException(OpsTeamAdminException.Code.USERNAME_CONFLICT, "用户名已存在");
            }
            if (cmd.email() != null && !cmd.email().isBlank() && existsByField("email", cmd.email())) {
                throw new OpsTeamAdminException(OpsTeamAdminException.Code.EMAIL_CONFLICT, "邮箱已被占用");
            }
            if (cmd.mobile() != null && !cmd.mobile().isBlank() && existsByField("mobile", cmd.mobile())) {
                throw new OpsTeamAdminException(OpsTeamAdminException.Code.MOBILE_CONFLICT, "手机号已被占用");
            }
            requireSuperAdminOperatorForGrant(cmd.roleIds());
            SysUser u = new SysUser();
            u.setTenantId(OPS_TENANT);
            u.setUsername(cmd.username());
            u.setPasswordHash(passwordHasher.hash(cmd.password()));
            u.setRealName(cmd.realName());
            u.setEmail(cmd.email());
            u.setMobile(cmd.mobile());
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userCommandService.insert(u);
            replaceRoles(u.getId(), cmd.roleIds());
            return toDetail(u);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public MemberDetail updateMember(long userId, UpdateMemberCommand cmd) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysUser u = requireUser(userId);
            // ② 审计对象名：加载后即登记「旧名/新名」（业务前，失败也留意图）
            AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(userId), u.getRealName(), cmd.realName());
            requireSuperAdminOperatorFor(userId);
            requireSuperAdminOperatorForGrant(cmd.roleIds());
            // 列表/详情默认脱敏，编辑表单回填掩码占位；提交值与当前值掩码一致即「未修改」，
            // 归一为 null 不覆盖，避免把掩码写回库（脱敏架构约束）。
            String emailIn = unchangedIfMask(cmd.email(), u.getEmail(), true);
            String mobileIn = unchangedIfMask(cmd.mobile(), u.getMobile(), false);
            if (emailIn != null && !emailIn.equals(u.getEmail()) && existsByField("email", emailIn)) {
                throw new OpsTeamAdminException(OpsTeamAdminException.Code.EMAIL_CONFLICT, "邮箱已被占用");
            }
            if (mobileIn != null && !mobileIn.equals(u.getMobile()) && existsByField("mobile", mobileIn)) {
                throw new OpsTeamAdminException(OpsTeamAdminException.Code.MOBILE_CONFLICT, "手机号已被占用");
            }
            if (cmd.realName() != null) u.setRealName(cmd.realName());
            if (emailIn != null) u.setEmail(emailIn);
            if (mobileIn != null) u.setMobile(mobileIn);
            if (cmd.gender() != null) u.setGender(cmd.gender());
            if (cmd.birthday() != null) u.setBirthday(cmd.birthday());
            if (cmd.empNo() != null) u.setEmpNo(cmd.empNo());
            u.setUpdatedAt(LocalDateTime.now());
            userCommandService.updateById(u);
            if (cmd.roleIds() != null) {
                replaceRoles(userId, cmd.roleIds());
            }
            return toDetail(u);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public void disableMember(long userId, long currentUserId) {
        if (userId == currentUserId) {
            throw new OpsTeamAdminException(OpsTeamAdminException.Code.CANNOT_DISABLE_SELF, "不能停用自己");
        }
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysUser u = requireUser(userId);
            // ② 审计对象名：加载后即登记当前姓名（realName 缺失回退账号；业务前，失败也留意图）
            AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(userId), memberAuditName(u));
            requireSuperAdminOperatorFor(userId);
            u.setStatus(0);
            u.setUpdatedAt(LocalDateTime.now());
            userCommandService.updateById(u);
            sessionStore.invalidateAllByUser(OPS_TENANT, userId);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public void enableMember(long userId) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysUser u = requireUser(userId);
            // ② 审计对象名：加载后即登记当前姓名（realName 缺失回退账号；业务前，失败也留意图）
            AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(userId), memberAuditName(u));
            requireSuperAdminOperatorFor(userId);
            u.setStatus(1);
            u.setUpdatedAt(LocalDateTime.now());
            userCommandService.updateById(u);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public void resetPassword(long userId, String newPassword) {
        PasswordStrengthValidator.Result strength =
                PasswordStrengthValidator.evaluate(newPassword, securityPolicy.passwordMinLength());
        if (!strength.passed()) {
            throw new OpsTeamAdminException(OpsTeamAdminException.Code.WEAK_PASSWORD,
                    "密码不满足强度要求：至少 " + securityPolicy.passwordMinLength() + " 位且含大小写/数字/特殊符号中的 3 类");
        }
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysUser u = requireUser(userId);
            // ② 审计对象名：加载后即登记当前姓名（realName 缺失回退账号；业务前，失败也留意图）
            AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(userId), memberAuditName(u));
            requireSuperAdminOperatorFor(userId);
            u.setPasswordHash(passwordHasher.hash(newPassword));
            u.setUpdatedAt(LocalDateTime.now());
            userCommandService.updateById(u);
            sessionStore.invalidateAllByUser(OPS_TENANT, userId);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public List<OAuthBindingService.Binding> listOAuthBindings(long userId) {
        // 运营成员（tenant_id=0）活跃绑定；绕 TenantLine（运营无环境租户上下文）。
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            requireUser(userId);
            return oauthBindingMapper.selectList(new LambdaQueryWrapper<SysUserOAuthBinding>()
                            .eq(SysUserOAuthBinding::getUserId, userId)
                            .isNull(SysUserOAuthBinding::getDeletedAt)
                            .orderByAsc(SysUserOAuthBinding::getProvider))
                    .stream()
                    .map(b -> new OAuthBindingService.Binding(
                            b.getProvider(), b.getNickname(), b.getAvatarUrl(), b.getBoundAt()))
                    .toList();
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public void unbindOAuth(long userId, String provider) {
        // 运营成员（tenant_id=0）的 OAuth 解绑；按 user_id + provider 软删活跃绑定（幂等）。
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysUser u = requireUser(userId);
            // ② 审计对象名：加载后即登记当前姓名（realName 缺失回退账号；业务前，失败也留意图）
            AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(userId), memberAuditName(u));
            requireSuperAdminOperatorFor(userId);
            oauthBindingMapper.update(null, Wrappers.<SysUserOAuthBinding>lambdaUpdate()
                    .eq(SysUserOAuthBinding::getUserId, userId)
                    .eq(SysUserOAuthBinding::getProvider, provider)
                    .isNull(SysUserOAuthBinding::getDeletedAt)
                    .set(SysUserOAuthBinding::getDeletedAt, LocalDateTime.now())
                    .set(SysUserOAuthBinding::getUpdatedAt, LocalDateTime.now()));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    // ===== private helpers =====

    /** 审计对象名 = realName（NOT NULL；与代登录/操作人列口径一致）。 */
    private static String memberAuditName(SysUser u) {
        return u.getRealName();
    }

    /** 目标成员持有 OPS_SUPER_ADMIN 时，要求当前操作者本人也持有 OPS_SUPER_ADMIN。 */
    private void requireSuperAdminOperatorFor(long targetUserId) {
        if (!holdsSuperAdminRole(targetUserId)) return;
        if (!operatorHoldsSuperAdminRole()) {
            throw new OpsTeamAdminException(OpsTeamAdminException.Code.CANNOT_OPERATE_SUPER_ADMIN,
                    "仅超级管理员可操作持有超级管理员角色的成员");
        }
    }

    /** roleIds 含 OPS_SUPER_ADMIN 角色时，要求当前操作者本人持有 OPS_SUPER_ADMIN（授予超管仅限超管）。 */
    private void requireSuperAdminOperatorForGrant(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return;
        boolean grantsSuperAdmin = userRoleQueryService.rolesByIds(OPS_TENANT, roleIds)
                .stream().anyMatch(r -> SUPER_ADMIN_ROLE_CODE.equals(r.code()));
        if (grantsSuperAdmin && !operatorHoldsSuperAdminRole()) {
            throw new OpsTeamAdminException(OpsTeamAdminException.Code.CANNOT_OPERATE_SUPER_ADMIN,
                    "仅超级管理员可授予超级管理员角色");
        }
    }

    private boolean operatorHoldsSuperAdminRole() {
        UserPrincipal me = UserContext.get();
        return me != null && holdsSuperAdminRole(me.userId());
    }

    private boolean holdsSuperAdminRole(long userId) {
        return userRoleQueryService.rolesOfUser(OPS_TENANT, userId)
                .stream().anyMatch(r -> SUPER_ADMIN_ROLE_CODE.equals(r.code()));
    }

    private SysUser requireUser(long userId) {
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, OPS_TENANT)
                .eq(SysUser::getId, userId));
        if (u == null) {
            throw new OpsTeamAdminException(OpsTeamAdminException.Code.MEMBER_NOT_FOUND, "成员不存在");
        }
        return u;
    }

    /**
     * 脱敏占位归一：提交值等于「当前值的掩码」时返回 null（视为未修改），否则原样返回。
     * 手机/邮箱明文不含 {@code *}，故真实新值绝不会等于掩码，判定可靠。
     */
    private static String unchangedIfMask(String incoming, String current, boolean email) {
        if (incoming == null) {
            return null;
        }
        String mask = email ? Sensitives.maskEmail(current) : Sensitives.maskPhone(current);
        return incoming.equals(mask) ? null : incoming;
    }

    private boolean existsByField(String field, String value) {
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, OPS_TENANT);
        switch (field) {
            case "username" -> q.eq(SysUser::getUsername, value);
            // mobile/email 落库加密（确定性 AES）：MP wrapper 不走 TypeHandler，须显式加密入参
            // 后用原始片段与列上密文等值比对（同明文恒同密文，唯一性判断成立）。
            case "email"    -> q.apply("email = {0}", sensitiveFieldCipher.encrypt(value));
            case "mobile"   -> q.apply("mobile = {0}", sensitiveFieldCipher.encrypt(value));
            default -> { return false; }
        }
        return userMapper.selectCount(q) > 0;
    }

    private void replaceRoles(long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getTenantId, OPS_TENANT)
                .eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) return;
        Set<Long> seen = new HashSet<>();
        for (Long rid : roleIds) {
            if (rid == null || !seen.add(rid)) continue;
            SysUserRole link = new SysUserRole();
            link.setTenantId(OPS_TENANT);
            link.setUserId(userId);
            link.setRoleId(rid);
            link.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(link);
        }
    }

    private MemberDetail toDetail(SysUser u) {
        List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getTenantId, OPS_TENANT)
                .eq(SysUserRole::getUserId, u.getId()));
        List<Long> roleIds = links.stream().map(SysUserRole::getRoleId).toList();
        List<String> roleCodes = roleIds.isEmpty() ? List.of() : roleMapper.selectList(
                        new LambdaQueryWrapper<SysRole>()
                                .eq(SysRole::getTenantId, OPS_TENANT)
                                .in(SysRole::getId, roleIds))
                .stream().map(SysRole::getCode).toList();
        return new MemberDetail(u.getId(), u.getUsername(), u.getRealName(),
                Sensitives.maskEmail(u.getEmail()), Sensitives.maskPhone(u.getMobile()),
                u.getGender(), u.getBirthday(), u.getEmpNo(),
                u.getStatus() == null ? 0 : u.getStatus(),
                roleIds, roleCodes,
                u.getLastLoginAt(), u.getCreatedAt(), u.getUpdatedAt());
    }

    private Map<Long, List<String>> userRoleCodes(List<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getTenantId, OPS_TENANT)
                .in(SysUserRole::getUserId, userIds));
        if (links.isEmpty()) return Collections.emptyMap();
        List<Long> roleIds = links.stream().map(SysUserRole::getRoleId).distinct().toList();
        Map<Long, String> codeById = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getTenantId, OPS_TENANT)
                        .in(SysRole::getId, roleIds))
                .stream().collect(Collectors.toMap(SysRole::getId, SysRole::getCode));
        Map<Long, List<String>> out = new HashMap<>();
        for (SysUserRole l : links) {
            String code = codeById.get(l.getRoleId());
            if (code == null) continue;
            out.computeIfAbsent(l.getUserId(), k -> new ArrayList<>()).add(code);
        }
        return out;
    }
}
