package com.wensaas.core.web.ops.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 运营开通新租户响应体。租户端链接（重置/登录）由前端按租户子域名拼接，
 * 后端只返回 token + tenantCode。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class CreatedTenantView {
    private String tenantId;
    private String tenantCode;
    private String tenantNo;
    private String adminUsername;
    private String resetToken;
    private String resetExpiresAt;
}
