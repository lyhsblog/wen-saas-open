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
 * 重新签发主管理员密码重置 token 的返回视图。
 *
 * <p>{@code resetExpiresAt} ISO-8601 带 +08:00 偏移。
 * 重置链接由前端按租户子域名拼接（调本端点时已在租户详情页，tenantCode 已知）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class ReissueResetLinkView {
    private String resetToken;
    private String resetExpiresAt;
    private String adminUsername;
}
