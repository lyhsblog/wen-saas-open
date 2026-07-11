package com.wensaas.core.impl.auth.mfa;

import com.wensaas.core.api.auth.user.AuthenticatedIdentity;
import com.wensaas.core.api.auth.login.MfaMethod;
import com.wensaas.core.impl.auth.login.AuthService;
import com.wensaas.core.impl.auth.login.AuthServiceImpl;

import java.util.List;

/**
 * 安全验证服务：身份验证器（TOTP）绑定 + 登录二步验证开关，两者解耦。
 *
 * <ul>
 *   <li><b>验证器绑定</b>：{@link #bindTotpStart}/{@link #bindTotpConfirm} 独立管理，绑定后同时
 *       服务登录验证与敏感操作 step-up（{@link #isTotpBound}/{@link #verifyTotp}），不动恢复码。</li>
 *   <li><b>登录二步验证</b>：{@link #enableLoginMfaStart}/{@link #enableLoginMfaConfirm} 开启
 *       （验证一次推导通道后发放恢复码）、{@link #disableLoginMfa} 关闭（清恢复码、保留绑定）。
 *       登录通道不落库、每次登录推导：已绑 TOTP → TOTP，否则邮箱验证码（sys_user.email 现值）。</li>
 * </ul>
 *
 * <p>登录流：{@link AuthServiceImpl} 密码通过后，若（运营账号且 ops-mfa-enforced）或用户已开启
 * 开关，则调 {@link #beginLoginChallenge} 返回预认证票据；前端凭票据走
 * {@link #startSetup}（强制模式下的首次配置）或 {@link #sendLoginChallenge}+{@link #verify}
 * （已开启登录），verify 通过才签发会话 token（支持恢复码兜底）。
 */
public interface MfaService {

    // ===== 登录流（AuthServiceImpl 调用）=====

    /** 登录密码通过后发起二步验证，创建预认证票据。method 为推导通道（未开启时为 null）。 */
    BeginResult beginLoginChallenge(AuthenticatedIdentity identity, String account, String loginMethod);

    record BeginResult(boolean enabled, MfaMethod method, String preAuthToken) {
    }

    // ===== 公开端点（凭 preAuthToken）=====

    /** 强制模式首次配置：选定通道。TOTP 返回密钥 + otpauth URI；EMAIL 发送动态码。 */
    SetupChallenge startSetup(String preAuthToken, MfaMethod method);

    record SetupChallenge(MfaMethod method, String totpSecret, String otpauthUri, String maskedTarget) {
    }

    /** 已开启登录：EMAIL 通道发送登录动态码；TOTP 不发。通道服务端推导。 */
    ChallengeInfo sendLoginChallenge(String preAuthToken);

    record ChallengeInfo(MfaMethod method, String maskedTarget) {
    }

    /** 校验（setup 完成 / 登录挑战）。成功创建会话；首次 setup 额外返回一次性恢复码。 */
    VerifyOutcome verify(String preAuthToken, String code);

    record VerifyOutcome(AuthService.LoginResult login, List<String> recoveryCodes) {
    }

    // ===== 已登录态（个人中心）=====

    Status status(long userId);

    /**
     * @param loginMfaEnabled 登录二步验证开关
     * @param totpBound       是否已绑定身份验证器
     * @param loginMethod     推导的登录验证通道（已绑 TOTP→TOTP，否则有邮箱→EMAIL，否则 null）
     */
    record Status(boolean loginMfaEnabled, boolean totpBound, MfaMethod loginMethod) {
    }

    /** 该用户是否已绑定身份验证器（totp_secret 非空）。供敏感操作 step-up 判断 TOTP 通道是否可用。 */
    boolean isTotpBound(long userId);

    /** 校验用户身份验证器动态码（未绑定返回 false）。供敏感操作 step-up 复用。 */
    boolean verifyTotp(long userId, String code);

    /** 绑定/重新绑定身份验证器 · 起手：生成密钥 + otpauth URI（Redis 暂存待确认）。 */
    SetupChallenge bindTotpStart(long userId, long tenantId);

    /** 绑定/重新绑定身份验证器 · 确认：校验新验证器动态码 → 落 totp_secret。不动恢复码。 */
    void bindTotpConfirm(long userId, long tenantId, String code);

    /**
     * 开启登录二步验证 · 起手：推导通道（已绑 TOTP 不发码；否则邮箱发码；两者皆无抛 TARGET_MISSING）。
     * 已开启抛 ILLEGAL_STATE。
     */
    ChallengeInfo enableLoginMfaStart(long userId, long tenantId);

    /** 开启登录二步验证 · 确认：按推导通道验码 → 置开 + 重生成恢复码（一次性返回）。 */
    List<String> enableLoginMfaConfirm(long userId, long tenantId, String code);

    /** 关闭登录二步验证：置关 + 清恢复码；保留验证器绑定（无绑定则删行）。调用方需先完成身份验证。 */
    void disableLoginMfa(long userId, long tenantId);
}
