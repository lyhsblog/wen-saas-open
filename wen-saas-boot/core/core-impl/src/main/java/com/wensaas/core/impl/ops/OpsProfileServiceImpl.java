package com.wensaas.core.impl.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.ops.OpsLoginLogItem;
import com.wensaas.core.api.ops.OpsProfile;
import com.wensaas.core.api.ops.OpsProfileException;
import com.wensaas.core.api.ops.OpsProfileService;
import com.wensaas.core.api.ops.OpsSessionItem;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.verification.VerificationCodeService;
import com.wensaas.core.api.verification.VerificationPurpose;
import com.wensaas.core.impl.audit.log.SysLoginLog;
import com.wensaas.core.impl.audit.log.SysLoginLogMapper;
import com.wensaas.core.impl.auth.AuthProperties;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.auth.password.PasswordStrengthValidator;
import com.wensaas.core.impl.auth.password.SecurityPolicySettings;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.session.Session;
import com.wensaas.core.impl.auth.mfa.MfaService;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.user.UserCommandService;
import com.wensaas.core.impl.auth.login.UserAgents;
import com.wensaas.core.impl.permission.role.SysRole;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.role.SysUserRole;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 运营个人中心服务实现。
 *
 * <p>所有 DB 操作绕过 MP TenantLine（{@code @CrossTenantQuery}），以显式 (tenant_id, user_id) 过滤；
 * 运营账号 {@code tenant_id=0}，无环境租户上下文。
 */
@CrossTenantQuery("OpsProfileService: 运营个人中心，显式 tenant_id 过滤")
public class OpsProfileServiceImpl implements OpsProfileService {

    private static final Logger log = LoggerFactory.getLogger(OpsProfileServiceImpl.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Duration AVATAR_URL_TTL = Duration.ofMinutes(10);
    private static final long AVATAR_MAX_BYTES = 1024L * 1024L; // 1MB
    private static final List<String> AVATAR_MIME = List.of("image/png", "image/jpeg", "image/jpg");
    private static final String DATA_SCOPE_ALL = "全部数据";

    private final SysUserMapper userMapper;
    private final UserCommandService userCommandService;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SessionStore sessionStore;
    private final PasswordHasher passwordHasher;
    private final FileService fileService;
    private final AuthProperties authProperties;
    private final ObjectProvider<MfaService> mfaServiceProvider;
    private final ObjectProvider<VerificationCodeService> verificationCodeServiceProvider;
    private final StringRedisTemplate redis;
    private final SecurityPolicySettings securityPolicy;

    private static final String MOBILE_FLOW_KEY = "wen-saas-boot:mobile-change:";
    private static final Duration MOBILE_FLOW_TTL = Duration.ofMinutes(10);
    private static final Pattern CN_MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    public OpsProfileServiceImpl(SysUserMapper userMapper,
                                 UserCommandService userCommandService,
                                 SysUserRoleMapper userRoleMapper,
                                 SysRoleMapper roleMapper,
                                 SysLoginLogMapper loginLogMapper,
                                 SessionStore sessionStore,
                                 PasswordHasher passwordHasher,
                                 FileService fileService,
                                 AuthProperties authProperties,
                                 ObjectProvider<MfaService> mfaServiceProvider,
                                 ObjectProvider<VerificationCodeService> verificationCodeServiceProvider,
                                 StringRedisTemplate redis,
                                 SecurityPolicySettings securityPolicy) {
        this.userMapper = userMapper;
        this.userCommandService = userCommandService;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.loginLogMapper = loginLogMapper;
        this.sessionStore = sessionStore;
        this.passwordHasher = passwordHasher;
        this.fileService = fileService;
        this.authProperties = authProperties;
        this.mfaServiceProvider = mfaServiceProvider;
        this.verificationCodeServiceProvider = verificationCodeServiceProvider;
        this.redis = redis;
        this.securityPolicy = securityPolicy;
    }

    private VerificationCodeService verificationCodeService() {
        VerificationCodeService svc = verificationCodeServiceProvider.getIfAvailable();
        if (svc == null) {
            throw new IllegalStateException(
                    "改手机号双通道需启用通知模块（wen-saas-boot.notification.enabled=true）");
        }
        return svc;
    }

    @Override
    public OpsProfile.Basic getBasic(long tenantId, long userId) {
        SysUser u = requireUser(tenantId, userId);
        String avatarUrl = resolveAvatarUrl(u.getAvatarFileId());
        return new OpsProfile.Basic(
                u.getId(), u.getUsername(), u.getEmpNo(), u.getEmail(), u.getRealName(),
                u.getMobile(), u.getGender(), u.getBirthday(), u.getAvatarFileId(), avatarUrl);
    }

    @Override
    public OpsProfile.TeamsRoles getTeamsRoles(long tenantId, long userId) {
        SysUser u = requireUser(tenantId, userId);
        return new OpsProfile.TeamsRoles(
                loadRoles(tenantId, userId), DATA_SCOPE_ALL, statusLabel(u.getStatus()));
    }

    @Override
    public OpsProfile.Security getSecurity(long tenantId, long userId) {
        requireUser(tenantId, userId);
        AuthProperties.LoginAttempt la = authProperties.getLoginAttempt();
        MfaService mfa = mfaServiceProvider.getIfAvailable();
        MfaService.Status status = mfa == null ? null : mfa.status(userId);
        return new OpsProfile.Security(
                authProperties.getTokenTtl().toSeconds(),
                la.getMaxAttempts(), la.getWindowSeconds(),
                authProperties.isOpsMfaEnforced(),
                status != null && status.loginMfaEnabled(),
                status == null || status.loginMethod() == null ? null : status.loginMethod().name());
    }

    @Override
    public OpsProfile.Sso getSso(long tenantId, long userId) {
        requireUser(tenantId, userId);
        return new OpsProfile.Sso(null, false, null);
    }

    @Override
    public void updateProfile(long tenantId, long userId, ProfileUpdate update) {
        SysUser u = requireUser(tenantId, userId);
        // 姓名不可自助修改（仅运营管理员在运营成员管理处变更）；此处只更新性别/生日。
        u.setGender(update.gender());
        u.setBirthday(update.birthday());
        // 头像改由独立「更换头像」入口即传即存（O5-1）；此接口 avatarFileId 为 null 视为未修改，
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
            throw new OpsProfileException(OpsProfileException.Code.INVALID_AVATAR,
                    "头像文件大小不能超过 1MB");
        }
        String mime = upload.mimeType() == null ? "" : upload.mimeType().toLowerCase();
        if (!AVATAR_MIME.contains(mime)) {
            throw new OpsProfileException(OpsProfileException.Code.INVALID_AVATAR,
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
            throw new OpsProfileException(OpsProfileException.Code.WEAK_PASSWORD,
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
    public List<OpsSessionItem> listSessions(long tenantId, long userId, String currentSessionId) {
        return sessionStore.findByUser(tenantId, userId).stream()
                .map(s -> new OpsSessionItem(
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
            throw new OpsProfileException(OpsProfileException.Code.CANNOT_REVOKE_CURRENT_SESSION,
                    "不能下线当前会话，请用其他设备操作");
        }
        Optional<Session> target = sessionStore.findBySessionId(targetSessionId);
        if (target.isEmpty() || target.get().userId() != userId || target.get().tenantId() != tenantId) {
            throw new OpsProfileException(OpsProfileException.Code.SESSION_NOT_FOUND, "会话不存在或不属于当前用户");
        }
        sessionStore.invalidate(targetSessionId);
    }

    @Override
    public void revokeOtherSessions(long tenantId, long userId, String currentSessionId) {
        sessionStore.invalidateAllByUserExcept(tenantId, userId, currentSessionId);
    }

    @Override
    public OpsLoginLogPage listLoginLogs(long tenantId, long userId, int page, int size) {
        Page<SysLoginLog> p = new Page<>(page, size);
        LambdaQueryWrapper<SysLoginLog> wrapper = Wrappers.<SysLoginLog>lambdaQuery()
                .eq(SysLoginLog::getTenantId, tenantId)
                .eq(SysLoginLog::getOperatorId, userId)
                .orderByDesc(SysLoginLog::getCreatedAt);
        IPage<SysLoginLog> result = loginLogMapper.selectPage(p, wrapper);
        List<OpsLoginLogItem> items = result.getRecords().stream()
                .map(r -> new OpsLoginLogItem(
                        r.getId(), r.getAccount(), r.getLoginMethod(), r.getAction(),
                        r.getResult(), r.getFailReason(), r.getIp(), r.getUserAgent(), r.getCreatedAt()))
                .toList();
        return new OpsLoginLogPage(items, result.getTotal());
    }

    private SysUser requireUser(long tenantId, long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null || u.getTenantId() == null || u.getTenantId() != tenantId) {
            throw new OpsProfileException(OpsProfileException.Code.USER_NOT_FOUND, "用户不存在");
        }
        return u;
    }

    private List<OpsProfile.RoleBrief> loadRoles(long tenantId, long userId) {
        List<SysUserRole> links = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getTenantId, tenantId)
                .eq(SysUserRole::getUserId, userId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = links.stream().map(SysUserRole::getRoleId).toList();
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                        .eq(SysRole::getTenantId, tenantId)
                        .in(SysRole::getId, roleIds)).stream()
                .map(r -> new OpsProfile.RoleBrief(r.getCode(), r.getName()))
                .toList();
    }

    private String resolveAvatarUrl(String avatarFileId) {
        if (avatarFileId == null || avatarFileId.isBlank()) {
            return null;
        }
        try {
            return fileService.signedDownloadUrl(avatarFileId, AVATAR_URL_TTL);
        } catch (RuntimeException e) {
            log.warn("ops.profile.avatarUrl.fail fileId={} reason={}", avatarFileId, e.getMessage());
            return null;
        }
    }

    private static String statusLabel(Integer status) {
        return status != null && status == 1 ? "active" : "disabled";
    }

    // ===== 改手机号 · step-up 前置身份验证后（发码到新手机 → 校验并更新）=====

    @Override
    public MobileChangeStep sendNewMobileCode(long tenantId, long userId, String newMobile) {
        SysUser u = requireUser(tenantId, userId);
        if (newMobile == null || !CN_MOBILE.matcher(newMobile).matches()) {
            throw new OpsProfileException(OpsProfileException.Code.MOBILE_CHANGE_INVALID_NEW, "新手机号格式错误");
        }
        if (newMobile.equals(u.getMobile())) {
            throw new OpsProfileException(OpsProfileException.Code.MOBILE_CHANGE_INVALID_NEW, "新手机号与原手机号相同");
        }
        VerificationCodeService.SentResult sent = verificationCodeService().send(
                VerificationPurpose.PHONE_CHANGE_NEW, Channel.SMS, newMobile, userId, tenantId);
        redis.opsForValue().set(flowKey(userId), "NEW_SENT:" + newMobile, MOBILE_FLOW_TTL);
        return new MobileChangeStep(sent.maskedTarget(), sent.ttlSeconds(), sent.resendAfterSeconds());
    }

    @Override
    public void confirmNewMobile(long tenantId, long userId, String code) {
        SysUser u = requireUser(tenantId, userId);
        String state = redis.opsForValue().get(flowKey(userId));
        if (state == null || !state.startsWith("NEW_SENT:")) {
            throw new OpsProfileException(OpsProfileException.Code.MOBILE_CHANGE_ILLEGAL_STATE,
                    "请先获取新手机验证码");
        }
        String newMobile = state.substring("NEW_SENT:".length());
        if (!verificationCodeService().verify(VerificationPurpose.PHONE_CHANGE_NEW, newMobile, code)) {
            throw new OpsProfileException(OpsProfileException.Code.MOBILE_CHANGE_CODE_INVALID, "新手机验证码错误");
        }
        u.setMobile(newMobile);
        u.setUpdatedAt(LocalDateTime.now());
        u.setUpdatedBy(userId);
        userCommandService.updateById(u);
        redis.delete(flowKey(userId));
        log.info("ops.mobile.changed userId={} newMobileMaskedLast4=****{}",
                userId, newMobile.substring(newMobile.length() - 4));
    }

    private static String flowKey(long userId) {
        return MOBILE_FLOW_KEY + userId;
    }
}
