package com.wensaas.core.impl.auth.oauth;

import java.time.Instant;

/**
 * OAuth 绑定流临时凭证携带的数据（存 Redis，TTL=5min）。OAuth 授权成功但未绑定平台账号时暂存渠道身份，
 * 待用户补账密完成绑定。
 *
 * @param provider   渠道：dingtalk
 * @param externalId 渠道侧稳定标识（绑定写入 sys_user_oauth_binding.external_id）
 * @param unionId    渠道 unionId（冗余留存）
 * @param nickname   昵称快照
 * @param avatarUrl  头像 URL
 * @param createdAt  签发时刻
 */
public record OAuthBindPreAuth(String provider, String externalId, String unionId,
                               String nickname, String avatarUrl, Instant createdAt) {
}
