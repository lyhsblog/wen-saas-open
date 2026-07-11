package com.wensaas.core.impl.auth.oauth;

import com.wensaas.core.api.auth.oauth.OAuthExchangeException;
import com.wensaas.core.api.auth.oauth.OAuthUserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 钉钉真实客户端单测：mock HTTP 覆盖成功映射、token 接口失败、用户接口失败、缺字段 → 均 {@link OAuthExchangeException}。
 */
class RealDingtalkOAuthClientTest {

    private static final String BASE = "https://api.dingtalk.com";
    private static final String TOKEN_URL = BASE + "/v1.0/oauth2/userAccessToken";
    private static final String USER_URL = BASE + "/v1.0/contact/users/me";

    private record Fixture(RealDingtalkOAuthClient client, MockRestServiceServer server) {
    }

    private Fixture newFixture() {
        DingtalkOAuthProperties props = new DingtalkOAuthProperties();
        props.setMode("real");
        props.setClientId("test-key");
        props.setClientSecret("test-secret");
        props.setBaseUrl(BASE);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new RealDingtalkOAuthClient(props, builder.build()), server);
    }

    @Test
    void success_mapsUserInfo() {
        Fixture f = newFixture();
        f.server().expect(requestTo(TOKEN_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"accessToken\":\"at-1\",\"expireIn\":7200}",
                        MediaType.APPLICATION_JSON));
        f.server().expect(requestTo(USER_URL)).andExpect(method(HttpMethod.GET))
                .andExpect(header("x-acs-dingtalk-access-token", "at-1"))
                .andRespond(withSuccess(
                        "{\"openId\":\"oid-1\",\"unionId\":\"uid-1\",\"nick\":\"张三\",\"avatarUrl\":\"http://a/x.png\"}",
                        MediaType.APPLICATION_JSON));

        OAuthUserInfo info = f.client().exchange("code-1");

        assertThat(info.openId()).isEqualTo("oid-1");
        assertThat(info.unionId()).isEqualTo("uid-1");
        assertThat(info.nickname()).isEqualTo("张三");
        assertThat(info.avatarUrl()).isEqualTo("http://a/x.png");
        assertThat(info.externalId()).isEqualTo("uid-1");
        f.server().verify();
    }

    @Test
    void tokenEndpointFails_throwsExchange() {
        Fixture f = newFixture();
        f.server().expect(requestTo(TOKEN_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> f.client().exchange("code-1"))
                .isInstanceOf(OAuthExchangeException.class);
    }

    @Test
    void userEndpointFails_throwsExchange() {
        Fixture f = newFixture();
        f.server().expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"accessToken\":\"at-1\"}", MediaType.APPLICATION_JSON));
        f.server().expect(requestTo(USER_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> f.client().exchange("code-1"))
                .isInstanceOf(OAuthExchangeException.class);
    }

    @Test
    void tokenMissing_throwsExchange() {
        Fixture f = newFixture();
        f.server().expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"expireIn\":7200}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.client().exchange("code-1"))
                .isInstanceOf(OAuthExchangeException.class);
    }

    @Test
    void userInfoMissingIds_throwsExchange() {
        Fixture f = newFixture();
        f.server().expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"accessToken\":\"at-1\"}", MediaType.APPLICATION_JSON));
        f.server().expect(requestTo(USER_URL))
                .andRespond(withSuccess("{\"nick\":\"无id\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.client().exchange("code-1"))
                .isInstanceOf(OAuthExchangeException.class);
    }

    @Test
    void blankAuthCode_throwsExchange() {
        Fixture f = newFixture();
        assertThatThrownBy(() -> f.client().exchange("  "))
                .isInstanceOf(OAuthExchangeException.class);
    }
}
