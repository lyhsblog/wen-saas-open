/** 三个 OAuth 渠道（与 tenant-web 一致），当前均走 mock 客户端。 */
export type OAuthProvider = 'wechat' | 'wecom' | 'dingtalk';

// 图标圈内用 name 首字 monogram（渲染时取 name[0]），标签用全称，避免缩写与标签称呼不一致。
export const PROVIDER_META: Record<OAuthProvider, { name: string; color: string }> = {
  wechat: { name: '微信', color: '#07C160' },
  wecom: { name: '企业微信', color: '#0e62d6' },
  dingtalk: { name: '钉钉', color: '#1677ff' },
};

export const ALL_PROVIDERS: OAuthProvider[] = ['wechat', 'wecom', 'dingtalk'];

/** 运行环境（按 UA 识别），决定置顶推荐的一键登录方式。 */
export type RuntimeEnv = OAuthProvider | 'browser';

export function detectEnv(ua: string = navigator.userAgent): RuntimeEnv {
  const s = ua.toLowerCase();
  // 企微 UA 同时含 micromessenger + wxwork，须先判企微
  if (s.includes('wxwork')) return 'wecom';
  if (s.includes('dingtalk')) return 'dingtalk';
  if (s.includes('micromessenger')) return 'wechat';
  return 'browser';
}

export const ENV_HINT: Record<OAuthProvider, string> = {
  wechat: '检测到微信环境 · 推荐使用微信一键登录',
  wecom: '检测到企业微信环境 · 推荐使用企微一键登录',
  dingtalk: '检测到钉钉环境 · 推荐使用钉钉一键登录',
};

/** mock 后端约定的 authCode（真实接入后改为 SDK 换取的 code，见 R2 剩余项）。 */
export function mockAuthCode(provider: OAuthProvider, scope: string): string {
  return `mock-${provider}-${scope || 'default'}`;
}
