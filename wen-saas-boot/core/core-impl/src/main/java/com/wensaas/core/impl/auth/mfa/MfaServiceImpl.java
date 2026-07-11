package com.wensaas.core.impl.auth.mfa;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.audit.LoginAuditEvent;
import com.wensaas.core.api.auth.user.AuthenticatedIdentity;
import com.wensaas.core.api.auth.login.MfaException;
import com.wensaas.core.api.auth.login.MfaMethod;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.verification.VerificationCodeService;
import com.wensaas.core.api.verification.VerificationPurpose;
import com.wensaas.core.impl.auth.login.AuthService;
import com.wensaas.core.impl.auth.session.JwtTokenProvider;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.user.UserCommandService;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

/**
 * 安全验证服务实现。详见 {@link MfaService}。
 */
public class MfaServiceImpl implements MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaServiceImpl.class);
    private static final String TOTP_BIND_KEY_PREFIX = "wen-saas-boot:totp-bind:";

    private final SysMfaConfigMapper configMapper;
    private final SysMfaRecoveryCodeMapper recoveryMapper;
    private final SysUserMapper userMapper;
    private final UserCommandService userCommandService;
    private final MfaPreAuthStore preAuthStore;
    private final MfaSecretCipher secretCipher;
    private final VerificationCodeService verificationCodeService;
    private final SessionStore sessionStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogRecorder auditLogRecorder;
    private final StringRedisTemplate redis;
    private final MfaProperties props;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier totpVerifier =
            new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
    private final RecoveryCodeGenerator recoveryCodeGenerator = new RecoveryCodeGenerator();

    public MfaServiceImpl(SysMfaConfigMapper configMapper,
                          SysMfaRecoveryCodeMapper recoveryMapper,
                          SysUserMapper userMapper,
                          UserCommandService userCommandService,
                          MfaPreAuthStore preAuthStore,
                          MfaSecretCipher secretCipher,
                          VerificationCodeService verificationCodeService,
                          SessionStore sessionStore,
                          JwtTokenProvider jwtTokenProvider,
                          AuditLogRecorder auditLogRecorder,
                          StringRedisTemplate redis,
                          MfaProperties props) {
        this.configMapper = configMapper;
        this.recoveryMapper = recoveryMapper;
        this.userMapper = userMapper;
        this.userCommandService = userCommandService;
        this.preAuthStore = preAuthStore;
        this.secretCipher = secretCipher;
        this.verificationCodeService = verificationCodeService;
        this.sessionStore = sessionStore;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditLogRecorder = auditLogRecorder;
        this.redis = redis;
        this.props = props;
    }

    // ===== 登录流 =====

    @Override
    public BeginResult beginLoginChallenge(AuthenticatedIdentity identity, String account, String loginMethod) {
        SysMfaConfig config = findConfig(identity.userId());
        boolean enabled = isLoginEnabled(config);
        MfaMethod method = enabled ? deriveLoginMethod(config, loadUser(identity.userId())) : null;
        String token = preAuthStore.create(
                MfaPreAuth.ofLogin(identity, account, loginMethod, enabled));
        return new BeginResult(enabled, method, token);
    }

    @Override
    public SetupChallenge startSetup(String preAuthToken, MfaMethod method) {
        MfaPreAuth preAuth = requirePreAuth(preAuthToken);
        if (method == null) {
            throw new MfaException(MfaException.Code.METHOD_REQUIRED, "未选择验证方式");
        }
        SysUser user = loadUser(preAuth.userId());
        SetupTentative t = buildTentative(method, user, preAuth.userId(), preAuth.tenantId());
        preAuthStore.update(preAuthToken, preAuth.withSetup(method.name(), t.secret(), t.target()));
        return new SetupChallenge(method, t.secret(), t.otpauthUri(), t.maskedTarget());
    }

    @Override
    public ChallengeInfo sendLoginChallenge(String preAuthToken) {
        MfaPreAuth preAuth = requirePreAuth(preAuthToken);
        if (!preAuth.enabled()) {
            throw new MfaException(MfaException.Code.NOT_CONFIGURED, "尚未开启登录二步验证，请先 setup");
        }
        SysMfaConfig config = findConfig(preAuth.userId());
        SysUser user = loadUser(preAuth.userId());
        MfaMethod method = deriveLoginMethod(config, user);
        if (method == MfaMethod.TOTP) {
            return new ChallengeInfo(MfaMethod.TOTP, null);
        }
        if (method == null) {
            throw new MfaException(MfaException.Code.TARGET_MISSING, "账号未设置邮箱且未绑定身份验证器");
        }
        VerificationCodeService.SentResult sent = verificationCodeService.send(
                VerificationPurpose.MFA_LOGIN, Channel.EMAIL, user.getEmail(),
                preAuth.userId(), preAuth.tenantId());
        return new ChallengeInfo(method, sent.maskedTarget());
    }

    @Override
    public VerifyOutcome verify(String preAuthToken, String code) {
        MfaPreAuth preAuth = requirePreAuth(preAuthToken);
        List<String> recoveryCodes = null;
        if (preAuth.enabled()) {
            SysMfaConfig config = findConfig(preAuth.userId());
            SysUser user = loadUser(preAuth.userId());
            if (!verifyByDerivedMethod(config, user, code, VerificationPurpose.MFA_LOGIN)
                    && !consumeRecoveryCode(preAuth.userId(), code)) {
                throw new MfaException(MfaException.Code.CODE_INVALID, "验证码错误");
            }
        } else {
            if (preAuth.setupMethod() == null) {
                throw new MfaException(MfaException.Code.ILLEGAL_STATE, "请先发起二步验证配置");
            }
            MfaMethod method = MfaMethod.valueOf(preAuth.setupMethod());
            boolean ok = method == MfaMethod.TOTP
                    ? totpVerifier.isValidCode(preAuth.tentativeSecret(), code)
                    : verificationCodeService.verify(VerificationPurpose.MFA_SETUP, preAuth.setupTarget(), code);
            if (!ok) {
                throw new MfaException(MfaException.Code.CODE_INVALID, "验证码错误");
            }
            // 强制模式首次配置 = （TOTP 时）绑定验证器 + 开启开关，一步完成
            if (method == MfaMethod.TOTP) {
                upsertTotpSecret(preAuth.userId(), preAuth.tenantId(),
                        secretCipher.encrypt(preAuth.tentativeSecret()));
            }
            setLoginEnabled(preAuth.userId(), preAuth.tenantId(), true);
            recoveryCodes = regenerateRecoveryCodes(preAuth.userId(), preAuth.tenantId());
        }
        AuthService.LoginResult login = completeLogin(preAuth);
        preAuthStore.consume(preAuthToken);
        return new VerifyOutcome(login, recoveryCodes);
    }

    // ===== 已登录态（个人中心）=====

    @Override
    public Status status(long userId) {
        SysMfaConfig config = findConfig(userId);
        boolean enabled = isLoginEnabled(config);
        return new Status(enabled, isBound(config), deriveLoginMethod(config, loadUser(userId)));
    }

    @Override
    public boolean isTotpBound(long userId) {
        return isBound(findConfig(userId));
    }

    @Override
    public boolean verifyTotp(long userId, String code) {
        SysMfaConfig config = findConfig(userId);
        if (!isBound(config)) {
            return false;
        }
        return totpVerifier.isValidCode(secretCipher.decrypt(config.getTotpSecret()), code);
    }

    @Override
    public SetupChallenge bindTotpStart(long userId, long tenantId) {
        SysUser user = loadUser(userId);
        SetupTentative t = buildTentative(MfaMethod.TOTP, user, userId, tenantId);
        redis.opsForValue().set(TOTP_BIND_KEY_PREFIX + userId,
                JSON.toJSONString(new TotpBindState(t.secret())),
                props.getReconfigureTtl());
        return new SetupChallenge(MfaMethod.TOTP, t.secret(), t.otpauthUri(), t.maskedTarget());
    }

    @Override
    public void bindTotpConfirm(long userId, long tenantId, String code) {
        String raw = redis.opsForValue().get(TOTP_BIND_KEY_PREFIX + userId);
        if (raw == null) {
            throw new MfaException(MfaException.Code.ILLEGAL_STATE, "请先发起验证器绑定");
        }
        TotpBindState state = JSON.parseObject(raw, TotpBindState.class);
        if (!totpVerifier.isValidCode(state.secret(), code)) {
            throw new MfaException(MfaException.Code.CODE_INVALID, "验证码错误");
        }
        upsertTotpSecret(userId, tenantId, secretCipher.encrypt(state.secret()));
        redis.delete(TOTP_BIND_KEY_PREFIX + userId);
        log.info("mfa.totp.bound userId={} tenantId={}", userId, tenantId);
    }

    @Override
    public ChallengeInfo enableLoginMfaStart(long userId, long tenantId) {
        SysMfaConfig config = findConfig(userId);
        if (isLoginEnabled(config)) {
            throw new MfaException(MfaException.Code.ILLEGAL_STATE, "登录二步验证已开启");
        }
        SysUser user = loadUser(userId);
        MfaMethod method = deriveLoginMethod(config, user);
        if (method == MfaMethod.TOTP) {
            return new ChallengeInfo(MfaMethod.TOTP, null);
        }
        if (method == null) {
            throw new MfaException(MfaException.Code.TARGET_MISSING, "账号未设置邮箱，且未绑定身份验证器");
        }
        VerificationCodeService.SentResult sent = verificationCodeService.send(
                VerificationPurpose.MFA_SETUP, Channel.EMAIL, user.getEmail(), userId, tenantId);
        return new ChallengeInfo(method, sent.maskedTarget());
    }

    @Override
    public List<String> enableLoginMfaConfirm(long userId, long tenantId, String code) {
        SysMfaConfig config = findConfig(userId);
        if (isLoginEnabled(config)) {
            throw new MfaException(MfaException.Code.ILLEGAL_STATE, "登录二步验证已开启");
        }
        SysUser user = loadUser(userId);
        if (!verifyByDerivedMethod(config, user, code, VerificationPurpose.MFA_SETUP)) {
            throw new MfaException(MfaException.Code.CODE_INVALID, "验证码错误");
        }
        setLoginEnabled(userId, tenantId, true);
        log.info("mfa.login.enabled userId={} tenantId={}", userId, tenantId);
        return regenerateRecoveryCodes(userId, tenantId);
    }

    @Override
    public void disableLoginMfa(long userId, long tenantId) {
        SysMfaConfig config = findConfig(userId);
        if (config == null) {
            return;
        }
        if (config.getTotpSecret() == null) {
            configMapper.deleteById(config.getId());
        } else {
            setLoginEnabled(userId, tenantId, false);
        }
        recoveryMapper.delete(Wrappers.<SysMfaRecoveryCode>lambdaQuery()
                .eq(SysMfaRecoveryCode::getUserId, userId));
        log.info("mfa.login.disabled userId={} tenantId={}", userId, tenantId);
    }

    // ===== 内部 =====

    private static boolean isLoginEnabled(SysMfaConfig config) {
        return config != null && Boolean.TRUE.equals(config.getLoginMfaEnabled());
    }

    private static boolean isBound(SysMfaConfig config) {
        return config != null && config.getTotpSecret() != null && !config.getTotpSecret().isBlank();
    }

    /** 登录验证通道推导：已绑验证器 → TOTP；否则有邮箱 → EMAIL（用 sys_user.email 现值）；否则 null。 */
    private static MfaMethod deriveLoginMethod(SysMfaConfig config, SysUser user) {
        if (isBound(config)) {
            return MfaMethod.TOTP;
        }
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return MfaMethod.EMAIL;
        }
        return null;
    }

    /** 按推导通道校验动态码（TOTP → 验证器码；EMAIL → 指定 purpose 的邮箱验证码）。 */
    private boolean verifyByDerivedMethod(SysMfaConfig config, SysUser user, String code,
                                          VerificationPurpose purpose) {
        MfaMethod method = deriveLoginMethod(config, user);
        if (method == MfaMethod.TOTP) {
            return totpVerifier.isValidCode(secretCipher.decrypt(config.getTotpSecret()), code);
        }
        if (method == MfaMethod.EMAIL) {
            return verificationCodeService.verify(purpose, user.getEmail(), code);
        }
        return false;
    }

    private SetupTentative buildTentative(MfaMethod method, SysUser user, long userId, long tenantId) {
        if (method == MfaMethod.TOTP) {
            String secret = secretGenerator.generate();
            String label = user.getEmail() != null && !user.getEmail().isBlank()
                    ? user.getEmail() : user.getUsername();
            QrData data = new QrData.Builder()
                    .label(label)
                    .secret(secret)
                    .issuer(props.getIssuer())
                    .algorithm(HashingAlgorithm.SHA1)
                    .digits(6)
                    .period(30)
                    .build();
            return new SetupTentative(secret, data.getUri(), null, null);
        }
        String target = method == MfaMethod.SMS ? user.getMobile() : user.getEmail();
        if (target == null || target.isBlank()) {
            throw new MfaException(MfaException.Code.TARGET_MISSING,
                    method == MfaMethod.SMS ? "账号未设置手机号" : "账号未设置邮箱");
        }
        VerificationCodeService.SentResult sent = verificationCodeService.send(
                VerificationPurpose.MFA_SETUP, channelOf(method), target, userId, tenantId);
        return new SetupTentative(null, null, target, sent.maskedTarget());
    }

    /** 落验证器密钥（无行则建行，登录开关保持原值/默认关）。 */
    private void upsertTotpSecret(long userId, long tenantId, String totpSecretCipher) {
        Instant now = Instant.now();
        SysMfaConfig existing = findConfig(userId);
        if (existing == null) {
            SysMfaConfig c = new SysMfaConfig();
            c.setTenantId(tenantId);
            c.setUserId(userId);
            c.setTotpSecret(totpSecretCipher);
            c.setLoginMfaEnabled(false);
            c.setCreatedAt(now);
            c.setUpdatedAt(now);
            configMapper.insert(c);
        } else {
            configMapper.update(null, Wrappers.<SysMfaConfig>lambdaUpdate()
                    .eq(SysMfaConfig::getId, existing.getId())
                    .set(SysMfaConfig::getTotpSecret, totpSecretCipher)
                    .set(SysMfaConfig::getUpdatedAt, now));
        }
    }

    /** 置登录二步验证开关（无行则建行）。 */
    private void setLoginEnabled(long userId, long tenantId, boolean enabled) {
        Instant now = Instant.now();
        SysMfaConfig existing = findConfig(userId);
        if (existing == null) {
            SysMfaConfig c = new SysMfaConfig();
            c.setTenantId(tenantId);
            c.setUserId(userId);
            c.setLoginMfaEnabled(enabled);
            c.setEnabledAt(enabled ? now : null);
            c.setCreatedAt(now);
            c.setUpdatedAt(now);
            configMapper.insert(c);
        } else {
            configMapper.update(null, Wrappers.<SysMfaConfig>lambdaUpdate()
                    .eq(SysMfaConfig::getId, existing.getId())
                    .set(SysMfaConfig::getLoginMfaEnabled, enabled)
                    .set(SysMfaConfig::getEnabledAt, enabled ? now : null)
                    .set(SysMfaConfig::getUpdatedAt, now));
        }
    }

    private List<String> regenerateRecoveryCodes(long userId, long tenantId) {
        recoveryMapper.delete(Wrappers.<SysMfaRecoveryCode>lambdaQuery()
                .eq(SysMfaRecoveryCode::getUserId, userId));
        String[] codes = recoveryCodeGenerator.generateCodes(props.getRecoveryCodeCount());
        Instant now = Instant.now();
        for (String code : codes) {
            SysMfaRecoveryCode rc = new SysMfaRecoveryCode();
            rc.setTenantId(tenantId);
            rc.setUserId(userId);
            rc.setCodeHash(sha256(code));
            rc.setCreatedAt(now);
            recoveryMapper.insert(rc);
        }
        return Arrays.asList(codes);
    }

    private boolean consumeRecoveryCode(long userId, String code) {
        LambdaUpdateWrapper<SysMfaRecoveryCode> update = Wrappers.<SysMfaRecoveryCode>lambdaUpdate()
                .eq(SysMfaRecoveryCode::getUserId, userId)
                .eq(SysMfaRecoveryCode::getCodeHash, sha256(code))
                .isNull(SysMfaRecoveryCode::getUsedAt)
                .set(SysMfaRecoveryCode::getUsedAt, Instant.now());
        return recoveryMapper.update(null, update) > 0;
    }

    private AuthService.LoginResult completeLogin(MfaPreAuth preAuth) {
        AuthenticatedIdentity identity = preAuth.toIdentity();
        String sessionId = sessionStore.createSession(identity);
        String token = jwtTokenProvider.issue(sessionId);
        userCommandService.touchLastLogin(preAuth.userId());
        crossTenant(() -> {
            recordLoginSuccess(preAuth);
            return null;
        });
        UserPrincipal principal = new UserPrincipal(identity.userId(), identity.tenantId(),
                identity.username(), sessionId, identity.tenantOwner(), identity.mainDeptId(),
                false, null, identity.realName());
        log.info("mfa.verify.success userId={} tenantId={}", identity.userId(), identity.tenantId());
        return AuthService.LoginResult.completed(token, jwtTokenProvider.tokenTtl(), principal);
    }

    private void recordLoginSuccess(MfaPreAuth preAuth) {
        String rid = RequestContext.get();
        auditLogRecorder.recordLogin(new LoginAuditEvent(
                null, preAuth.tenantId(), rid != null && !rid.isBlank() ? rid : "system",
                preAuth.userId(), preAuth.username(), preAuth.mainDeptId(),
                preAuth.account(), preAuth.loginMethod(), "login", "success",
                null, null, null, Instant.now()));
    }

    private SysMfaConfig findConfig(long userId) {
        // 登录期（status / beginLoginChallenge）尚未建立 TenantContext，且本查询亦可能在调用方
        // @CrossTenantQuery 的 ignore 被前序 clearIgnoreStrategy 清掉后执行。user_id 全局唯一，
        // 跨租户读一行即可——在查询处就近开启 tenantLine 忽略，不依赖外层 ignore 是否仍在。
        return crossTenant(() -> configMapper.selectOne(Wrappers.<SysMfaConfig>lambdaQuery()
                .eq(SysMfaConfig::getUserId, userId)));
    }

    private MfaPreAuth requirePreAuth(String token) {
        return preAuthStore.find(token).orElseThrow(() ->
                new MfaException(MfaException.Code.PREAUTH_INVALID, "预认证票据无效或已过期，请重新登录"));
    }

    private SysUser loadUser(long userId) {
        return crossTenant(() -> userMapper.selectById(userId));
    }

    private static Channel channelOf(MfaMethod method) {
        return method == MfaMethod.SMS ? Channel.SMS : Channel.EMAIL;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 在 MyBatis-Plus 多租户拦截器跳过的上下文中执行（公开端点无 TenantContext 时访问 sys_user）。 */
    private <T> T crossTenant(Supplier<T> action) {
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        try {
            return action.get();
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    private record SetupTentative(String secret, String otpauthUri, String target, String maskedTarget) {
    }

    private record TotpBindState(String secret) {
    }
}
