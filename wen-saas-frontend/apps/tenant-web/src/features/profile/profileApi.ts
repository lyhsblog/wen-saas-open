import { tenantProfileApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const profileApi = tenantProfileApi(http);

/** 性别取值：后端 Integer（0=保密/未知 · 1=男 · 2=女）。 */
export const GENDER_OPTIONS = [
  { value: 2, label: '女' },
  { value: 1, label: '男' },
  { value: 0, label: '保密' },
] as const;

export function genderLabel(gender?: number | null): string {
  return GENDER_OPTIONS.find((g) => g.value === gender)?.label ?? '保密';
}

/** 第三方账号 provider 元数据（OAuth 登录模块未上线，当前仅展示占位）。 */
export const OAUTH_PROVIDERS = [
  { kind: 'wechat', name: '微信', color: '#07C160' },
  { kind: 'wecom', name: '企业微信', color: '#0E62D6' },
  { kind: 'dingtalk', name: '钉钉', color: '#1677FF' },
] as const;
