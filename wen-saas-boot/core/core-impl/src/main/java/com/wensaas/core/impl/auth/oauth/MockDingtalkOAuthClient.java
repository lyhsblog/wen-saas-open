package com.wensaas.core.impl.auth.oauth;

import com.wensaas.core.api.auth.oauth.OAuthClient;
import com.wensaas.core.api.auth.oauth.OAuthExchangeException;
import com.wensaas.core.api.auth.oauth.OAuthUserInfo;

/**
 * 钉钉 OAuth mock 客户端（暂无真实开放平台凭据时的默认实现）。
 *
 * <p>确定性派生：同一 authCode 永远得到同一 unionId，因此"未绑→绑定→再用同 code 扫码"能命中绑定直登，
 * 便于本地联调与单测验证完整闭环。约定以 {@code "fail"} 开头的 code 模拟换码失败，覆盖失败路径。
 *
 * <p>真实实现见 {@code RealDingtalkOAuthClient}，通过 {@code wen-saas-boot.oauth.dingtalk.mode=real} 互斥切换。
 */
public class MockDingtalkOAuthClient implements OAuthClient {

    @Override
    public String provider() {
        return "dingtalk";
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
        return new OAuthUserInfo("mock-openid-" + code, "mock-dingtalk-" + code,
                "钉钉用户-" + code, null);
    }
}
