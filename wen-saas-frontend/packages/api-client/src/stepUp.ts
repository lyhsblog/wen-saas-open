import type { HttpClient } from './http';

/**
 * 敏感操作 step-up（前置身份验证）通用端点封装，运营 / 租户共用。
 *
 * 流程：getChannels（拿可用通道 + 掩码，未配置置灰）→ challenge（短信/邮件发码；TOTP 免）
 * → verify（校验→拿 stepUpToken）→ 把 token 交给改密码 / 改手机 / 改邮箱端点。
 */

export type StepUpMethod = 'TOTP' | 'SMS' | 'EMAIL';

export type StepUpChannelAvail = {
  available?: boolean;
  maskedTarget?: string | null;
};

export type StepUpChannels = {
  totp?: StepUpChannelAvail;
  sms?: StepUpChannelAvail;
  email?: StepUpChannelAvail;
};

export type StepUpChallenge = {
  channel?: StepUpMethod;
  maskedTarget?: string | null;
  ttlSeconds?: number;
  resendAfterSeconds?: number;
};

export type StepUpTicket = {
  stepUpToken?: string;
  expiresInSeconds?: number;
};

export function stepUpApi(http: HttpClient) {
  const base = '/api/v1/security/stepup';
  return {
    async getChannels(): Promise<StepUpChannels> {
      const res = await http.get<StepUpChannels>(`${base}/channels`);
      return res.data;
    },
    async challenge(channel: StepUpMethod): Promise<StepUpChallenge> {
      const res = await http.post<StepUpChallenge>(`${base}/challenge`, { channel });
      return res.data;
    },
    async verify(channel: StepUpMethod, code: string): Promise<StepUpTicket> {
      const res = await http.post<StepUpTicket>(`${base}/verify`, { channel, code });
      return res.data;
    },
  };
}
