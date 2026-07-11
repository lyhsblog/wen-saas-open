import type { HttpClient } from './http';

/**
 * 租户后台「个人中心」。对应 boot `/api/v1/base/profile`。
 *
 * 面向租户内任意成员管理自己的账号、安全与登录设备。与运营侧 {@link opsProfileApi} 平行，
 * 但 MFA 为 opt-in（可自助开启/关闭）、无通知偏好（由通知模板统一控制）。
 * 雪花 id 后端序列化为字符串，前端按 string 处理。
 */

// ===================== 基本信息 =====================

export type TenantProfileBasic = {
  userId?: string;
  username?: string;
  empNo?: string | null;
  email?: string | null;
  realName?: string | null;
  mobile?: string | null;
  /** 0=保密/未知 · 1=男 · 2=女。 */
  gender?: number | null;
  birthday?: string | null;
  avatarFileId?: string | null;
  avatarUrl?: string | null;
};

export type UpdateTenantProfileRequest = {
  realName: string;
  gender?: number | null;
  birthday?: string | null;
  avatarFileId?: string | null;
};

export type AvatarResult = {
  avatarFileId?: string;
  avatarUrl?: string | null;
};

// ===================== 组织与角色 =====================

export type TenantProfileRoleBrief = {
  code?: string;
  name?: string;
};

export type TenantProfileWork = {
  mainDeptName?: string | null;
  mainDeptPath?: string | null;
  secondaryDeptNames?: string[];
  roles?: TenantProfileRoleBrief[];
  /** active / disabled。 */
  statusLabel?: string;
  dataScopeLabel?: string;
  owner?: boolean;
};

// ===================== 安全设置 =====================

export type TenantProfileSecurity = {
  tokenTtlSeconds?: number;
  maxAttempts?: number;
  windowSeconds?: number;
  /** 租户恒 false（opt-in）。 */
  mfaRequired?: boolean;
  /** 登录二步验证开关。 */
  mfaEnabled?: boolean;
  /** 推导的登录验证通道 TOTP / EMAIL；未开启为 null。 */
  mfaMethod?: string | null;
};

// ===================== 会话 / 设备 =====================

export type TenantSessionItem = {
  sessionId?: string;
  loginAt?: string;
  lastAccessAt?: string;
  current?: boolean;
  /** 设备标签「浏览器 · 系统」（解析自 User-Agent），无法识别为「未知设备」。 */
  deviceLabel?: string;
  /** 登录时客户端 IP，可能为空（历史会话）。 */
  ip?: string;
};

// ===================== 密码 =====================

export type ChangePasswordRequest = {
  stepUpToken: string;
  newPassword: string;
};

// ===================== MFA =====================

export type MfaMethod = 'TOTP' | 'SMS' | 'EMAIL';

export type MfaStatusView = {
  required?: boolean;
  /** 登录二步验证开关。 */
  enabled?: boolean;
  /** 推导的登录验证通道 TOTP / EMAIL；未开启为 null。 */
  method?: string | null;
};

/** 开启登录二步验证挑战：推导通道（TOTP 不发码 / EMAIL 已发码）。 */
export type MfaEnableChallenge = {
  method?: MfaMethod;
  maskedTarget?: string | null;
};

/** 验证器绑定挑战：secret + otpauth URI。 */
export type MfaSetupChallenge = {
  method?: MfaMethod;
  totpSecret?: string | null;
  otpauthUri?: string | null;
  maskedTarget?: string | null;
};

export type MfaRecoveryCodesResponse = {
  recoveryCodes?: string[];
};

// ===================== 改手机号双通道 =====================

export type MobileChangeStep = {
  maskedTarget?: string;
  ttlSeconds?: number;
  resendAfterSeconds?: number;
};

// ===================== 第三方账号绑定（OAuth）=====================

export type OAuthBinding = {
  provider?: string;
  nickname?: string | null;
  avatarUrl?: string | null;
  boundAt?: string | null;
};

// ===================== client =====================

export function tenantProfileApi(http: HttpClient) {
  const base = '/api/v1/base/profile';
  return {
    async getBasic(): Promise<TenantProfileBasic> {
      const res = await http.get<TenantProfileBasic>(`${base}/basic`);
      return res.data;
    },
    async getWork(): Promise<TenantProfileWork> {
      const res = await http.get<TenantProfileWork>(`${base}/work`);
      return res.data;
    },
    async getSecurity(): Promise<TenantProfileSecurity> {
      const res = await http.get<TenantProfileSecurity>(`${base}/security`);
      return res.data;
    },
    async updateProfile(body: UpdateTenantProfileRequest): Promise<void> {
      await http.put(base, body);
    },
    /** 上传头像（≤1MB，JPG/PNG）；返回新 fileId + 签名 URL。 */
    async uploadAvatar(file: File): Promise<AvatarResult> {
      const form = new FormData();
      form.append('file', file);
      const res = await http.post<AvatarResult>(`${base}/avatar`, form);
      return res.data;
    },
    async changePassword(body: ChangePasswordRequest): Promise<void> {
      await http.post(`${base}/change-password`, body);
    },
    async listSessions(): Promise<TenantSessionItem[]> {
      const res = await http.get<TenantSessionItem[]>(`${base}/sessions`);
      return res.data;
    },
    async revokeSession(sessionId: string): Promise<void> {
      await http.delete(`${base}/sessions/${encodeURIComponent(sessionId)}`);
    },
    async revokeOtherSessions(): Promise<void> {
      await http.post(`${base}/sessions/revoke-others`);
    },

    // ===== MFA =====
    async getMfaStatus(): Promise<MfaStatusView> {
      const res = await http.get<MfaStatusView>(`${base}/mfa`);
      return res.data;
    },
    /** 绑定身份验证器 · 起手：返回 secret + otpauth URI。 */
    async totpBindStart(): Promise<MfaSetupChallenge> {
      const res = await http.post<MfaSetupChallenge>(`${base}/mfa/totp/bind`);
      return res.data;
    },
    /** 绑定身份验证器 · 确认：校验新验证器动态码。 */
    async totpBindConfirm(code: string): Promise<void> {
      await http.post(`${base}/mfa/totp/bind/confirm`, { code });
    },
    /** 开启登录二步验证 · 起手：通道服务端推导（TOTP 不发码 / EMAIL 发码）。 */
    async mfaEnableStart(): Promise<MfaEnableChallenge> {
      const res = await http.post<MfaEnableChallenge>(`${base}/mfa/enable`);
      return res.data;
    },
    /** 开启登录二步验证 · 确认：验码 → 开启 + 返回 8 组一次性恢复码。 */
    async mfaEnableConfirm(code: string): Promise<MfaRecoveryCodesResponse> {
      const res = await http.post<MfaRecoveryCodesResponse>(`${base}/mfa/enable/confirm`, { code });
      return res.data;
    },
    /** 关闭登录二步验证：需先经身份验证（stepUpToken）。 */
    async disableMfa(stepUpToken: string): Promise<void> {
      await http.post(`${base}/mfa/disable`, { stepUpToken });
    },

    // ===== 第三方账号绑定（OAuth）=====
    async listOAuthBindings(): Promise<OAuthBinding[]> {
      const res = await http.get<OAuthBinding[]>(`${base}/oauth-bindings`);
      return res.data;
    },
    /** 已登录态主动绑定：用渠道 authCode 绑定到当前账号。本期 provider=dingtalk。 */
    async bindOAuth(provider: string, authCode: string): Promise<void> {
      await http.post(`${base}/oauth-bindings/${encodeURIComponent(provider)}`, { authCode });
    },
    async unbindOAuth(provider: string): Promise<void> {
      await http.delete(`${base}/oauth-bindings/${encodeURIComponent(provider)}`);
    },

    // ===== 改手机号 · step-up 前置身份验证后 =====
    async sendNewMobileCode(stepUpToken: string, newMobile: string): Promise<MobileChangeStep> {
      const res = await http.post<MobileChangeStep>(`${base}/mobile/send-new-code`, {
        stepUpToken,
        newMobile,
      });
      return res.data;
    },
    async confirmNewMobile(stepUpToken: string, code: string): Promise<void> {
      await http.post(`${base}/mobile/change`, { stepUpToken, code });
    },

    // ===== 改邮箱 · step-up 前置身份验证后（租户端自助）=====
    async sendNewEmailCode(stepUpToken: string, newEmail: string): Promise<MobileChangeStep> {
      const res = await http.post<MobileChangeStep>(`${base}/email/send-new-code`, {
        stepUpToken,
        newEmail,
      });
      return res.data;
    },
    async confirmNewEmail(stepUpToken: string, code: string): Promise<void> {
      await http.post(`${base}/email/change`, { stepUpToken, code });
    },
  };
}
