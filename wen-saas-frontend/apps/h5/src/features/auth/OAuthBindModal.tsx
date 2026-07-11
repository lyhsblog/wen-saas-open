import { useState } from 'react';
import { Button, CenterPopup, Form, Input } from 'antd-mobile';
import { PROVIDER_META, type OAuthProvider } from './oauth';

export type BindPrompt = {
  provider: OAuthProvider;
  bindToken: string;
  nickname?: string | null;
};

/** 首次 OAuth 未绑定：补平台账号密码完成绑定（首次扫码绑定流程）。 */
export function OAuthBindModal({
  prompt,
  onCancel,
  onSubmit,
}: {
  prompt: BindPrompt;
  onCancel: () => void;
  /** 返回错误文案（失败）或 null（成功）。 */
  onSubmit: (username: string, password: string) => Promise<string | null>;
}) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const m = PROVIDER_META[prompt.provider];

  const handleFinish = (v: { username: string; password: string }) => {
    setSubmitting(true);
    setError(null);
    void onSubmit(v.username.trim(), v.password)
      .then(setError)
      .finally(() => setSubmitting(false));
  };

  return (
    <CenterPopup visible closeOnMaskClick={false} style={{ '--max-width': '340px' }}>
      <div style={{ padding: '20px 20px 12px' }}>
        <div style={{ fontSize: 17, fontWeight: 600, marginBottom: 6 }}>绑定{m.name}账号</div>
        <div style={{ fontSize: 12, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
          该{m.name}账号尚未绑定，请输入平台账号密码完成绑定，以后即可直接登录。
        </div>
        {error && (
          <div style={{ color: 'var(--danger, #dc2626)', fontSize: 13, marginTop: 10 }}>
            {error}
          </div>
        )}
      </div>
      <Form
        layout="vertical"
        onFinish={handleFinish}
        footer={
          <div style={{ display: 'flex', gap: 10 }}>
            <Button block fill="outline" onClick={onCancel} disabled={submitting}>
              取消
            </Button>
            <Button block type="submit" color="primary" loading={submitting}>
              绑定并登录
            </Button>
          </div>
        }
      >
        <Form.Item name="username" label="账号" rules={[{ required: true, message: '请输入账号' }]}>
          <Input placeholder="账号 / 手机号" clearable />
        </Form.Item>
        <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
          <Input type="password" placeholder="密码" clearable />
        </Form.Item>
      </Form>
    </CenterPopup>
  );
}
