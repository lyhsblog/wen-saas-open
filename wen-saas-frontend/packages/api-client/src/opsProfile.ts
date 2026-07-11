import type { HttpClient } from './http';
import { toWirePageParams } from './pagination';

// ===================== enums =====================

export type NotificationCategory =
  | 'TODO_TASK'
  | 'TICKET_ALERT'
  | 'CUSTOMER_HEALTH'
  | 'PACKAGE_EXPIRY'
  | 'SYSTEM_ANNOUNCEMENT'
  | 'SECURITY_ALERT';

export type NotificationChannel = 'STATION' | 'EMAIL' | 'SMS' | 'WECHAT' | 'WECOM' | 'DINGTALK';

// ===================== profile aggregate =====================

export type OpsProfileBasic = {
  userId?: string;
  username?: string;
  empNo?: string;
  email?: string;
  realName?: string;
  mobile?: string;
  /** 0=未知 / 1=男 / 2=女。 */
  gender?: number;
  birthday?: string;
  avatarFileId?: string;
  avatarUrl?: string;
};

export type OpsProfileRoleBrief = {
  id?: string;
  code?: string;
  name?: string;
};

export type OpsProfileTeamsRoles = {
  roles?: OpsProfileRoleBrief[];
  dataScope?: string;
  accountStatus?: string;
};

export type OpsProfileSecurity = {
  sessionTimeoutSeconds?: number;
  loginLockMaxAttempts?: number;
  loginLockWindowSeconds?: number;
  /** 是否被平台强制登录二步验证（配置项 ops-mfa-enforced） */
  mfaRequired?: boolean;
  /** 登录二步验证开关 */
  mfaEnabled?: boolean;
  /** 推导的登录验证通道（TOTP/EMAIL）；未开启为空 */
  mfaMethod?: OpsMfaMethod | null;
};

export type OpsProfileSso = {
  provider?: string;
  enabled?: boolean;
  lastSyncAt?: string;
};

export type OpsProfileView = {
  basic?: OpsProfileBasic;
  teamsRoles?: OpsProfileTeamsRoles;
  security?: OpsProfileSecurity;
  sso?: OpsProfileSso;
};

export type UpdateOpsProfileRequest = {
  realName: string;
  gender?: number | null;
  birthday?: string | null;
  avatarFileId?: string | null;
};

/** 头像上传结果（新文件 ID + 短期签名 URL）。 */
export type OpsAvatarResult = {
  avatarFileId?: string;
  avatarUrl?: string | null;
};

// ===================== password =====================

export type ChangePasswordRequest = {
  stepUpToken: string;
  newPassword: string;
};

// ===================== sessions =====================

export type OpsSessionItem = {
  sessionId?: string;
  loginAt?: string;
  lastAccessAt?: string;
  current?: boolean;
  /** 设备标签「浏览器 · 系统」（解析自 User-Agent），无法识别为「未知设备」。 */
  deviceLabel?: string;
  /** 登录时客户端 IP，可能为空（历史会话）。 */
  ip?: string;
};

// ===================== login logs =====================

export type OpsLoginLogItem = {
  id?: string;
  account?: string;
  loginMethod?: string;
  action?: string;
  result?: string;
  failReason?: string;
  ip?: string;
  userAgent?: string;
  createdAt?: string;
};

export type OpsLoginLogPageView = {
  records?: OpsLoginLogItem[];
  total?: number;
  page?: number;
  size?: number;
};

// ===================== notification preferences =====================

export type NotificationChannelState = {
  channel?: NotificationChannel;
  label?: string;
  enabled?: boolean;
  /** 系统强制开启（如 STATION），UI 不允许取消。 */
  locked?: boolean;
};

export type NotificationCategoryGroup = {
  category?: NotificationCategory;
  label?: string;
  channels?: NotificationChannelState[];
};

export type NotificationPreferenceView = {
  categories?: NotificationCategoryGroup[];
};

export type NotificationPreferenceItem = {
  category: NotificationCategory;
  channel: NotificationChannel;
  enabled?: boolean;
};

export type UpdateNotificationPreferencesRequest = {
  preferences: NotificationPreferenceItem[];
};

/** 运营 MFA 通道。 */
export type OpsMfaMethod = 'TOTP' | 'SMS' | 'EMAIL';

/** 验证器绑定挑战：secret + otpauth URI（扫码/手动添加后验码确认）。 */
export type OpsMfaSetupChallenge = {
  method?: OpsMfaMethod;
  totpSecret?: string | null;
  otpauthUri?: string | null;
  maskedTarget?: string | null;
};

/** 开启登录二步验证挑战：推导通道（TOTP 不发码 / EMAIL 已发码到掩码目标）。 */
export type OpsMfaEnableChallenge = {
  method?: OpsMfaMethod;
  maskedTarget?: string | null;
};

/** 一次性恢复码（开启成功后返回）。 */
export type OpsMfaRecoveryCodesResponse = {
  recoveryCodes?: string[];
};

/** 改手机号各步发码结果（脱敏目标 + 倒计时）。 */
export type OpsMobileChangeStep = {
  maskedTarget?: string;
  ttlSeconds?: number;
  resendAfterSeconds?: number;
};

// ===================== client =====================

export function opsProfileApi(http: HttpClient) {
  const base = '/api/v1/ops/profile';
  return {
    async getBasic(): Promise<OpsProfileBasic> {
      const res = await http.get<OpsProfileBasic>(`${base}/basic`);
      return res.data;
    },
    async getTeamsRoles(): Promise<OpsProfileTeamsRoles> {
      const res = await http.get<OpsProfileTeamsRoles>(`${base}/teams-roles`);
      return res.data;
    },
    async getSecurity(): Promise<OpsProfileSecurity> {
      const res = await http.get<OpsProfileSecurity>(`${base}/security`);
      return res.data;
    },
    async getSso(): Promise<OpsProfileSso> {
      const res = await http.get<OpsProfileSso>(`${base}/sso`);
      return res.data;
    },
    async updateProfile(body: UpdateOpsProfileRequest): Promise<void> {
      await http.put(base, body);
    },
    /** 上传头像（≤1MB，JPG/PNG）；返回新 fileId + 签名 URL。 */
    async uploadAvatar(file: File): Promise<OpsAvatarResult> {
      const form = new FormData();
      form.append('file', file);
      const res = await http.post<OpsAvatarResult>(`${base}/avatar`, form);
      return res.data;
    },
    async changePassword(body: ChangePasswordRequest): Promise<void> {
      await http.post(`${base}/change-password`, body);
    },
    async listSessions(): Promise<OpsSessionItem[]> {
      const res = await http.get<OpsSessionItem[]>(`${base}/sessions`);
      return res.data;
    },
    async revokeSession(sessionId: string): Promise<void> {
      await http.delete(`${base}/sessions/${encodeURIComponent(sessionId)}`);
    },
    async revokeOtherSessions(): Promise<void> {
      await http.post(`${base}/sessions/revoke-others`);
    },
    async listLoginLogs(query?: {
      page?: number;
      pageSize?: number;
    }): Promise<OpsLoginLogPageView> {
      const res = await http.get<OpsLoginLogPageView>(`${base}/login-logs`, {
        params: toWirePageParams(query),
      });
      return res.data;
    },
    async getNotificationPreferences(): Promise<NotificationPreferenceView> {
      const res = await http.get<NotificationPreferenceView>(`${base}/notification-preferences`);
      return res.data;
    },
    async updateNotificationPreferences(body: UpdateNotificationPreferencesRequest): Promise<void> {
      await http.put(`${base}/notification-preferences`, body);
    },

    // ===== 安全验证：验证器绑定 + 登录二步验证开关 =====
    /** 绑定身份验证器 · 起手：返回 secret + otpauth URI。 */
    async totpBindStart(): Promise<OpsMfaSetupChallenge> {
      const res = await http.post<OpsMfaSetupChallenge>(`${base}/mfa/totp/bind`);
      return res.data;
    },
    /** 绑定身份验证器 · 确认：校验新验证器动态码。 */
    async totpBindConfirm(code: string): Promise<void> {
      await http.post(`${base}/mfa/totp/bind/confirm`, { code });
    },
    /** 开启登录二步验证 · 起手：通道服务端推导（TOTP 不发码 / EMAIL 发码）。 */
    async mfaEnableStart(): Promise<OpsMfaEnableChallenge> {
      const res = await http.post<OpsMfaEnableChallenge>(`${base}/mfa/enable`);
      return res.data;
    },
    /** 开启登录二步验证 · 确认：验码 → 开启 + 返回 8 组一次性恢复码。 */
    async mfaEnableConfirm(code: string): Promise<OpsMfaRecoveryCodesResponse> {
      const res = await http.post<OpsMfaRecoveryCodesResponse>(`${base}/mfa/enable/confirm`, {
        code,
      });
      return res.data;
    },
    /** 关闭登录二步验证：需先经身份验证（stepUpToken）。 */
    async mfaDisable(stepUpToken: string): Promise<void> {
      await http.post(`${base}/mfa/disable`, { stepUpToken });
    },

    // ===== 改手机号 · step-up 前置身份验证后 =====
    async sendNewMobileCode(stepUpToken: string, newMobile: string): Promise<OpsMobileChangeStep> {
      const res = await http.post<OpsMobileChangeStep>(`${base}/mobile/send-new-code`, {
        stepUpToken,
        newMobile,
      });
      return res.data;
    },
    async confirmNewMobile(stepUpToken: string, code: string): Promise<void> {
      await http.post(`${base}/mobile/change`, { stepUpToken, code });
    },
  };
}
