package com.wensaas.core.impl.auth.filter;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 公开端点 IP 级频控配置（架构审查低优项）。
 *
 * <p>登录已有「失败锁定」护栏，但公开密码重置 / OAuth 端点（未认证、无账号维度）此前无独立频控，
 * 易被脚本枚举 token / 刷渠道授权。此处按客户端 IP 在滑动窗口内限流。
 *
 * <pre>
 * wen-saas-boot:
 *   public-rate-limit:
 *     enabled: true
 *     window: 60s
 *     limit: 20                  # 单 IP 每窗口允许的请求数
 *     key-prefix: wen-saas-boot:ratelimit:public
 *     paths:                     # 命中即按 IP 限流（Ant 风格）
 *       - /api/v1/public/reset-password/**
 *       - /api/auth/oauth/**
 *       - /api/auth/mfa/**
 * </pre>
 */
@ConfigurationProperties(prefix = "wen-saas-boot.public-rate-limit")
@Getter
@Setter
public class PublicRateLimitProperties {

    private boolean enabled = true;
    private Duration window = Duration.ofSeconds(60);
    private int limit = 20;
    private String keyPrefix = "wen-saas-boot:ratelimit:public";
    private List<String> paths = new ArrayList<>(List.of(
            "/api/v1/public/reset-password/**",
            "/api/auth/oauth/**",
            // MFA 端点（发码/校验）：未认证可打（凭 preAuthToken 收敛），加 IP 级兜底防脚本刷码
            "/api/auth/mfa/**"));
}
