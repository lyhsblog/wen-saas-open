package com.wensaas.core.impl.auth.password;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.auth.login.PasswordResetException;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.user.UserCommandService;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 公开密码重置的业务编排。
 *
 * <p>原先这套 token 校验 + 密码哈希 + 账号激活 + 标记 token 的多步写逻辑内联在
 * {@code PasswordResetPublicController} 里，既越层直接操作 Mapper，又是无事务的安全敏感写路径
 * （中途失败会留下「密码已改但 token 未标记 / 账号未激活」的不一致）。本类把编排收回 core-impl：
 * <ul>
 *   <li>{@code @CrossTenantQuery} 收到方法级（保持窄作用域）：公开接口无租户上下文，
 *       按 token 跨租户定位租户 / 用户；</li>
 *   <li>写路径 {@link #submitReset} 加 {@code @Transactional}：更新密码 + 激活 + 标记 token 要么全成、要么全回滚。</li>
 * </ul>
 */
public class PasswordResetPublicService {

    private final PasswordResetTokenService resetTokenService;
    private final TenantQueryService tenantQuery;
    private final SysUserMapper userMapper;
    private final UserCommandService userCommandService;
    private final PasswordHasher passwordHasher;
    private final SessionStore sessionStore;

    public PasswordResetPublicService(PasswordResetTokenService resetTokenService,
                                      TenantQueryService tenantQuery,
                                      SysUserMapper userMapper,
                                      UserCommandService userCommandService,
                                      PasswordHasher passwordHasher,
                                      SessionStore sessionStore) {
        this.resetTokenService = resetTokenService;
        this.tenantQuery = tenantQuery;
        this.userMapper = userMapper;
        this.userCommandService = userCommandService;
        this.passwordHasher = passwordHasher;
        this.sessionStore = sessionStore;
    }

    /** token 状态视图（控制器再映射为 web DTO 并格式化时间）。 */
    public record TokenStatus(String tenantName, String tenantCode, String usernameMask,
                              Instant expiresAt, String status, String purpose) {}

    /** 重置结果（控制器据此拼登录跳转路径）。 */
    public record ResetOutcome(String tenantCode) {}

    @CrossTenantQuery("PasswordResetPublicService.checkToken: 公开接口无租户上下文，按 token 跨租户查询")
    public TokenStatus checkToken(String token) {
        SysPasswordResetToken t = resetTokenService.findByToken(token);
        if (t == null) {
            throw new PasswordResetException(PasswordResetException.Code.TOKEN_NOT_FOUND,
                    "密码重置链接不存在或已失效");
        }
        TenantInfo tenant = tenantQuery.tenantById(t.getTenantId());
        String tenantName = tenant != null ? tenant.name() : t.getTenantCode();
        return new TokenStatus(tenantName, t.getTenantCode(), t.getUsernameMask(),
                t.getExpiresAt(), resolveStatus(t), t.getPurpose());
    }

    @CrossTenantQuery("PasswordResetPublicService.submitReset: 公开接口无租户上下文，按 token 操作")
    @Transactional
    public ResetOutcome submitReset(String token, String usernameInput, String newPassword) {
        SysPasswordResetToken t = resetTokenService.findByToken(token);
        if (t == null) {
            throw new PasswordResetException(PasswordResetException.Code.TOKEN_NOT_FOUND,
                    "密码重置链接不存在");
        }
        Instant now = Instant.now();
        if (t.getUsedAt() != null) {
            throw new PasswordResetException(PasswordResetException.Code.TOKEN_USED,
                    "该链接已使用，请直接登录");
        }
        if (t.getInvalidatedAt() != null) {
            throw new PasswordResetException(PasswordResetException.Code.TOKEN_INVALIDATED,
                    "该链接已作废，请联系运营重新生成");
        }
        if (t.getExpiresAt().isBefore(now)) {
            throw new PasswordResetException(PasswordResetException.Code.TOKEN_EXPIRED,
                    "链接已过期，请联系运营重新生成");
        }

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getId, t.getUserId()));
        if (user == null || !user.getUsername().equals(usernameInput)) {
            throw new PasswordResetException(PasswordResetException.Code.USERNAME_MISMATCH,
                    "账号与链接不匹配，请确认后重试");
        }
        if (!isStrongPassword(newPassword)) {
            throw new PasswordResetException(PasswordResetException.Code.PASSWORD_TOO_WEAK,
                    "密码必须同时包含字母和数字");
        }

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPasswordHash(passwordHasher.hash(newPassword));
        update.setStatus(1);
        userCommandService.updateById(update);

        resetTokenService.markUsed(t.getId());
        // 改密后强制下线该用户已有会话（与 OpsProfileServiceImpl.changePassword 一致），
        // 防「重置前泄露的会话仍可用」；afterCommit 之外的 Redis 清理失败不应回滚密码，故置于返回前、不抛断事务。
        sessionStore.invalidateAllByUser(t.getTenantId(), user.getId());
        return new ResetOutcome(t.getTenantCode());
    }

    /** token 当前状态：USED / INVALIDATED / EXPIRED / VALID。 */
    public static String resolveStatus(SysPasswordResetToken t) {
        if (t.getUsedAt() != null) return "USED";
        if (t.getInvalidatedAt() != null) return "INVALIDATED";
        if (t.getExpiresAt().isBefore(Instant.now())) return "EXPIRED";
        return "VALID";
    }

    /** 密码强度：长度 ≥ 8（由 @Size 保证）+ 同时含字母和数字。 */
    private static boolean isStrongPassword(String password) {
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            else if (Character.isDigit(c)) hasDigit = true;
            if (hasLetter && hasDigit) return true;
        }
        return false;
    }
}
