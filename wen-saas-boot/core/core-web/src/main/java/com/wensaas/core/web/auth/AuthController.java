package com.wensaas.core.web.auth;

import com.wensaas.core.api.auth.login.LoginCredentials;
import com.wensaas.core.api.auth.permission.PublicEndpoint;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.impl.auth.login.AuthService;
import com.wensaas.core.impl.auth.impersonate.ImpersonateService;
import com.wensaas.core.impl.auth.session.JwtTokenProvider;
import com.wensaas.core.impl.auth.session.RefreshTokenService;
import com.wensaas.core.impl.auth.session.Session;
import com.wensaas.core.impl.auth.session.SessionStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * 认证接口入口。
 */
@Tag(name = "auth", description = "认证 / 会话")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ImpersonateService impersonateService;
    private final RefreshCookie refreshCookie;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final SessionStore sessionStore;

    public AuthController(AuthService authService, ImpersonateService impersonateService,
                          RefreshCookie refreshCookie, RefreshTokenService refreshTokenService,
                          JwtTokenProvider jwtTokenProvider, SessionStore sessionStore) {
        this.authService = authService;
        this.impersonateService = impersonateService;
        this.refreshCookie = refreshCookie;
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionStore = sessionStore;
    }

    @Operation(summary = "登录",
            description = "用户名 + 密码换取 JWT；token 仅承载 sessionId，会话状态在 Redis。"
                    + "运营账号（tenant_id=0）强制二步验证，返回 mfaRequired=true + preAuthToken，"
                    + "需走 /api/auth/mfa/* 完成后才拿到 token")
    @SecurityRequirements // 显式无鉴权（覆盖全局 bearer-jwt 默认）
    @PublicEndpoint
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletResponse resp) {
        AuthService.LoginResult r = authService.login(
                new LoginCredentials.PasswordCredentials(req.username(), req.password(), req.tenantCode()));
        if (r.mfaRequired()) {
            // 运营账号待 MFA：refresh cookie 在 /api/auth/mfa/verify 完成时再下发（rememberMe 由前端在 verify 时回传）。
            return LoginResponse.mfaPending(r.mfaEnabled(),
                    r.mfaMethod() == null ? null : r.mfaMethod().name(), r.preAuthToken());
        }
        return completed(r, req.remember(), resp);
    }

    /** 登录 / 兑换完成态共用：种 refresh cookie 并组装 LoginResponse。 */
    private LoginResponse completed(AuthService.LoginResult r, boolean remember, HttpServletResponse resp) {
        UserPrincipal p = r.principal();
        refreshCookie.issue(resp, p.sessionId(), remember);
        return LoginResponse.completed(
                r.token(),
                r.expiresIn().toSeconds(),
                new LoginResponse.UserSummary(p.userId(), p.username(), p.tenantId()));
    }

    @Operation(summary = "刷新 access token",
            description = "凭 httpOnly refresh cookie 续期：校验并轮换 refresh（回种新 cookie），签发新 access。"
                    + "refresh 过期/无效/被重放 → 401，前端转重新登录")
    @SecurityRequirements
    @PublicEndpoint
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest req, HttpServletResponse resp) {
        String raw = refreshCookie.read(req);
        RefreshTokenService.RotateResult result = refreshTokenService.rotate(raw);
        if (result.status() != RefreshTokenService.Status.OK) {
            refreshCookie.clear(resp);
            return ResponseEntity.status(401).build();
        }
        Optional<Session> session = sessionStore.findBySessionId(result.sessionId());
        if (session.isEmpty()) {
            refreshCookie.clear(resp);
            return ResponseEntity.status(401).build();
        }
        Session s = session.get();
        String access = jwtTokenProvider.issue(s.sessionId());
        refreshCookie.rotate(resp, result.newRefreshToken(), result.remember());
        return ResponseEntity.ok(LoginResponse.completed(
                access,
                jwtTokenProvider.tokenTtl().toSeconds(),
                new LoginResponse.UserSummary(s.userId(), s.username(), s.tenantId())));
    }

    /**
     * 退出登录。要求已认证（不在白名单），从 UserContext 取 sessionId。
     */
    @Operation(summary = "登出", description = "立即失效当前 session（Redis 删除 + 同 token 二次访问 401）")
    @SecurityRequirement(name = "bearer-jwt")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse resp) {
        UserPrincipal p = UserContext.require();
        refreshTokenService.revoke(p.sessionId());
        authService.logout(p.sessionId());
        refreshCookie.clear(resp);
        return ResponseEntity.noContent().build();
    }

    /**
     * 代登录 nonce 兑换。无需鉴权（@PublicEndpoint），凭 nonce 唯一身份证明。
     * 供租户端在跳转落地页调用，兑换代登录 token 后存入 localStorage。
     */
    @Operation(summary = "代登录 nonce 兑换",
            description = "公开接口；租户端凭运营端签发的一次性 nonce 换取代登录 token，nonce 5min 内单次有效")
    @SecurityRequirements
    @PublicEndpoint
    @PostMapping("/impersonate/redeem")
    public LoginResponse redeemImpersonate(@Valid @RequestBody RedeemImpersonateRequest req,
                                           HttpServletResponse resp) {
        AuthService.LoginResult r = impersonateService.redeemNonce(req.nonce());
        // 代登录会话也种 refresh cookie（否则短 access 下会很快被锁死、无法续期），但恒非 remember（不可 30 天）。
        // XHR 同源打目标租户子域，Set-Cookie host-only 落在目标子域，不影响运营端原 session。
        return completed(r, false, resp);
    }

    /**
     * 退出代登录。由 tenant-web（持代登录 token）调用，仅销毁代登录 session + 写双向审计。
     * 运营原 session 在 ops 域名独立存活，无需重签；ops-web 端的运营 token 全程未受影响。
     */
    @Operation(summary = "退出代登录",
            description = "销毁代登录 session 并写 impersonate-exit 审计。运营原 session 在另一域名独立存活，"
                    + "本端不重签 token。须为代登录态（session.originalSessionId 非空）；运营原 session 已过期则 410。")
    @SecurityRequirement(name = "bearer-jwt")
    @PostMapping("/exit-impersonation")
    public ResponseEntity<Void> exitImpersonation(HttpServletResponse resp) {
        UserPrincipal p = UserContext.require();
        refreshTokenService.revoke(p.sessionId());
        impersonateService.exitImpersonation(p.sessionId());
        refreshCookie.clear(resp);
        return ResponseEntity.noContent().build();
    }

    /**
     * 调试 / 自检：返回当前认证主体。所有受保护接口的最小验证端点。
     */
    @Operation(summary = "当前主体", description = "返回当前 JWT 解析出的 UserPrincipal 摘要（调试 / 自检用）")
    @SecurityRequirement(name = "bearer-jwt")
    @GetMapping("/me")
    public Map<String, Object> me() {
        UserPrincipal p = UserContext.require();
        return Map.of(
                "userId", p.userId(),
                "tenantId", p.tenantId(),
                "username", p.username(),
                "sessionId", p.sessionId()
        );
    }
}
