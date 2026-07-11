package com.wensaas.core.api.auth.oauth;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 第三方渠道返回的用户信息（{@link OAuthClient#exchange} 输出）。
 *
 * @param openId    渠道应用内唯一标识
 * @param unionId   渠道开放平台跨应用唯一标识（钉钉/微信 unionId），可能为空
 * @param nickname  昵称（绑定时快照，仅展示）
 * @param avatarUrl 头像 URL，可能为空
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class OAuthUserInfo {
    private String openId;
    private String unionId;
    private String nickname;
    private String avatarUrl;

    /** 渠道侧稳定标识：unionId 优先，回退 openId。绑定表 external_id 取此值。 */
    public String externalId() {
        return (unionId != null && !unionId.isBlank()) ? unionId : openId;
    }
}
