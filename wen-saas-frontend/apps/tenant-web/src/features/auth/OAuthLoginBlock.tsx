import { useState } from 'react';
import { Avatar, Button, Form, Input, Modal, Tooltip } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';

/** 绑定流上下文：登录页发起 OAuth 未绑定时携带，供绑定弹窗补账密。 */
export type OAuthBindingPrompt = {
  provider: string;
  authCode: string;
  bindToken: string;
  nickname?: string | null;
  avatarUrl?: string | null;
};

/** 三个渠道元数据；当前均走 mock 客户端，三渠道同态可用。 */
const PROVIDERS = [
  { kind: 'wechat', name: '微信', color: '#07C160', enabled: true },
  { kind: 'wecom', name: '企业微信', color: '#0E62D6', enabled: true },
  { kind: 'dingtalk', name: '钉钉', color: '#1677FF', enabled: true },
] as const;

/** 登录页第三方登录区块：分隔线 + 三个渠道图标按钮。 */
export function OAuthLoginBlock({
  disabled,
  onLogin,
}: {
  disabled?: boolean;
  onLogin: (provider: string) => void;
}) {
  return (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          margin: '28px 0 18px',
          color: 'var(--text-tertiary)',
        }}
      >
        <div style={{ flex: 1, height: 1, background: 'var(--divider)' }} />
        <span style={{ fontSize: 11 }}>第三方账号登录</span>
        <div style={{ flex: 1, height: 1, background: 'var(--divider)' }} />
      </div>
      <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
        {PROVIDERS.map((o) => {
          const usable = o.enabled && !disabled;
          const btn = (
            <button
              key={o.kind}
              type="button"
              disabled={!usable}
              onClick={() => usable && onLogin(o.kind)}
              title={o.enabled ? `${o.name} 登录` : `${o.name} 登录即将上线`}
              style={{
                flex: 1,
                minWidth: 0,
                height: 48,
                padding: '0 8px',
                borderRadius: 'var(--r-md)',
                border: '1px solid var(--border)',
                background: 'var(--bg-elev)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                whiteSpace: 'nowrap',
                cursor: usable ? 'pointer' : 'not-allowed',
                color: o.color,
                opacity: o.enabled ? 1 : 0.45,
                fontSize: 13,
                fontWeight: 600,
              }}
            >
              {o.name}
            </button>
          );
          return o.enabled ? (
            btn
          ) : (
            <Tooltip key={o.kind} title="即将上线">
              <span style={{ display: 'flex', flex: 1, minWidth: 0 }}>{btn}</span>
            </Tooltip>
          );
        })}
      </div>
    </>
  );
}

const PROVIDER_LABEL: Record<string, string> = {
  wechat: '微信',
  wecom: '企业微信',
  dingtalk: '钉钉',
};

/** 绑定流弹窗：展示渠道资料 + 输入平台账号密码完成绑定（首次扫码绑定流程）。 */
export function OAuthBindModal({
  prompt,
  onCancel,
  onSubmit,
}: {
  prompt: OAuthBindingPrompt;
  onCancel: () => void;
  /** 返回错误文案（绑定失败）或 null（成功）。 */
  onSubmit: (username: string, password: string) => Promise<string | null>;
}) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const label = PROVIDER_LABEL[prompt.provider] ?? prompt.provider;

  return (
    <Modal open title={`绑定${label}账号`} onCancel={onCancel} footer={null} destroyOnHidden>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <Avatar src={prompt.avatarUrl ?? undefined} style={{ background: 'var(--brand)' }}>
          {(prompt.nickname ?? label).slice(0, 1)}
        </Avatar>
        <div>
          <div style={{ fontWeight: 600 }}>{prompt.nickname ?? `${label}用户`}</div>
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
            该{label}账号尚未绑定，请输入平台账号密码完成绑定，以后扫码即可直接登录
          </div>
        </div>
      </div>
      {error && (
        <div style={{ color: 'var(--danger, #d4380d)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}
      <Form
        layout="vertical"
        requiredMark={false}
        onFinish={(v: { username: string; password: string }) => {
          setSubmitting(true);
          setError(null);
          void onSubmit(v.username, v.password)
            .then((err) => setError(err))
            .finally(() => setSubmitting(false));
        }}
      >
        <Form.Item name="username" label="账号" rules={[{ required: true, message: '请输入账号' }]}>
          <Input
            prefix={<UserOutlined />}
            placeholder="账号 / 手机号"
            size="large"
            autoComplete="username"
          />
        </Form.Item>
        <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password
            prefix={<LockOutlined />}
            placeholder="密码"
            size="large"
            autoComplete="current-password"
          />
        </Form.Item>
        <Form.Item style={{ marginBottom: 0 }}>
          <Button type="primary" htmlType="submit" loading={submitting} block size="large">
            绑定并登录
          </Button>
        </Form.Item>
      </Form>
    </Modal>
  );
}
