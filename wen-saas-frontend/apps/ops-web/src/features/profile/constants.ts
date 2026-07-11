import type { NotificationCategory } from '@wen-saas/api-client';

export const SECTIONS = [
  { id: 'basic', label: '基本信息' },
  { id: 'team', label: '团队与角色' },
  { id: 'security', label: '安全设置' },
  { id: 'sso', label: '企业 SSO' },
  { id: 'devices', label: '登录设备' },
  { id: 'notify', label: '通知偏好' },
] as const;

export type SectionId = (typeof SECTIONS)[number]['id'];

export const GENDER_LABEL: Record<number, string> = {
  0: '未知',
  1: '男',
  2: '女',
};

/** 登录二步验证 / 身份验证通道展示名。 */
export const MFA_METHOD_LABEL: Record<string, string> = {
  TOTP: '身份验证器 (TOTP)',
  SMS: '短信验证码',
  EMAIL: '邮箱验证码',
};

// 暂无短信认证资源：依赖短信的入口统一置「暂未开放」；接入后翻开关即恢复（与登录页 AUTH_RESOURCES_READY 同口径）
export const SMS_READY = false;
export const SMS_CLOSED_TAG = '短信服务未接入';


export const LOGIN_METHOD_LABEL: Record<string, string> = {
  PASSWORD: '密码',
  SSO: 'SSO',
  MFA: '二步验证',
};

export const LOGIN_RESULT_LABEL: Record<
  string,
  { text: string; tone: 'success' | 'danger' | 'warning' }
> = {
  SUCCESS: { text: '成功', tone: 'success' },
  FAIL: { text: '失败', tone: 'danger' },
  LOCKED: { text: '已锁定', tone: 'warning' },
};

export const CATEGORY_DESC: Record<NotificationCategory, string> = {
  TODO_TASK: '@我、待审批',
  TICKET_ALERT: '工单超 SLA、新工单分派给我',
  CUSTOMER_HEALTH: '我跟进的租户进入"危险"',
  PACKAGE_EXPIRY: '我开通/跟进的租户临近到期',
  SYSTEM_ANNOUNCEMENT: '平台公告与维护通知',
  SECURITY_ALERT: '新设备登录 / 密码变更',
};

/** UI 暴露的 4 个通知通道列；最后一列"企业 IM 推送"是占位（不送后端）。 */
export const NOTIFY_UI_CHANNELS = [
  { key: 'STATION', label: '站内信', hint: '系统强制', locked: true, placeholder: false },
  { key: 'EMAIL', label: '企业邮箱', hint: undefined, locked: false, placeholder: false },
  { key: 'SMS', label: '短信', hint: '平台付费', locked: false, placeholder: false },
  { key: 'IM_PUSH', label: '企业 IM 推送', hint: '敬请期待', locked: true, placeholder: true },
] as const;
