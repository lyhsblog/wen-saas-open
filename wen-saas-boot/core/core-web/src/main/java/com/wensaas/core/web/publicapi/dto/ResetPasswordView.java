package com.wensaas.core.web.publicapi.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * GET /api/v1/public/reset-password/by-token/{token} 响应体。
 * status: VALID | EXPIRED | USED | INVALIDATED
 * purpose: init_admin | reset_admin | reset_member（成员忘记密码由管理员重置签发 reset_member，无自助签发口径）
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class ResetPasswordView {
    private String tenantName;
    private String tenantCode;
    private String usernameMask;
    private String expiresAt;
    private String status;
    private String purpose;
}
