package com.wensaas.core.impl.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * 钉钉 OAuth 配置。{@code mode=mock}（缺省）装配 {@link MockDingtalkOAuthClient}，
 * {@code mode=real} 装配 {@link RealDingtalkOAuthClient}（互斥，见 {@code AuthConfiguration}）。
 *
 * <p>{@code clientId / clientSecret} 为钉钉开放平台应用凭据，<b>仅经环境变量注入</b>，禁止提交真实值。
 */
@ConfigurationProperties(prefix = "wen-saas-boot.oauth.dingtalk")
@Getter
@Setter
public class DingtalkOAuthProperties {

    /** mock（默认）/ real。 */
    private String mode = "mock";

    /** 钉钉应用 clientId（AppKey）。real 模式必配。 */
    private String clientId;

    /** 钉钉应用 clientSecret（AppSecret）。real 模式必配。 */
    private String clientSecret;

    /** 钉钉开放平台网关，默认正式环境。 */
    private String baseUrl = "https://api.dingtalk.com";

    /** HTTP 连接超时。 */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** HTTP 读超时。 */
    private Duration readTimeout = Duration.ofSeconds(5);

}
