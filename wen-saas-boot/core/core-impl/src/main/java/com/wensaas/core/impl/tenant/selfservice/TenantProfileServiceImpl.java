package com.wensaas.core.impl.tenant.selfservice;

import com.wensaas.core.api.auth.org.DeptQueryService;
import com.wensaas.core.api.auth.org.DeptQueryService.DeptBinding;
import com.wensaas.core.api.auth.org.DeptQueryService.DeptInfo;
import com.wensaas.core.api.auth.permission.UserRoleQueryService;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.tenant.selfservice.TenantProfileException;
import com.wensaas.core.api.tenant.selfservice.TenantProfileService;
import com.wensaas.core.api.verification.VerificationCodeService;
import com.wensaas.core.api.verification.VerificationPurpose;
import com.wensaas.core.impl.auth.AuthProperties;
import com.wensaas.core.impl.auth.mfa.MfaService;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.auth.password.PasswordStrengthValidator;
import com.wensaas.core.impl.auth.password.SecurityPolicySettings;
import com.wensaas.core.impl.auth.session.Session;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.user.UserCommandService;
import com.wensaas.core.impl.auth.login.UserAgents;
import com.wensaas.core.impl.permission.datascope.DataScope;
import com.wensaas.core.impl.security.SensitiveFieldCipher;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 租户个人中心服务实现（与运营侧 {@code OpsProfileServiceImpl} 平行）。
 *
 * <p>运行在租户上下文内：DB 操作经 MP TenantLine 自动按当前租户过滤，再以显式
 * {@code (tenant_id, user_id)} 双重收敛，确保只能读写自己的账号。
 */
public class TenantProfileServiceImpl implements TenantProfileService {

    private static final Logger log = LoggerFactory.getLogger(TenantProfileServiceImpl.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Duration AVATAR_URL_TTL = Duration.ofMinutes(10);
    private static final long AVATAR_MAX_BYTES = 1024L * 1024L; // 1MB
    private static final List<String> AVATAR_MIME = List.of("image/png", "image/jpeg", "image/jpg");

    private static final String MOBILE_FLOW_KEY = "wen-saas-boot:tenant-mobile-change:";
    private static final String EMAIL_FLOW_KEY = "wen-saas-boot:tenant-email-change:";
    private static final Duration MOBILE_FLOW_TTL = Duration.ofMinutes(10);
    private static final Pattern CN_MOBILE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_RE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SysUserMapper userMapper;
    private final UserCommandService userCommandService;
    private final UserRoleQueryService userRoleQuery;
    private final DeptQueryService deptQuery;
    private final SessionStore sessionStore;
    private final PasswordHasher passwordHasher;
    private final FileService fileService;
    private final AuthProperties authProperties;
    private final SecurityPolicySettings securityPolicy;
    private final ObjectProvider<MfaService> mfaServiceProvider;
    private final ObjectProvider<VerificationCodeService> verificationCodeServiceProvider;
    private final StringRedisTemplate redis;
    private final com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore defaultScopeStore;
    private final SensitiveFieldCipher sensitiveFieldCipher;

    public TenantProfileServiceImpl(SysUserMapper userMapper,
                                    UserCommandService userCommandService,
                                    UserRoleQueryService userRoleQuery,
                                    DeptQueryService deptQuery,
                                    SessionStore sessionStore,
                                    PasswordHasher passwordHasher,
                                    FileService fileService,
                                    AuthProperties authProperties,
                                    SecurityPolicySettings securityPolicy,
                                    ObjectProvider<MfaService> mfaServiceProvider,
                                    ObjectProvider<VerificationCodeService> verificationCodeServiceProvider,
                                    StringRedisTemplate redis,
                                    com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore defaultScopeStore,
                                    SensitiveFieldCipher sensitiveFieldCipher) {
        this.userMapper = userMapper;
        this.userCommandService = userCommandService;
        this.userRoleQuery = userRoleQuery;
        this.deptQuery = deptQuery;
        this.sessionStore = sessionStore;
        this.passwordHasher = passwordHasher;
        this.fileService = fileService;
        this.authProperties = authProperties;
        this.securityPolicy = securityPolicy;
        this.mfaServiceProvider = mfaServiceProvider;
        this.verificationCodeServiceProvider = verificationCodeServiceProvider;
        this.redis = redis;
        this.defaultScopeStore = defaultScopeStore;
        this.sensitiveFieldCipher = sensitiveFieldCipher;
    }

    private VerificationCodeService verificationCodeService() {
        VerificationCodeService svc = verificationCodeServiceProvider.getIfAvailable();
        if (svc == null) {
            throw new IllegalStateException(
                    "改手机号双通道需启用通知模块（wen-saas-boot.notification.enabled=true）");
        }
        return svc;
    }

    // ===== 只读视图 =====

    @Override
    public Basic getBasic(long tenantId, long userId) {
        SysUser u = requireUser(tenantId, userId);
        return new Basic(
                u.getId(), u.getUsername(), u.getEmpNo(), u.getEmail(), u.getRealName(),
                u.getMobile(), u.getGender(), u.getBirthday(), u.getAvatarFileId(),
                resolveAvatarUrl(u.getAvatarFileId()));
    }

    @Override
    public Work getWork(long tenantId, long userId, boolean owner) {
        SysUser u = requireUser(tenantId, userId);

        List<DeptBinding> bindings = deptQuery.deptBindingsOfUser(tenantId, userId);
        DeptBinding main = bindings.stream()
                .filter(DeptBinding::main)
                .findFirst().orElse(null);

        // 部门名 / 全路径统一取整租户部门缓存快照（0 查库）。绑定指向的部门若已删除，
        // 快照中不存在则跳过，与逐条直查（逻辑删过滤后返回 null）口径一致；停用部门照常展示。
        Map<Long, DeptInfo> depts = bindings.isEmpty() ? Map.of() : deptQuery.deptInfosOfTenant(tenantId);
        String mainDeptName = null;
        String mainDeptPath = null;
        if (main != null) {
            DeptInfo dept = depts.get(main.deptId());
            if (dept != null) {
                mainDeptName = dept.name();
                mainDeptPath = buildDeptPath(dept, depts);
            }
        }
        List<String> secondaryNames = new ArrayList<>();
        for (DeptBinding b : bindings) {
            if (b.main()) {
                continue;
            }
            DeptInfo dept = depts.get(b.deptId());
            if (dept != null) {
                secondaryNames.add(dept.name());
            }
        }

        List<UserRoleQueryService.RoleBrief> roles = loadRoles(tenantId, userId);
        List<RoleBrief> roleBriefs = roles.stream()
                .map(r -> new RoleBrief(r.code(), r.name())).toList();

        return new Work(mainDeptName, mainDeptPath, secondaryNames, roleBriefs,
                statusLabel(u.getStatus()), dataScopeLabel(tenantId, roles, owner), owner);
    }

    @Override
    public Security getSecurity(long tenantId, long userId) {
        requireUser(tenantId, userId);
        AuthProperties.LoginAttempt la = authProperties.getLoginAttempt();
        MfaService mfa = mfaServiceProvider.getIfAvailable();
        boolean enabled = false;
        String method = null;
        if (mfa != null) {
            MfaService.Status status = mfa.status(userId);
            enabled = status.loginMfaEnabled();
            method = status.loginMethod() == null ? null : status.loginMethod().name();
        }
        return new Security(
                authProperties.getTokenTtl().toSeconds(),
                la.getMaxAttempts(), la.getWindowSeconds(),
                false, enabled, method);
    }

    // ===== 写操作 =====

    @Override
    public void updateProfile(long tenantId, long userId, ProfileUpdate update) {
        SysUser u = requireUser(tenantId, userId);
        // 姓名不可自助修改（仅管理员在用户管理处变更）；此处只更新性别/生日。
        u.setGender(update.gender());
        u.setBirthday(update.birthday());
        // 头像改由独立「更换头像」入口即传即存（O5-1）；avatarFileId 为 null 视为未修改，
        // 避免编辑基本信息（不带头像）把已设头像清空。
        if (update.avatarFileId() != null) {
            u.setAvatarFileId(update.avatarFileId());
        }
        u.setUpdatedAt(LocalDateTime.now());
        u.setUpdatedBy(userId);
        userCommandService.updateById(u);
    }

    @Override
    public AvatarResult uploadAvatar(long tenantId, long userId, AvatarUpload upload) {
        SysUser u = requireUser(tenantId, userId);
        if (upload.sizeBytes() > AVATAR_MAX_BYTES) {
            throw new TenantProfileException(TenantProfileException.Code.INVALID_AVATAR,
                    "头像文件大小不能超过 1MB");
        }
        String mime = upload.mimeType() == null ? "" : upload.mimeType().toLowerCase();
        if (!AVATAR_MIME.contains(mime)) {
            throw new TenantProfileException(TenantProfileException.Code.INVALID_AVATAR,
                    "头像仅支持 JPG / PNG 格式");
        }
        FileUploadResult result = fileService.uploadDirect(new FileUploadRequest(
                upload.fileName(), upload.mimeType(), upload.sizeBytes(), upload.content(),
                FileVisibility.FILE_CENTER, null, null, null, "/avatar"));
        u.setAvatarFileId(result.fileId());
        u.setUpdatedAt(LocalDateTime.now());
        u.setUpdatedBy(userId);
        userCommandService.updateById(u);
        return new AvatarResult(result.fileId(), resolveAvatarUrl(result.fileId()));
    }

    @Override
    public void changePassword(long tenantId, long userId, String newPassword) {
        SysUser u = requireUser(tenantId, userId);
        int minLength = securityPolicy.passwordMinLength();
        if (!PasswordStrengthValidator.evaluate(newPassword, minLength).passed()) {
            throw new TenantProfileException(TenantProfileException.Code.WEAK_PASSWORD,
                    "新密码强度不足：至少 " + minLength + " 位且包含大小写/数字/特殊符号中的 3 类");
        }
        u.setPasswordHash(passwordHasher.hash(newPassword));
        u.setUpdatedAt(LocalDateTime.now());
        u.setUpdatedBy(userId);
        userCommandService.updateById(u);
        // 改密后强制下线所有设备（含当前），前端跳登录页重登。
        sessionStore.invalidateAllByUser(tenantId, userId);
    }

    @Override
    public List<SessionItem> listSessions(long tenantId, long userId, String currentSessionId) {
        return sessionStore.findByUser(tenantId, userId).stream()
                .map(s -> new SessionItem(
                        s.sessionId(),
                        s.loginAt().atZone(SHANGHAI).toOffsetDateTime(),
                        s.lastAccessAt().atZone(SHANGHAI).toOffsetDateTime(),
                        s.sessionId().equals(currentSessionId),
                        UserAgents.describe(s.userAgent()),
                        s.clientIp()))
                .toList();
    }

    @Override
    public void revokeSession(long tenantId, long userId, String currentSessionId, String targetSessionId) {
        if (targetSessionId != null && targetSessionId.equals(currentSessionId)) {
            throw new TenantProfileException(TenantProfileException.Code.CANNOT_REVOKE_CURRENT_SESSION,
                    "不能下线当前会话，请用其他设备操作");
        }
        Optional<Session> target = sessionStore.findBySessionId(targetSessionId);
        if (target.isEmpty() || target.get().userId() != userId || target.get().tenantId() != tenantId) {
            throw new TenantProfileException(TenantProfileException.Code.SESSION_NOT_FOUND,
                    "会话不存在或不属于当前用户");
        }
        sessionStore.invalidate(targetSessionId);
    }

    @Override
    public void revokeOtherSessions(long tenantId, long userId, String currentSessionId) {
        sessionStore.invalidateAllByUserExcept(tenantId, userId, currentSessionId);
    }

    // ===== 改手机号 · step-up 前置身份验证后（发码到新手机 → 校验并更新）=====

    @Override
    public MobileChangeStep sendNewMobileCode(long tenantId, long userId, String newMobile) {
        SysUser u = requireUser(tenantId, userId);
        if (newMobile == null || !CN_MOBILE.matcher(newMobile).matches()) {
            throw new TenantProfileException(TenantProfileException.Code.MOBILE_CHANGE_INVALID_NEW, "新手机号格式错误");
        }
        if (newMobile.equals(u.getMobile())) {
            throw new TenantProfileException(TenantProfileException.Code.MOBILE_CHANGE_INVALID_NEW, "新手机号与原手机号相同");
        }
        VerificationCodeService.SentResult sent = verificationCodeService().send(
                VerificationPurpose.PHONE_CHANGE_NEW, Channel.SMS, newMobile, userId, tenantId);
        redis.opsForValue().set(mobileFlowKey(userId), "NEW_SENT:" + newMobile, MOBILE_FLOW_TTL);
        return new MobileChangeStep(sent.maskedTarget(), sent.ttlSeconds(), sent.resendAfterSeconds());
    }

    @Override
    public void confirmNewMobile(long tenantId, long userId, String code) {
        SysUser u = requireUser(tenantId, userId);
        String state = redis.opsForValue().get(mobileFlowKey(userId));
        if (state == null || !state.startsWith("NEW_SENT:")) {
            throw new TenantProfileException(TenantProfileException.Code.MOBILE_CHANGE_ILLEGAL_STATE,
                    "请先获取新手机验证码");
        }
        String newMobile = state.substring("NEW_SENT:".length());
        if (!verificationCodeService().verify(VerificationPurpose.PHONE_CHANGE_NEW, newMobile, code)) {
            throw new TenantProfileException(TenantProfileException.Code.MOBILE_CHANGE_CODE_INVALID, "新手机验证码错误");
        }
        u.setMobile(newMobile);
        u.setUpdatedAt(LocalDateTime.now());
        u.setUpdatedBy(userId);
        userCommandService.updateById(u);
        redis.delete(mobileFlowKey(userId));
        log.info("tenant.mobile.changed tenantId={} userId={} newMobileLast4=****{}",
                tenantId, userId, newMobile.substring(newMobile.length() - 4));
    }

    // ===== 改邮箱 · step-up 前置身份验证后（发码到新邮箱 → 校验唯一性并更新）=====

    @Override
    public MobileChangeStep sendNewEmailCode(long tenantId, long userId, String newEmail) {
        SysUser u = requireUser(tenantId, userId);
        String email = newEmail == null ? "" : newEmail.trim();
        if (!EMAIL_RE.matcher(email).matches()) {
            throw new TenantProfileException(TenantProfileException.Code.EMAIL_CHANGE_INVALID_NEW, "新邮箱格式错误");
        }
        if (email.equalsIgnoreCase(u.getEmail())) {
            throw new TenantProfileException(TenantProfileException.Code.EMAIL_CHANGE_INVALID_NEW, "新邮箱与原邮箱相同");
        }
        VerificationCodeService.SentResult sent = verificationCodeService().send(
                VerificationPurpose.EMAIL_CHANGE_NEW, Channel.EMAIL, email, userId, tenantId);
        redis.opsForValue().set(emailFlowKey(userId), "NEW_SENT:" + email, MOBILE_FLOW_TTL);
        return new MobileChangeStep(sent.maskedTarget(), sent.ttlSeconds(), sent.resendAfterSeconds());
    }

    @Override
    public void confirmNewEmail(long tenantId, long userId, String code) {
        SysUser u = requireUser(tenantId, userId);
        String state = redis.opsForValue().get(emailFlowKey(userId));
        if (state == null || !state.startsWith("NEW_SENT:")) {
            throw new TenantProfileException(TenantProfileException.Code.EMAIL_CHANGE_ILLEGAL_STATE,
                    "请先获取新邮箱验证码");
        }
        String newEmail = state.substring("NEW_SENT:".length());
        if (!verificationCodeService().verify(VerificationPurpose.EMAIL_CHANGE_NEW, newEmail, code)) {
            throw new TenantProfileException(TenantProfileException.Code.EMAIL_CHANGE_CODE_INVALID, "新邮箱验证码错误");
        }
        // 租户内邮箱唯一：确定性密文等值查，排除自己（在租户上下文内经 MP TenantLine 自动限本租户）
        Long conflictId = userMapper.selectObjs(new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getId)
                        .apply("email = {0}", sensitiveFieldCipher.encrypt(newEmail))
                        .ne(SysUser::getId, userId)
                        .last("LIMIT 1")).stream()
                .findFirst().map(o -> ((Number) o).longValue()).orElse(null);
        if (conflictId != null) {
            throw new TenantProfileException(TenantProfileException.Code.EMAIL_CONFLICT,
                    "该邮箱已被租户内其他成员使用");
        }
        u.setEmail(newEmail);
        u.setUpdatedAt(LocalDateTime.now());
        u.setUpdatedBy(userId);
        userCommandService.updateById(u);
        redis.delete(emailFlowKey(userId));
        log.info("tenant.email.changed tenantId={} userId={}", tenantId, userId);
    }

    // ===== 内部 =====

    private SysUser requireUser(long tenantId, long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null || u.getTenantId() == null || u.getTenantId() != tenantId) {
            throw new TenantProfileException(TenantProfileException.Code.USER_NOT_FOUND, "用户不存在");
        }
        return u;
    }

    private List<UserRoleQueryService.RoleBrief> loadRoles(long tenantId, long userId) {
        return userRoleQuery.rolesOfUser(tenantId, userId);
    }

    /**
     * 由 {@code ancestors}（形如 {@code /1/3/}）解析上级名称，拼成 "根 / 中 / 当前" 路径。
     * 上级一律取自入参的整租户部门快照，不查库；快照中缺失的段（如虚根 0、已删部门）跳过。
     */
    private String buildDeptPath(DeptInfo dept, Map<Long, DeptInfo> depts) {
        List<String> parts = new ArrayList<>();
        String ancestors = dept.ancestors();
        if (ancestors != null && !ancestors.isBlank()) {
            for (String seg : ancestors.split("/")) {
                if (seg.isBlank()) {
                    continue;
                }
                try {
                    DeptInfo anc = depts.get(Long.parseLong(seg));
                    if (anc != null) {
                        parts.add(anc.name());
                    }
                } catch (NumberFormatException ignore) {
                    // 容错：非法段跳过
                }
            }
        }
        parts.add(dept.name());
        return String.join(" / ", parts);
    }

    private String dataScopeLabel(long tenantId, List<UserRoleQueryService.RoleBrief> roles, boolean owner) {
        if (owner) {
            return "全部数据";
        }
        DataScope widest = null;
        for (UserRoleQueryService.RoleBrief r : roles) {
            // 角色默认范围读 sys_role_data_scope NULL-action 行（sys_role.data_scope 已退役）
            DataScope s = defaultScopeStore.get(tenantId, r.id());
            widest = widest == null ? s : DataScope.widest(widest, s);
        }
        if (widest == null) {
            return "仅本人";
        }
        return switch (widest) {
            case ALL -> "全部数据";
            case DEPT_AND_BELOW -> "本部门及下级";
            case DEPT_ONLY -> "仅本部门";
            case SELF -> "仅本人";
        };
    }

    private String resolveAvatarUrl(String avatarFileId) {
        if (avatarFileId == null || avatarFileId.isBlank()) {
            return null;
        }
        try {
            return fileService.signedDownloadUrl(avatarFileId, AVATAR_URL_TTL);
        } catch (RuntimeException e) {
            log.warn("tenant.profile.avatarUrl.fail fileId={} reason={}", avatarFileId, e.getMessage());
            return null;
        }
    }

    private static String statusLabel(Integer status) {
        return status != null && status == 1 ? "active" : "disabled";
    }

    private static String mobileFlowKey(long userId) {
        return MOBILE_FLOW_KEY + userId;
    }

    private static String emailFlowKey(long userId) {
        return EMAIL_FLOW_KEY + userId;
    }
}
