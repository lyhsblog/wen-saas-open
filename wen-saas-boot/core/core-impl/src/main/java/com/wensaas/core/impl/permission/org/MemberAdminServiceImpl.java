package com.wensaas.core.impl.permission.org;

import com.wensaas.core.api.namesource.EntityTypes;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.audit.Sensitives;
import com.wensaas.core.api.auth.permission.MemberAdminException;
import com.wensaas.core.api.auth.permission.MemberAdminService;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.auth.oauth.OAuthBindingService;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.audit.log.SysLoginLog;
import com.wensaas.core.impl.audit.log.SysLoginLogMapper;
import com.wensaas.core.impl.auth.login.LoginAttemptGuard;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.auth.password.PasswordResetTokenService;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.user.UserCommandService;
import com.wensaas.core.impl.namesource.TransFiller;
import com.wensaas.core.impl.security.SensitiveFieldCipher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.wensaas.core.impl.permission.role.SysRole;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.role.SysUserRole;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;

/**
 * 成员管理（写）实现。
 *
 * <p>租户上下文由 {@code TenantContext} 提供，MP {@code TenantLineInnerInterceptor} 自动为
 * sys_user / sys_user_dept / sys_user_role 等混合表注入 {@code tenant_id}；INSERT 仍显式
 * {@code setTenantId}（与 {@link DeptAdminServiceImpl} 同模式，MP 见列已存在即不重复注入）。
 *
 * <p>手机 / 邮箱确定性 AES 落库加密：唯一性 / 检索按密文等值（{@link SensitiveFieldCipher}）。
 */
public class MemberAdminServiceImpl implements MemberAdminService {

    private static final String PURPOSE_MEMBER = "reset_member";

    private final SysUserMapper userMapper;
    private final UserCommandService userCommandService;
    private final SysUserDeptMapper userDeptMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptMapper deptMapper;
    private final SysDeptLeaderMapper deptLeaderMapper;
    private final SysRoleMapper roleMapper;
    private final TenantQueryService tenantQuery;
    private final PasswordHasher passwordHasher;
    private final PasswordResetTokenService resetTokenService;
    private final SessionStore sessionStore;
    private final LoginAttemptGuard loginAttemptGuard;
    private final SensitiveFieldCipher cipher;
    private final DictService dictService;
    private final OAuthBindingService oauthBindingService;
    private final SysLoginLogMapper loginLogMapper;
    private final TransFiller transFiller;
    /** 可空：事件总线关闭（wen-saas-boot.event.enabled=false）时为 null，跳过领域事件发布。 */
    private final com.wensaas.core.api.event.DomainEventPublisher eventPublisher;

    /** 岗位字典分组 code。 */
    private static final String POSITION_GROUP = "POSITION";

    /** 登录记录回看窗口（天）：超出不在成员详情展示，避免全分区扫描。 */
    private static final int LOGIN_LOG_LOOKBACK_DAYS = 180;
    /** 登录记录单次返回上限。 */
    private static final int LOGIN_LOG_MAX_LIMIT = 100;

    public MemberAdminServiceImpl(SysUserMapper userMapper,
                                  UserCommandService userCommandService,
                                  SysUserDeptMapper userDeptMapper,
                                  SysUserRoleMapper userRoleMapper,
                                  SysDeptMapper deptMapper,
                                  SysDeptLeaderMapper deptLeaderMapper,
                                  SysRoleMapper roleMapper,
                                  TenantQueryService tenantQuery,
                                  PasswordHasher passwordHasher,
                                  PasswordResetTokenService resetTokenService,
                                  SessionStore sessionStore,
                                  LoginAttemptGuard loginAttemptGuard,
                                  SensitiveFieldCipher cipher,
                                  DictService dictService,
                                  OAuthBindingService oauthBindingService,
                                  SysLoginLogMapper loginLogMapper,
                                  TransFiller transFiller,
                                  com.wensaas.core.api.event.DomainEventPublisher eventPublisher) {
        this.userMapper = userMapper;
        this.userCommandService = userCommandService;
        this.userDeptMapper = userDeptMapper;
        this.userRoleMapper = userRoleMapper;
        this.deptMapper = deptMapper;
        this.deptLeaderMapper = deptLeaderMapper;
        this.roleMapper = roleMapper;
        this.tenantQuery = tenantQuery;
        this.passwordHasher = passwordHasher;
        this.resetTokenService = resetTokenService;
        this.sessionStore = sessionStore;
        this.loginAttemptGuard = loginAttemptGuard;
        this.cipher = cipher;
        this.dictService = dictService;
        this.oauthBindingService = oauthBindingService;
        this.loginLogMapper = loginLogMapper;
        this.transFiller = transFiller;
        this.eventPublisher = eventPublisher;
    }

    // ===================== 查询 =====================

    @Override
    public MemberPage listMembers(MemberQuery query) {
        long tenantId = TenantContext.require();
        int page = Math.max(1, query.page());
        int size = query.pageSize() <= 0 ? 20 : Math.min(query.pageSize(), 200);

        // 部门 / 角色过滤先解析候选 userId 集合（null = 不约束）
        Set<Long> candidate = null;
        if (query.deptId() != null) {
            Set<Long> deptIds = resolveDeptScope(tenantId, query.deptId(), query.includeSubDepts());
            candidate = userIdsInDepts(deptIds);
        }
        if (query.roleId() != null) {
            Set<Long> byRole = new HashSet<>(userRoleMapper.listUserIdsByRole(tenantId, query.roleId()));
            candidate = (candidate == null) ? byRole : intersect(candidate, byRole);
        }
        if (candidate != null && candidate.isEmpty()) {
            return new MemberPage(List.of(), 0, page, size);
        }

        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getCreatedAt);
        if (candidate != null) {
            q.in(SysUser::getId, candidate);
        }
        if (query.status() != null) {
            q.eq(SysUser::getStatus, query.status());
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String trimmed = query.keyword().trim();
            String like = "%" + trimmed + "%";
            String enc = cipher.encrypt(trimmed);
            q.and(w -> w.like(SysUser::getUsername, like)
                    .or().like(SysUser::getRealName, like)
                    .or().apply("email = {0}", enc)
                    .or().apply("mobile = {0}", enc));
        }
        Page<SysUser> p = userMapper.selectPage(Page.of(page, size), q);
        List<SysUser> users = p.getRecords();
        if (users.isEmpty()) {
            return new MemberPage(List.of(), p.getTotal(), page, size);
        }
        List<Long> ids = users.stream().map(SysUser::getId).toList();
        Map<Long, List<SysUserDept>> deptsByUser = userDeptByUsers(ids);
        Map<Long, List<Long>> roleIdsByUser = roleIdsByUsers(ids);
        Map<Long, Integer> leaderCountByUser = leaderDeptCountByUsers(ids);
        long ownerId = ownerUserId(tenantId);
        Map<String, String> positionNames = positionNameMap(
                users.stream().map(SysUser::getPositionCode).toList());

        List<MemberListItem> items = new ArrayList<>(users.size());
        for (SysUser u : users) {
            List<SysUserDept> binds = deptsByUser.getOrDefault(u.getId(), List.of());
            SysUserDept main = binds.stream().filter(b -> b.getIsMain() != null && b.getIsMain() == 1).findFirst().orElse(null);
            int secondary = (int) binds.stream().filter(b -> b.getIsMain() == null || b.getIsMain() == 0).count();
            List<Long> rids = roleIdsByUser.getOrDefault(u.getId(), List.of());
            // mainDeptName / roleNames 留空，由下方 transFiller.fill 按 @TransField 批量回填。
            items.add(new MemberListItem()
                    .id(u.getId()).username(u.getUsername()).realName(u.getRealName())
                    .mobile(Sensitives.maskPhone(u.getMobile())).email(Sensitives.maskEmail(u.getEmail()))
                    .mainDeptId(main == null ? 0L : main.getDeptId())
                    .secondaryDeptCount(secondary)
                    .status(u.getStatus() == null ? 0 : u.getStatus())
                    .owner(u.getId() == ownerId)
                    .roleIds(rids)
                    .positionCode(u.getPositionCode()).positionName(positionName(positionNames, u.getPositionCode()))
                    .loginLocked(loginAttemptGuard.lockStatus(tenantId, u.getUsername()).locked())
                    .lastLoginAt(u.getLastLoginAt()).createdAt(u.getCreatedAt())
                    .leaderDeptCount(leaderCountByUser.getOrDefault(u.getId(), 0)));
        }
        transFiller.fill(items); // 一次反射回填：mainDeptId→mainDeptName、roleIds→roleNames（批量去重，N+1 堵死）
        return new MemberPage(items, p.getTotal(), page, size);
    }

    @Override
    public MemberDetail getMember(long userId) {
        long tenantId = TenantContext.require();
        SysUser u = requireUser(userId);
        return toDetail(tenantId, u);
    }

    @Override
    public List<OAuthBindingService.Binding> listOAuthBindings(long userId) {
        long tenantId = TenantContext.require();
        requireUser(userId); // 校验成员存在且属当前租户
        return oauthBindingService.listByUser(tenantId, userId);
    }

    @Override
    public List<LoginLogItem> listLoginLogs(long userId, int limit) {
        long tenantId = TenantContext.require();
        requireUser(userId); // 校验成员存在且属当前租户
        int n = limit <= 0 ? 20 : Math.min(limit, LOGIN_LOG_MAX_LIMIT);
        LocalDateTime floor = LocalDateTime.now().minusDays(LOGIN_LOG_LOOKBACK_DAYS);
        // 显式带 tenant_id + operator_id：命中 idx_login_log* 并对分区表加时间下限，避免全分区扫描。
        List<SysLoginLog> rows = loginLogMapper.selectList(new LambdaQueryWrapper<SysLoginLog>()
                .eq(SysLoginLog::getTenantId, tenantId)
                .eq(SysLoginLog::getOperatorId, userId)
                .ge(SysLoginLog::getCreatedAt, floor)
                .orderByDesc(SysLoginLog::getCreatedAt)
                .last("LIMIT " + n));
        return rows.stream()
                .map(r -> new LoginLogItem(r.getLoginMethod(), r.getAction(), r.getResult(),
                        r.getFailReason(), r.getIp(), r.getUserAgent(), r.getCreatedAt()))
                .toList();
    }

    @Override
    public ContactReveal revealContact(long userId) {
        TenantContext.require();
        SysUser u = requireUser(userId);
        // ② 审计对象名：加载后即登记当前姓名（realName 缺失回退账号；业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(userId), memberAuditName(u));
        return new ContactReveal(u.getMobile(), u.getEmail());
    }

    @Override
    public List<MemberSearchItem> search(String keyword, int limit) {
        long tenantId = TenantContext.require();
        int lim = limit <= 0 ? 20 : Math.min(limit, 50);
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .orderByDesc(SysUser::getCreatedAt)
                .last("limit " + lim);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            q.and(w -> w.like(SysUser::getUsername, like).or().like(SysUser::getRealName, like));
        }
        List<SysUser> users = userMapper.selectList(q);
        if (users.isEmpty()) return List.of();
        Map<Long, List<SysUserDept>> deptsByUser = userDeptByUsers(users.stream().map(SysUser::getId).toList());
        Map<Long, String> deptNames = deptNameMap(allDeptIds(deptsByUser.values()));
        List<MemberSearchItem> out = new ArrayList<>(users.size());
        for (SysUser u : users) {
            SysUserDept main = deptsByUser.getOrDefault(u.getId(), List.of()).stream()
                    .filter(b -> b.getIsMain() != null && b.getIsMain() == 1).findFirst().orElse(null);
            out.add(new MemberSearchItem(u.getId(), u.getUsername(), u.getRealName(),
                    main == null ? null : deptNames.get(main.getDeptId())));
        }
        return out;
    }

    // ===================== 写 =====================

    @Override
    @Transactional
    public CreatedMember createMember(CreateMemberCommand cmd) {
        if (cmd == null || cmd.username() == null || cmd.username().isBlank()) {
            throw new MemberAdminException(MemberAdminException.Code.INVALID_ARGUMENT, "账号不能为空");
        }
        long tenantId = TenantContext.require();
        if (existsByUsername(cmd.username())) {
            throw new MemberAdminException(MemberAdminException.Code.USERNAME_CONFLICT, "账号已存在：" + cmd.username());
        }
        if (notBlank(cmd.email()) && existsByEncrypted("email", cmd.email())) {
            throw new MemberAdminException(MemberAdminException.Code.EMAIL_CONFLICT, "邮箱已被占用");
        }
        if (notBlank(cmd.mobile()) && existsByEncrypted("mobile", cmd.mobile())) {
            throw new MemberAdminException(MemberAdminException.Code.MOBILE_CONFLICT, "手机号已被占用");
        }
        validateContactFormat(cmd.mobile(), cmd.email());
        if (cmd.mainDeptId() == null) {
            throw new MemberAdminException(MemberAdminException.Code.MAIN_DEPT_REQUIRED, "必须指定主部门");
        }
        requireDept(cmd.mainDeptId());

        SysUser u = new SysUser();
        u.setTenantId(tenantId);
        u.setUsername(cmd.username().trim());
        // 不设初始密码：置一个不可登录的随机占位 hash，待激活链接重设
        u.setPasswordHash(passwordHasher.hash(UUID.randomUUID().toString()));
        u.setRealName(cmd.realName());
        if (notBlank(cmd.email())) u.setEmail(cmd.email().trim());
        if (notBlank(cmd.mobile())) u.setMobile(cmd.mobile().trim());
        if (notBlank(cmd.positionCode())) u.setPositionCode(cmd.positionCode().trim());
        u.setStatus(1);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        u.setCreatedBy(currentUserId());
        userCommandService.insert(u);

        rewriteDepts(tenantId, u.getId(), cmd.mainDeptId(), cmd.secondaryDeptIds());
        replaceRoles(tenantId, u.getId(), cmd.roleIds());

        PasswordResetTokenService.IssuedToken issued = resetTokenService.issue(
                u.getId(), tenantId, tenantCode(tenantId), u.getUsername(), PURPOSE_MEMBER);
        return new CreatedMember(u.getId(), u.getUsername(), issued.token(), issued.expiresAt());
    }

    @Override
    @Transactional
    public MemberDetail updateMember(long userId, UpdateMemberCommand cmd) {
        long tenantId = TenantContext.require();
        SysUser u = requireUser(userId);
        // ② 审计对象名：加载后即登记「旧名/新名」（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(userId), u.getRealName(), cmd.realName());
        // 列表/详情默认脱敏，编辑表单回填的是掩码占位；提交值与当前值掩码一致即「未修改」，
        // 归一为 null 不覆盖，避免把 138****0001 写回库（脱敏架构约束）。
        String emailIn = unchangedIfMask(cmd.email(), u.getEmail(), true);
        String mobileIn = unchangedIfMask(cmd.mobile(), u.getMobile(), false);
        validateContactFormat(mobileIn, emailIn);
        if (notBlank(emailIn) && !emailIn.equals(u.getEmail()) && existsByEncrypted("email", emailIn)) {
            throw new MemberAdminException(MemberAdminException.Code.EMAIL_CONFLICT, "邮箱已被占用");
        }
        if (notBlank(mobileIn) && !mobileIn.equals(u.getMobile()) && existsByEncrypted("mobile", mobileIn)) {
            throw new MemberAdminException(MemberAdminException.Code.MOBILE_CONFLICT, "手机号已被占用");
        }
        if (cmd.realName() != null) u.setRealName(cmd.realName());
        if (emailIn != null) u.setEmail(emailIn.isBlank() ? null : emailIn.trim());
        if (mobileIn != null) u.setMobile(mobileIn.isBlank() ? null : mobileIn.trim());
        if (cmd.gender() != null) u.setGender(cmd.gender());
        if (cmd.birthday() != null) u.setBirthday(cmd.birthday());
        if (cmd.empNo() != null) u.setEmpNo(cmd.empNo());
        // positionCode：null 不动；空串清空（岗位非必填，可置空）
        boolean clearPosition = false;
        if (cmd.positionCode() != null) {
            if (cmd.positionCode().isBlank()) {
                clearPosition = true;        // 置空：MP updateById 跳过 null 字段，下方显式 set
            } else {
                u.setPositionCode(cmd.positionCode().trim());
            }
        }
        u.setUpdatedAt(LocalDateTime.now());
        u.setUpdatedBy(currentUserId());
        userCommandService.updateById(u);
        if (clearPosition) {
            userCommandService.clearPosition(userId);
        }
        if (cmd.roleIds() != null) {
            replaceRoles(tenantId, userId, cmd.roleIds());
        }
        return toDetail(tenantId, u);
    }

    @Override
    @Transactional
    public MemberDetail assignRoles(long userId, List<Long> roleIds) {
        long tenantId = TenantContext.require();
        SysUser u = requireUser(userId);
        replaceRoles(tenantId, userId, roleIds);
        return toDetail(tenantId, u);
    }

    @Override
    @Transactional
    public MemberDetail manageDepts(long userId, ManageDeptsCommand cmd) {
        long tenantId = TenantContext.require();
        SysUser u = requireUser(userId);
        if (cmd.mainDeptId() == null) {
            throw new MemberAdminException(MemberAdminException.Code.MAIN_DEPT_REQUIRED, "必须保留一个主部门");
        }
        // 主管理员可改自己的部门，但不能被他人调部门
        long ownerId = ownerUserId(tenantId);
        if (userId == ownerId && currentUserId() != null && currentUserId() != ownerId) {
            throw new MemberAdminException(MemberAdminException.Code.OWNER_PROTECTED, "主管理员的部门只能由其本人调整");
        }
        requireDept(cmd.mainDeptId());
        rewriteDepts(tenantId, userId, cmd.mainDeptId(), cmd.secondaryDeptIds());
        return toDetail(tenantId, u);
    }

    @Override
    @Transactional
    public void setStatus(long userId, boolean enabled, long currentUserId) {
        long tenantId = TenantContext.require();
        SysUser u = requireUser(userId);
        // ② 审计对象名：加载后即登记当前姓名（realName 缺失回退账号；业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(userId), memberAuditName(u));
        if (!enabled) {
            if (userId == ownerUserId(tenantId)) {
                throw new MemberAdminException(MemberAdminException.Code.OWNER_PROTECTED, "主管理员不可停用");
            }
            if (userId == currentUserId) {
                throw new MemberAdminException(MemberAdminException.Code.CANNOT_DISABLE_SELF, "不能停用自己");
            }
        }
        u.setStatus(enabled ? 1 : 0);
        u.setUpdatedAt(LocalDateTime.now());
        u.setUpdatedBy(currentUserId());
        userCommandService.updateById(u);
        if (!enabled) {
            // 停用是「状态信号」非连锁清理：仅强制下线全部会话；
            // 部门负责人身份 / 角色等一律保留，恢复启用即复权。仍持负责人身份的停用成员经
            // MemberDetail.leaderDepts / MemberListItem.leaderDeptCount 暴露给前端高亮提醒。
            sessionStore.invalidateAllByUser(tenantId, userId);
            // 领域事件：业务模块可订阅做善后（如 crm_demo 提醒改派名下客户）；发布异步、失败仅记日志
            if (eventPublisher != null) {
                eventPublisher.publish(com.wensaas.core.api.event.CoreEventTypes.MEMBER_DISABLED,
                        new com.wensaas.core.api.event.CoreEventTypes.MemberDisabledPayload(
                                String.valueOf(userId), u.getRealName(), String.valueOf(currentUserId)));
            }
        }
    }

    @Override
    @Transactional
    public IssuedActivation reissueActivation(long userId) {
        long tenantId = TenantContext.require();
        SysUser u = requireUser(userId);
        // ② 审计对象名：result(IssuedActivation) 不含 realName，这里登记成员姓名
        AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(userId), memberAuditName(u));
        PasswordResetTokenService.IssuedToken issued = resetTokenService.issue(
                u.getId(), tenantId, tenantCode(tenantId), u.getUsername(), PURPOSE_MEMBER);
        return new IssuedActivation(issued.token(), issued.expiresAt(), u.getUsername());
    }

    @Override
    public void unlockLogin(long userId) {
        long tenantId = TenantContext.require();
        SysUser u = requireUser(userId);
        // ② 审计对象名：加载后即登记当前姓名（realName 缺失回退账号；业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(userId), memberAuditName(u));
        loginAttemptGuard.unlock(tenantId, u.getUsername());
    }

    // ===================== helpers =====================

    /** 审计对象名 = realName（NOT NULL；与代登录/操作人列口径一致）。 */
    private static String memberAuditName(SysUser u) {
        return u.getRealName();
    }

    private SysUser requireUser(long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            throw new MemberAdminException(MemberAdminException.Code.MEMBER_NOT_FOUND, "成员不存在：" + userId);
        }
        return u;
    }

    private void requireDept(long deptId) {
        SysDept d = deptMapper.selectById(deptId);
        if (d == null) {
            throw new MemberAdminException(MemberAdminException.Code.DEPT_NOT_FOUND, "部门不存在：" + deptId);
        }
    }

    private boolean existsByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim())) > 0;
    }

    private boolean existsByEncrypted(String field, String value) {
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<>();
        q.apply(field + " = {0}", cipher.encrypt(value.trim()));
        return userMapper.selectCount(q) > 0;
    }

    /** 全量重写成员部门绑定：1 主 + N 兼。成员被移出某部门时自动取消其在该部门的负责人身份。 */
    private void rewriteDepts(long tenantId, long userId, long mainDeptId, List<Long> secondaryDeptIds) {
        userDeptMapper.delete(new LambdaQueryWrapper<SysUserDept>().eq(SysUserDept::getUserId, userId));
        Set<Long> finalDepts = new HashSet<>();
        finalDepts.add(mainDeptId);
        SysUserDept main = new SysUserDept();
        main.setTenantId(tenantId);
        main.setUserId(userId);
        main.setDeptId(mainDeptId);
        main.setIsMain(1);
        main.setCreatedAt(LocalDateTime.now());
        userDeptMapper.insert(main);
        if (secondaryDeptIds != null) {
            for (Long did : secondaryDeptIds) {
                if (did == null || !finalDepts.add(did)) continue;
                requireDept(did);
                SysUserDept sec = new SysUserDept();
                sec.setTenantId(tenantId);
                sec.setUserId(userId);
                sec.setDeptId(did);
                sec.setIsMain(0);
                sec.setCreatedAt(LocalDateTime.now());
                userDeptMapper.insert(sec);
            }
        }
        // 清理：成员已不在的部门，撤销其负责人身份
        deptLeaderMapper.delete(new LambdaQueryWrapper<SysDeptLeader>()
                .eq(SysDeptLeader::getUserId, userId)
                .notIn(!finalDepts.isEmpty(), SysDeptLeader::getDeptId, finalDepts));
    }

    private void replaceRoles(long tenantId, long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) return;
        Set<Long> seen = new HashSet<>();
        for (Long rid : roleIds) {
            if (rid == null || !seen.add(rid)) continue;
            SysUserRole link = new SysUserRole();
            link.setTenantId(tenantId);
            link.setUserId(userId);
            link.setRoleId(rid);
            link.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(link);
        }
    }

    private MemberDetail toDetail(long tenantId, SysUser u) {
        List<SysUserDept> binds = userDeptMapper.selectList(new LambdaQueryWrapper<SysUserDept>()
                .eq(SysUserDept::getUserId, u.getId()));
        // DeptBinding 只给 deptId，部门名由末尾 transFiller.fill 深递归回填。
        DeptBinding mainDept = null;
        List<DeptBinding> secondary = new ArrayList<>();
        for (SysUserDept b : binds) {
            DeptBinding view = new DeptBinding(b.getDeptId());
            if (b.getIsMain() != null && b.getIsMain() == 1) mainDept = view;
            else secondary.add(view);
        }
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, u.getId()))
                .stream().map(SysUserRole::getRoleId).toList();
        // RoleBrief 需 code + name 两列、name 非纯 @TransField，仍由 roleMapper 一次查出（不收口）。
        List<RoleBrief> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectList(
                        new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds))
                .stream().map(r -> new RoleBrief(r.getId(), r.getCode(), r.getName())).toList();
        // 该成员当前持有的部门负责人身份（停用后保留，前端高亮提醒手动改派）
        List<Long> leaderDeptIds = deptLeaderMapper.selectList(new LambdaQueryWrapper<SysDeptLeader>()
                        .eq(SysDeptLeader::getUserId, u.getId()))
                .stream().map(SysDeptLeader::getDeptId).toList();
        List<DeptBinding> leaderDepts = leaderDeptIds.stream()
                .map(did -> new DeptBinding(did)).toList();
        Map<String, String> posName = positionNameMap(List.of(
                u.getPositionCode() == null ? "" : u.getPositionCode()));
        LoginAttemptGuard.LockStatus lock = loginAttemptGuard.lockStatus(tenantId, u.getUsername());
        MemberDetail detail = new MemberDetail(u.getId(), u.getUsername(), u.getRealName(),
                Sensitives.maskPhone(u.getMobile()), Sensitives.maskEmail(u.getEmail()),
                u.getGender(), u.getBirthday(), u.getEmpNo(),
                u.getPositionCode(), positionName(posName, u.getPositionCode()),
                u.getStatus() == null ? 0 : u.getStatus(),
                u.getId() == ownerUserId(tenantId),
                lock.locked(), lock.remainingSeconds(),
                mainDept, secondary, roleIds, roles,
                u.getLastLoginAt(), u.getCreatedAt(), u.getUpdatedAt(), leaderDepts);
        // 深递归回填 mainDept / secondaryDepts / leaderDepts 各 DeptBinding 的 deptName（一条 IN）。
        transFiller.fill(detail);
        return detail;
    }

    /** 批量解析岗位 code → name（字典 POSITION）；忽略空 code。 */
    private Map<String, String> positionNameMap(List<String> codes) {
        Set<String> wanted = codes.stream()
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return dictService.batchGetItemNames(POSITION_GROUP, wanted);
    }

    /** 岗位展示名：取字典名；字典项被删则回退 code 文本快照。 */
    private static String positionName(Map<String, String> nameMap, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return nameMap.getOrDefault(code, code);
    }

    private Set<Long> resolveDeptScope(long tenantId, long deptId, boolean includeSub) {
        Set<Long> ids = new HashSet<>();
        ids.add(deptId);
        if (includeSub) {
            List<SysDept> all = deptMapper.listForTenant(tenantId);
            SysDept self = all.stream().filter(d -> d.getId() == deptId).findFirst().orElse(null);
            if (self != null) {
                String marker = self.getAncestors() + deptId + "/";
                for (SysDept d : all) {
                    if (d.getAncestors() != null && d.getAncestors().startsWith(marker)) {
                        ids.add(d.getId());
                    }
                }
            }
        }
        return ids;
    }

    private Set<Long> userIdsInDepts(Set<Long> deptIds) {
        if (deptIds.isEmpty()) return new HashSet<>();
        return userDeptMapper.selectList(new LambdaQueryWrapper<SysUserDept>()
                        .in(SysUserDept::getDeptId, deptIds))
                .stream().map(SysUserDept::getUserId).collect(Collectors.toSet());
    }

    private Map<Long, List<SysUserDept>> userDeptByUsers(List<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        Map<Long, List<SysUserDept>> out = new HashMap<>();
        for (SysUserDept b : userDeptMapper.selectList(new LambdaQueryWrapper<SysUserDept>()
                .in(SysUserDept::getUserId, userIds))) {
            out.computeIfAbsent(b.getUserId(), k -> new ArrayList<>()).add(b);
        }
        return out;
    }

    private Map<Long, List<Long>> roleIdsByUsers(List<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        Map<Long, List<Long>> out = new HashMap<>();
        for (SysUserRole r : userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, userIds))) {
            out.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(r.getRoleId());
        }
        return out;
    }

    private Map<Long, Integer> leaderDeptCountByUsers(List<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        Map<Long, Integer> out = new HashMap<>();
        for (SysDeptLeader l : deptLeaderMapper.selectList(new LambdaQueryWrapper<SysDeptLeader>()
                .in(SysDeptLeader::getUserId, userIds))) {
            out.merge(l.getUserId(), 1, Integer::sum);
        }
        return out;
    }

    private Set<Long> allDeptIds(java.util.Collection<List<SysUserDept>> binds) {
        Set<Long> ids = new HashSet<>();
        for (List<SysUserDept> l : binds) for (SysUserDept b : l) ids.add(b.getDeptId());
        return ids;
    }

    // deptNameMap 仅 search() 仍在用（MemberSearchItem 无 deptId 兄弟字段、不改契约）；
    // listMembers / toDetail 的部门名已收口到 transFiller.fill。roleNameMap 已被框架完全取代删除。
    private Map<Long, String> deptNameMap(java.util.Collection<Long> deptIds) {
        Set<Long> ids = new HashSet<>(deptIds);
        if (ids.isEmpty()) return Collections.emptyMap();
        return deptMapper.selectList(new LambdaQueryWrapper<SysDept>().in(SysDept::getId, ids))
                .stream().collect(Collectors.toMap(SysDept::getId, SysDept::getName, (a, b) -> a));
    }

    private long ownerUserId(long tenantId) {
        TenantInfo t = tenantQuery.tenantById(tenantId);
        return t == null || t.mainAdminUserId() == null ? -1L : t.mainAdminUserId();
    }

    private String tenantCode(long tenantId) {
        TenantInfo t = tenantQuery.tenantById(tenantId);
        return t == null ? null : t.code();
    }

    private static Set<Long> intersect(Set<Long> a, Set<Long> b) {
        Set<Long> out = new HashSet<>(a);
        out.retainAll(b);
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // 手机/邮箱为选填，非空时校验格式（与前端 zod / 运营端 OpsMember*Request 的 @Pattern 口径一致）。
    private static final java.util.regex.Pattern MOBILE_RE =
            java.util.regex.Pattern.compile("^1[3-9]\\d{9}$");
    private static final java.util.regex.Pattern EMAIL_RE =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** 联系方式格式兜底校验（mobile/email 已脱敏占位归一为 null；null/空跳过，非空必须合法）。 */
    private static void validateContactFormat(String mobile, String email) {
        if (notBlank(mobile) && !MOBILE_RE.matcher(mobile.trim()).matches()) {
            throw new MemberAdminException(MemberAdminException.Code.INVALID_ARGUMENT, "手机号格式不正确");
        }
        if (notBlank(email) && !EMAIL_RE.matcher(email.trim()).matches()) {
            throw new MemberAdminException(MemberAdminException.Code.INVALID_ARGUMENT, "邮箱格式不正确");
        }
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

    private static Long currentUserId() {
        UserPrincipal p = UserContext.get();
        return p == null ? null : p.userId();
    }
}
