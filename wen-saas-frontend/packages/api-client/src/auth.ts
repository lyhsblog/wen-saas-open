import type { LoginRequest, LoginResponse, UserSummary } from '@wen-saas/domain';
import type { HttpClient } from './http';

/** 登录期二步验证通道。 */
export type LoginMfaMethod = 'TOTP' | 'SMS' | 'EMAIL';

/** MFA 配置挑战（强制模式首次 setup）。TOTP 返 secret + otpauth URI；EMAIL 返发码目标。 */
export type LoginMfaSetupChallenge = {
  method?: LoginMfaMethod;
  totpSecret?: string | null;
  otpauthUri?: string | null;
  maskedTarget?: string | null;
};

/** 已配置 SMS/EMAIL 时发码后的挑战信息。 */
export type LoginMfaChallengeInfo = {
  method?: LoginMfaMethod;
  maskedTarget?: string | null;
};

/** MFA 校验结果：通过即签发会话 token（首次配置额外带一次性恢复码）。 */
export type MfaVerifyResult = {
  token: string;
  expiresIn: number;
  user: UserSummary;
  recoveryCodes?: string[] | null;
};

/** 第三方登录渠道资料快照（绑定流展示）。 */
export type OAuthProfile = {
  provider?: string;
  nickname?: string | null;
  avatarUrl?: string | null;
};

/**
 * 第三方登录响应（两形态）：
 * - 已绑直登：`bindingRequired=false`，携带 token / expiresIn / user。
 * - 未绑引导：`bindingRequired=true`，携带 bindToken + profile，前端补账密走 oauthBind 完成。
 */
export type OAuthLoginResponse = {
  bindingRequired: boolean;
  token?: string | null;
  expiresIn?: number;
  user?: UserSummary | null;
  bindToken?: string | null;
  profile?: OAuthProfile | null;
};

export function authApi(http: HttpClient) {
  return {
    async login(body: LoginRequest): Promise<LoginResponse> {
      const res = await http.post<LoginResponse>('/api/auth/login', body);
      return res.data;
    },
    /** 登录期 MFA · 发起配置（首次强制）。凭 login 返回的 preAuthToken。 */
    async mfaSetup(body: {
      preAuthToken: string;
      method: LoginMfaMethod;
    }): Promise<LoginMfaSetupChallenge> {
      const res = await http.post<LoginMfaSetupChallenge>('/api/auth/mfa/setup', body);
      return res.data;
    },
    /** 登录期 MFA · 发送动态码（已配置 SMS/EMAIL；TOTP 返 method 不发码）。 */
    async mfaChallenge(body: { preAuthToken: string }): Promise<LoginMfaChallengeInfo> {
      const res = await http.post<LoginMfaChallengeInfo>('/api/auth/mfa/challenge', body);
      return res.data;
    },
    /** 登录期 MFA · 校验并完成登录（签发 token）。rememberMe 透传至 refresh token 有效期。 */
    async mfaVerify(body: {
      preAuthToken: string;
      code: string;
      rememberMe?: boolean;
    }): Promise<MfaVerifyResult> {
      const res = await http.post<MfaVerifyResult>('/api/auth/mfa/verify', body);
      return res.data;
    },
    /** 第三方登录：已绑直登返回 token；未绑返回 bindToken + 渠道资料。本期 provider=dingtalk。 */
    async oauthLogin(
      provider: string,
      body: { authCode: string; state?: string },
    ): Promise<OAuthLoginResponse> {
      const res = await http.post<OAuthLoginResponse>(
        `/api/auth/oauth/${encodeURIComponent(provider)}/login`,
        body,
      );
      return res.data;
    },
    /** 完成第三方绑定并登录：凭 bindToken + 账密绑定到平台账号，返回会话 token（同 completed 形态）。 */
    async oauthBind(
      provider: string,
      body: { bindToken: string; username: string; password: string; tenantCode?: string },
    ): Promise<LoginResponse> {
      const res = await http.post<LoginResponse>(
        `/api/auth/oauth/${encodeURIComponent(provider)}/bind`,
        body,
      );
      return res.data;
    },
    async logout(): Promise<void> {
      await http.post('/api/auth/logout');
    },
    async me(): Promise<UserSummary> {
      const res = await http.get<UserSummary>('/api/auth/me');
      return res.data;
    },
    /** 运营代登录：消费一次性 nonce，换取目标租户的登录态（同 LoginResponse 结构）。 */
    async redeemImpersonate(nonce: string): Promise<LoginResponse> {
      const res = await http.post<LoginResponse>('/api/auth/impersonate/redeem', { nonce });
      return res.data;
    },
    /** 退出代登录：销毁当前代登录 session（须为代登录态）。返回后前端应清空本端登录态。 */
    async exitImpersonation(): Promise<void> {
      await http.post('/api/auth/exit-impersonation');
    },
  };
}
