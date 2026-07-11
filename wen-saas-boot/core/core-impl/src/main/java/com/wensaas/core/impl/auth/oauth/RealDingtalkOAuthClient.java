package com.wensaas.core.impl.auth.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wensaas.core.api.auth.oauth.OAuthClient;
import com.wensaas.core.api.auth.oauth.OAuthExchangeException;
import com.wensaas.core.api.auth.oauth.OAuthUserInfo;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 钉钉 OAuth 真实客户端（{@code wen-saas-boot.oauth.dingtalk.mode=real} 装配）。
 *
 * <p>两步换取身份（钉钉开放平台 v1.0）：
 * <ol>
 *   <li>{@code POST /v1.0/oauth2/userAccessToken}（clientId/clientSecret/code）→ 取 {@code accessToken}；</li>
 *   <li>{@code GET /v1.0/contact/users/me} 带 {@code x-acs-dingtalk-access-token} → 取 openId/unionId/nick/avatar。</li>
 * </ol>
 *
 * <p>任何网络 / 4xx / 5xx / 解析异常一律包成 {@link OAuthExchangeException}（禁返 null），上层映射 401。
 */
public class RealDingtalkOAuthClient implements OAuthClient {

    private final DingtalkOAuthProperties props;
    private final RestClient client;

    public RealDingtalkOAuthClient(DingtalkOAuthProperties props) {
        this(props, defaultClient(props));
    }

    /** 测试注入点：传入已绑定 mock server 的 RestClient。 */
    RealDingtalkOAuthClient(DingtalkOAuthProperties props, RestClient client) {
        this.props = props;
        this.client = client;
    }

    private static RestClient defaultClient(DingtalkOAuthProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.getConnectTimeout().toMillis());
        factory.setReadTimeout((int) props.getReadTimeout().toMillis());
        return RestClient.builder().baseUrl(props.getBaseUrl()).requestFactory(factory).build();
    }

    @Override
    public String provider() {
        return "dingtalk";
    }

    @Override
    public OAuthUserInfo exchange(String authCode) {
        if (authCode == null || authCode.isBlank()) {
            throw new OAuthExchangeException("authCode 为空");
        }
        try {
            String accessToken = fetchUserAccessToken(authCode.trim());
            return fetchUserInfo(accessToken);
        } catch (OAuthExchangeException e) {
            throw e;
        } catch (RuntimeException e) {
            // 网络 / 4xx / 5xx / 反序列化等一律归一为换码失败。
            throw new OAuthExchangeException("钉钉换码失败", e);
        }
    }

    private String fetchUserAccessToken(String code) {
        UserAccessTokenResponse resp = client.post()
                .uri("/v1.0/oauth2/userAccessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "clientId", nullToEmpty(props.getClientId()),
                        "clientSecret", nullToEmpty(props.getClientSecret()),
                        "code", code,
                        "grantType", "authorization_code"))
                .retrieve()
                .body(UserAccessTokenResponse.class);
        if (resp == null || resp.accessToken() == null || resp.accessToken().isBlank()) {
            throw new OAuthExchangeException("钉钉 userAccessToken 响应缺少 accessToken");
        }
        return resp.accessToken();
    }

    private OAuthUserInfo fetchUserInfo(String accessToken) {
        ContactUserResponse resp = client.get()
                .uri("/v1.0/contact/users/me")
                .header("x-acs-dingtalk-access-token", accessToken)
                .retrieve()
                .body(ContactUserResponse.class);
        if (resp == null || (isBlank(resp.openId()) && isBlank(resp.unionId()))) {
            throw new OAuthExchangeException("钉钉用户信息缺少 openId/unionId");
        }
        return new OAuthUserInfo(resp.openId(), resp.unionId(), resp.nick(), resp.avatarUrl());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserAccessTokenResponse(String accessToken, Long expireIn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContactUserResponse(String openId, String unionId, String nick, String avatarUrl) {
    }
}
