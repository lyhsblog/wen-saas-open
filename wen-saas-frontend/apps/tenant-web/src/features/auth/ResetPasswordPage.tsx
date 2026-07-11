import { useEffect, useState } from 'react';
import { Alert, App, Button, Card, Form, Input, Result, Spin } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router';
import { useQuery, useMutation } from '@tanstack/react-query';
import { ApiError, publicResetApi, type ResetPasswordPurpose } from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { useTenantKey } from '../../lib/tenant-path';

type FormValues = {
  usernameInput: string;
  newPassword: string;
  confirmPassword: string;
};

type PurposeCopy = {
  tag: string;
  title: string;
  hint: string;
  accountHint: string;
  successText: string;
};

// 未覆盖的 purpose（如 init_admin）走 DEFAULT_COPY 兜底。
const PURPOSE_COPY: Partial<Record<ResetPasswordPurpose, PurposeCopy>> = {
  reset_admin: {
    tag: '激活管理员',
    title: '租户已为你开通',
    hint: '运营已开通租户并把你设为主管理员。请先校验账号身份，然后设置登录密码。',
    accountHint: '运营在开通时设置的主管理员账号',
    successText: '密码已设置，即将跳转登录页',
  },
  reset_member: {
    tag: '激活账号',
    title: '欢迎，请设置初始密码',
    hint: '管理员已为你创建账号。请先校验账号身份，然后设置一个登录密码。',
    accountHint: '管理员添加你时填写的账号',
    successText: '账号已激活，即将跳转登录页',
  },
};

const DEFAULT_COPY: PurposeCopy = {
  tag: '设置密码',
  title: '设置初始密码',
  hint: '请先校验账号身份，然后设置一个登录密码。',
  accountHint: '你的登录账号',
  successText: '密码设置成功，即将跳转登录页',
};

export function ResetPasswordPage() {
  const { message } = App.useApp();
  const { token = '' } = useParams<{ token: string }>();
  const tenantKey = useTenantKey();
  const navigate = useNavigate();
  const [submitted, setSubmitted] = useState(false);

  // 公开页豁免 tokens.css 的 body min-width:1280（重置链接常被手机打开）
  useEffect(() => {
    document.body.classList.add('public-page');
    return () => document.body.classList.remove('public-page');
  }, []);

  const {
    data: tokenInfo,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['reset-token', token],
    queryFn: () => publicResetApi(http).checkToken(token),
    enabled: !!token,
    retry: false,
  });

  const { mutateAsync, isPending } = useMutation({
    mutationFn: (values: Pick<FormValues, 'usernameInput' | 'newPassword'>) =>
      publicResetApi(http).submitReset(token, values),
  });

  if (isLoading) {
    return (
      <div style={centerStyle}>
        <Spin size="large" />
      </div>
    );
  }

  if (isError || !tokenInfo) {
    return (
      <div style={centerStyle}>
        <Result
          status="404"
          title="链接无效"
          subTitle="密码设置链接不存在，请联系运营人员重新生成。"
          extra={
            <Button type="primary" onClick={() => void navigate('/login')}>
              去登录
            </Button>
          }
        />
      </div>
    );
  }

  if (tokenInfo.status === 'USED') {
    return (
      <div style={centerStyle}>
        <Result
          status="success"
          title="密码已设置"
          subTitle="该链接已使用，您的账号已激活，请直接登录。"
          extra={
            <Button type="primary" onClick={() => void navigate('/login')}>
              去登录
            </Button>
          }
        />
      </div>
    );
  }

  if (tokenInfo.status === 'EXPIRED' || tokenInfo.status === 'INVALIDATED') {
    const reason =
      tokenInfo.status === 'EXPIRED' ? '已过期（超过 48 小时）' : '已作废（运营重新生成了新链接）';
    return (
      <div style={centerStyle}>
        <Result
          status="warning"
          title="链接不可用"
          subTitle={`该密码设置链接${reason}，请联系运营人员重新生成。`}
          extra={
            <Button type="primary" onClick={() => void navigate('/login')}>
              去登录
            </Button>
          }
        />
      </div>
    );
  }

  const copy = (tokenInfo.purpose ? PURPOSE_COPY[tokenInfo.purpose] : null) ?? DEFAULT_COPY;

  const handleFinish = async (values: FormValues) => {
    try {
      await mutateAsync({ usernameInput: values.usernameInput, newPassword: values.newPassword });
      setSubmitted(true);
      void message.success(copy.successText);
      setTimeout(() => {
        void navigate('/login', { replace: true });
      }, 1500);
    } catch (err) {
      if (err instanceof ApiError) {
        message.error(err.problem.detail ?? err.problem.title ?? '设置失败');
      } else {
        message.error('网络错误，请稍后重试');
      }
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        background: 'linear-gradient(180deg, var(--brand-bg) 0%, var(--bg) 50%)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
      }}
    >
      <Card
        bordered={false}
        style={{
          width: 'min(440px, calc(100vw - 32px))',
          boxShadow: 'var(--shadow-lg)',
          borderRadius: 'var(--r-xl)',
        }}
        styles={{ body: { padding: '40px 36px 32px' } }}
      >
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--text)' }}>{copy.title}</div>
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>
            租户：
            <code style={{ color: 'var(--text)', fontWeight: 600 }}>
              {tokenInfo.tenantName || tenantKey}
            </code>
          </div>
        </div>

        <Alert type="info" showIcon style={{ marginBottom: 16 }} message={copy.hint} />
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 20 }}
          message={
            <span>
              账号：<strong>{tokenInfo.usernameMask}</strong>（请输入完整账号确认身份）
            </span>
          }
        />

        <Form<FormValues>
          layout="vertical"
          requiredMark={false}
          onFinish={(values) => {
            void handleFinish(values);
          }}
          disabled={submitted}
        >
          <Form.Item
            name="usernameInput"
            label="账号"
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input
              prefix={<UserOutlined style={{ color: 'var(--text-tertiary)' }} />}
              placeholder={copy.accountHint}
              autoComplete="username"
              size="large"
            />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 8, message: '密码至少 8 位' },
              {
                validator: (_, v: string) =>
                  /[a-zA-Z]/.test(v) && /[0-9]/.test(v)
                    ? Promise.resolve()
                    : Promise.reject(new Error('密码须同时包含字母和数字')),
              },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: 'var(--text-tertiary)' }} />}
              placeholder="至少 8 位，须含字母和数字"
              autoComplete="new-password"
              size="large"
            />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认密码"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入密码' },
              ({ getFieldValue }) => ({
                validator: (_, v) =>
                  !v || getFieldValue('newPassword') === v
                    ? Promise.resolve()
                    : Promise.reject(new Error('两次密码不一致')),
              }),
            ]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: 'var(--text-tertiary)' }} />}
              placeholder="再次输入密码"
              autoComplete="new-password"
              size="large"
            />
          </Form.Item>
          <Form.Item style={{ marginTop: 12, marginBottom: 0 }}>
            <Button
              type="primary"
              htmlType="submit"
              loading={isPending}
              block
              size="large"
              style={{ height: 44, fontWeight: 600 }}
            >
              确认设置密码
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}

const centerStyle: React.CSSProperties = {
  minHeight: '100vh',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
};
