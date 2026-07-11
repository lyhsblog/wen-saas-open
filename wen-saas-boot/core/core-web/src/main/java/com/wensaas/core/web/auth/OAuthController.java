package com.wensaas.core.web.auth;

import com.wensaas.core.api.auth.login.LoginCredentials;
import com.wensaas.core.api.auth.permission.PublicEndpoint;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.impl.auth.login.AuthService;
import com.wensaas.core.impl.auth.oauth.OAuthStateStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 第三方账号登录与绑定。公开端点，凭渠道 authCode / 绑定 bindToken 完成。
 * 路径 {@code /api/auth/oauth/{provider}}，本期 provider=dingtalk。
 */
@Tag(name = "auth-oauth", description = "第三方账号登录与绑定")
@RestController
@RequestMapping("/api/auth/oauth")
public class OAuthController {

    private final AuthService authService;
    private final RefreshCookie refreshCookie;
    private final OAuthStateStore oauthStateStore;

    public OAuthController(AuthService authService, RefreshCookie refreshCookie,
                          OAuthStateStore oauthStateStore) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
        this.oauthStateStore = oauthStateStore;
    }

    @Operation(summary = "签发 OAuth 登录 state",
            description = "前端发起第三方登录前调用，取一次性 state（防 CSRF），跳转渠道授权页时带上；"
                    + "渠道回调原样带回，登录时由后端比对消费。state 有效期见 expiresIn。")
    @SecurityRequirements
    @PublicEndpoint
    @PostMapping("/{provider}/state")
    public OAuthStateResponse issueState(@PathVariable String provider) {
        String state = oauthStateStore.issue(provider);
        return new OAuthStateResponse(state, oauthStateStore.ttlSeconds());
    }

    @Operation(summary = "第三方登录",
            description = "用渠道 authCode 登录：已绑定直接签发 token；未绑定返回 bindingRequired=true + bindToken，"
                    + "前端引导补账密走 /bind 完成绑定")
    @SecurityRequirements
    @PublicEndpoint
    @PostMapping("/{provider}/login")
    public OAuthLoginResponse login(@PathVariable String provider,
                                    @Valid @RequestBody OAuthLoginRequest req,
                                    HttpServletResponse resp) {
        AuthService.LoginResult r = authService.login(
                new LoginCredentials.OAuthCredentials(provider, req.authCode(), req.state()));
        if (r.bindingRequired()) {
            AuthService.OAuthProfile p = r.bindProfile();
            return OAuthLoginResponse.binding(r.bindToken(), new OAuthLoginResponse.Profile(
                    p == null ? provider : p.provider(),
                    p == null ? null : p.nickname(),
                    p == null ? null : p.avatarUrl()));
        }
        UserPrincipal p = r.principal();
        // 第三方登录恒非 remember。
        refreshCookie.issue(resp, p.sessionId(), false);
        return OAuthLoginResponse.completed(r.token(), r.expiresIn().toSeconds(),
                new LoginResponse.UserSummary(p.userId(), p.username(), p.tenantId()));
    }

    @Operation(summary = "完成第三方绑定并登录",
            description = "凭 bindToken + 账号密码绑定到平台账号，绑定成功即签发 token；以后扫码直登")
    @SecurityRequirements
    @PublicEndpoint
    @PostMapping("/{provider}/bind")
    public LoginResponse bind(@PathVariable String provider,
                              @Valid @RequestBody OAuthBindRequest req,
                              HttpServletResponse resp) {
        AuthService.LoginResult r = authService.completeOAuthBinding(req.bindToken(),
                new LoginCredentials.PasswordCredentials(req.username(), req.password(), req.tenantCode()));
        UserPrincipal p = r.principal();
        refreshCookie.issue(resp, p.sessionId(), false);
        return LoginResponse.completed(r.token(), r.expiresIn().toSeconds(),
                new LoginResponse.UserSummary(p.userId(), p.username(), p.tenantId()));
    }

    @Schema(description = "第三方登录请求")
    public record OAuthLoginRequest(@NotBlank String authCode,
                                    @Schema(description = "防 CSRF state，须先经 /state 端点签发") @NotBlank String state) {
    }

    @Schema(description = "OAuth 登录 state 签发响应")
    public record OAuthStateResponse(String state,
                                     @Schema(description = "state 有效秒数") long expiresIn) {
    }

    @Schema(description = "完成绑定请求")
    public record OAuthBindRequest(@NotBlank String bindToken, @NotBlank String username,
                                   @NotBlank String password, String tenantCode) {
    }

    @Schema(description = "第三方登录响应（两形态：已绑直登 / 未绑引导绑定）")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class OAuthLoginResponse {
        @Schema(description = "是否需要绑定（true=未绑定，需走 /bind）")
        private boolean bindingRequired;
        @Schema(description = "JWT（已绑直登时返回）")
        private String token;
        @Schema(description = "token 有效秒数")
        private long expiresIn;
        private LoginResponse.UserSummary user;
        @Schema(description = "绑定流一次性票据（未绑时返回）")
        private String bindToken;
        private Profile profile;

        static OAuthLoginResponse completed(String token, long expiresIn, LoginResponse.UserSummary user) {
            return new OAuthLoginResponse(false, token, expiresIn, user, null, null);
        }

        static OAuthLoginResponse binding(String bindToken, Profile profile) {
            return new OAuthLoginResponse(true, null, 0, null, bindToken, profile);
        }

        @Schema(description = "渠道资料快照")
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @EqualsAndHashCode
        @ToString
        @Accessors(fluent = true)
        @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
        public static class Profile {
            private String provider;
            private String nickname;
            private String avatarUrl;

        }
    }
}
