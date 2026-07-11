import type { HttpClient } from './http';

// 成员自助忘记密码不做（找管理员重置签发 reset_member），故无自助类 purpose。
export type ResetPasswordPurpose = 'init_admin' | 'reset_admin' | 'reset_member';

export type ResetPasswordView = {
  tenantName: string;
  tenantCode: string;
  usernameMask: string;
  expiresAt: string;
  status: 'VALID' | 'EXPIRED' | 'USED' | 'INVALIDATED';
  purpose: ResetPasswordPurpose | null;
};

export type SubmitResetPasswordRequest = {
  usernameInput: string;
  newPassword: string;
};

export type ResetPasswordResult = {
  tenantCode: string;
};

export function publicResetApi(http: HttpClient) {
  const base = '/api/v1/public/reset-password';
  return {
    async checkToken(token: string): Promise<ResetPasswordView> {
      const res = await http.get<ResetPasswordView>(
        `${base}/by-token/${encodeURIComponent(token)}`,
      );
      return res.data;
    },
    async submitReset(
      token: string,
      body: SubmitResetPasswordRequest,
    ): Promise<ResetPasswordResult> {
      const res = await http.post<ResetPasswordResult>(
        `${base}/by-token/${encodeURIComponent(token)}`,
        body,
      );
      return res.data;
    },
  };
}
