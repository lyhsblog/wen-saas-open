package com.wensaas.core.impl.auth.oauth;

import com.wensaas.core.api.auth.oauth.OAuthClient;
import com.wensaas.core.api.auth.oauth.OAuthExchangeException;
import com.wensaas.core.api.auth.oauth.OAuthUserInfo;

/**
 * 企业微信 OAuth mock 客户端（暂无真实开放平台凭据时的默认实现）。
 *
 * <p>确定性派生：同一 authCode 永远得到同一 unionId，支持"未绑→绑定→再用同 code 扫码"闭环；
 * 约定以 {@code "fail"} 开头的 code 模拟换码失败。
 *
 * <p>真实接入（TODO {@code RealWecomOAuthClient}）：用 code 换 userid（{@code /cgi-bin/auth/getuserinfo}）→
 * 拿 unionid / 用户详情，照钉钉补配置类后用 {@code wen-saas-boot.oauth.wecom.mode=real} 切换。
 */
public class MockWecomOAuthClient implements OAuthClient {

    @Override
    public String provider() {
        return "wecom";
    }

    @Override
    public OAuthUserInfo exchange(String authCode) {
        if (authCode == null || authCode.isBlank()) {
            throw new OAuthExchangeException("authCode 为空");
        }
        String code = authCode.trim();
        if (code.startsWith("fail")) {
            throw new OAuthExchangeException("mock 模拟换码失败：" + code);
        }
        return new OAuthUserInfo("mock-openid-" + code, "mock-wecom-" + code,
                "企微用户-" + code, null);
    }
}
