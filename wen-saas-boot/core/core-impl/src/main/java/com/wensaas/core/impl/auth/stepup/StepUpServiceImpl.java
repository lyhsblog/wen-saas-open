package com.wensaas.core.impl.auth.stepup;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.auth.login.MfaMethod;
import com.wensaas.core.api.auth.stepup.StepUpException;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.verification.VerificationCodeService;
import com.wensaas.core.api.verification.VerificationPurpose;
import com.wensaas.core.impl.auth.mfa.MfaService;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * {@link StepUpService} 实现。TOTP 复用 {@link MfaService}，短信 / 邮件复用 {@link VerificationCodeService}，
 * 票据走 {@link StepUpTicketStore}（Redis）。
 */
public class StepUpServiceImpl implements StepUpService {

    private static final Logger log = LoggerFactory.getLogger(StepUpServiceImpl.class);

    private final MfaService mfaService;
    private final VerificationCodeService verificationCodeService;
    private final SysUserMapper userMapper;
    private final StepUpTicketStore ticketStore;

    public StepUpServiceImpl(MfaService mfaService,
                             VerificationCodeService verificationCodeService,
                             SysUserMapper userMapper,
                             StepUpTicketStore ticketStore) {
        this.mfaService = mfaService;
        this.verificationCodeService = verificationCodeService;
        this.userMapper = userMapper;
        this.ticketStore = ticketStore;
    }

    @Override
    public Channels availability(long userId) {
        SysUser user = loadUser(userId);
        String mobile = user == null ? null : user.getMobile();
        String email = user == null ? null : user.getEmail();
        boolean totp = mfaService.isTotpBound(userId);
        return new Channels(
                new ChannelAvail(totp, null),
                new ChannelAvail(isPresent(mobile), maskMobile(mobile)),
                new ChannelAvail(isPresent(email), maskEmail(email)));
    }

    @Override
    public Challenge challenge(long userId, long tenantId, MfaMethod channel) {
        if (channel == MfaMethod.TOTP) {
            // TOTP 无需服务端发码；前端直接读身份验证器动态码
            if (!mfaService.isTotpBound(userId)) {
                throw new StepUpException(StepUpException.Code.CHANNEL_UNAVAILABLE, "尚未绑定身份验证器");
            }
            return new Challenge(MfaMethod.TOTP, null, 0, 0);
        }
        SysUser user = loadUser(userId);
        String target = channel == MfaMethod.SMS ? contactOrThrow(user, true) : contactOrThrow(user, false);
        VerificationCodeService.SentResult sent = verificationCodeService.send(
                VerificationPurpose.STEP_UP, channelOf(channel), target, userId, tenantId);
        return new Challenge(channel, sent.maskedTarget(), sent.ttlSeconds(), sent.resendAfterSeconds());
    }

    @Override
    public Ticket verify(long userId, long tenantId, MfaMethod channel, String code) {
        boolean ok;
        if (channel == MfaMethod.TOTP) {
            ok = mfaService.verifyTotp(userId, code);
        } else {
            SysUser user = loadUser(userId);
            String target = channel == MfaMethod.SMS ? contactOrThrow(user, true) : contactOrThrow(user, false);
            ok = verificationCodeService.verify(VerificationPurpose.STEP_UP, target, code);
        }
        if (!ok) {
            throw new StepUpException(StepUpException.Code.CODE_INVALID, "验证码错误");
        }
        String token = ticketStore.issue(userId);
        log.info("stepup.verified userId={} channel={}", userId, channel);
        return new Ticket(token, ticketStore.ttl().toSeconds());
    }

    @Override
    public void requireValid(long userId, String stepUpToken) {
        Long owner = ticketStore.ownerOf(stepUpToken);
        if (owner == null || owner != userId) {
            throw new StepUpException(StepUpException.Code.TICKET_INVALID, "身份验证已过期，请重新验证");
        }
    }

    @Override
    public void consume(long userId, String stepUpToken) {
        requireValid(userId, stepUpToken);
        ticketStore.consume(stepUpToken);
    }

    // ===== 内部 =====

    private String contactOrThrow(SysUser user, boolean mobile) {
        String v = user == null ? null : (mobile ? user.getMobile() : user.getEmail());
        if (!isPresent(v)) {
            throw new StepUpException(StepUpException.Code.CHANNEL_UNAVAILABLE,
                    mobile ? "账号未设置手机号" : "账号未设置邮箱");
        }
        return v;
    }

    private static Channel channelOf(MfaMethod method) {
        return method == MfaMethod.SMS ? Channel.SMS : Channel.EMAIL;
    }

    private static boolean isPresent(String s) {
        return s != null && !s.isBlank();
    }

    private static String maskMobile(String mobile) {
        if (!isPresent(mobile) || mobile.length() < 7) {
            return isPresent(mobile) ? "****" : null;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private static String maskEmail(String email) {
        if (!isPresent(email)) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "*" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** 在 MyBatis-Plus 多租户拦截器跳过的上下文中读 sys_user（自助场景 userId 全局唯一）。 */
    private SysUser loadUser(long userId) {
        return crossTenant(() -> userMapper.selectById(userId));
    }

    private <T> T crossTenant(Supplier<T> action) {
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        try {
            return action.get();
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }
}
