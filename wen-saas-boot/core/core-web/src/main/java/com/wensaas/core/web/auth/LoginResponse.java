package com.wensaas.core.web.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 登录响应。两种形态：
 * <ul>
 *   <li>已完成：{@code mfaRequired=false}，携带 {@code token} / {@code expiresIn} / {@code user}。</li>
 *   <li>待二步验证：{@code mfaRequired=true}，{@code token} 为空，携带 {@code preAuthToken} 与
 *       {@code mfaEnabled} / {@code mfaMethod}，前端据此走 {@code /api/auth/mfa/*}。</li>
 * </ul>
 */
@Schema(description = "登录响应")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class LoginResponse {
    @Schema(description = "JWT；后续请求带 Authorization: Bearer <token>（待二步验证时为空）",
                example = "eyJhbGciOiJIUzI1NiJ9....")
    private String token;
    @Schema(description = "token 有效秒数", example = "7200")
    private long expiresIn;
    private UserSummary user;
    @Schema(description = "是否需要二步验证")
    private boolean mfaRequired;
    @Schema(description = "登录二步验证是否已开启（false=强制模式下需先 setup）")
    private boolean mfaEnabled;
    @Schema(description = "推导的登录验证通道：TOTP / EMAIL（未开启为空）", example = "TOTP")
    private String mfaMethod;
    @Schema(description = "二步验证预认证票据；调用 /api/auth/mfa/* 时回传")
    private String preAuthToken;

    public static LoginResponse completed(String token, long expiresIn, UserSummary user) {
        return new LoginResponse(token, expiresIn, user, false, false, null, null);
    }

    public static LoginResponse mfaPending(boolean enabled, String mfaMethod, String preAuthToken) {
        return new LoginResponse(null, 0, null, true, enabled, mfaMethod, preAuthToken);
    }

    @Schema(description = "登录用户摘要")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class UserSummary {
        @Schema(description = "用户 ID（雪花，JSON 序列化为字符串）", type = "string", example = "100001")
        private long id;
        @Schema(description = "登录账号", example = "alice")
        private String username;
        @Schema(description = "租户 ID（0=运营，JSON 序列化为字符串）",
                    type = "string", example = "9701")
        private long tenantId;

    }
}
