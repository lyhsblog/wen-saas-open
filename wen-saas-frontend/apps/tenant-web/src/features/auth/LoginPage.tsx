import { useEffect, useState } from 'react';
import { Alert, App, Button, Checkbox, Form, Input, Segmented } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import type { LoginRequest } from '@wen-saas/domain';
import { ApiError, publicTenantApi } from '@wen-saas/api-client';
import { useCapabilitiesStore } from '@wen-saas/permissions';
import { authService, http } from '../../lib/http';
import { useTenantKey } from '../../lib/tenant-path';
import { OAuthLoginBlock, OAuthBindModal, type OAuthBindingPrompt } from './OAuthLoginBlock';
import { MfaVerifyStep, type MfaChallengeState } from './MfaVerifyStep';

type FormValues = {
  username: string;
  password: string;
};

type LoginError = {
  code: string;
  detail: string;
  attemptsLeft?: number;
};

export function LoginPage() {
  const navigate = useNavigate();
  const tenantKey = useTenantKey();
  const [params] = useSearchParams();
  const [submitting, setSubmitting] = useState(false);
  const { message, modal } = App.useApp();
  // 登录方式：短信验证码后端暂无端点，先置灰占位。
  const [mode, setMode] = useState<'password' | 'sms'>('password');
  const [rememberMe, setRememberMe] = useState(true);

  // 公开租户信息：渲染租户名 / Logo，并据 canonicalCode 处理旧子域名 90 天重定向。
  const { data: publicTenant } = useQuery({
    queryKey: ['public-tenant', tenantKey],
    queryFn: () => publicTenantApi(http).byCode(tenantKey),
    enabled: !!tenantKey,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });

  const canonicalCode = publicTenant?.canonicalCode;
  useEffect(() => {
    // 旧子域名命中别名 → 整页跳到规范租户子域（跨子域，不能用路由内 navigate）。
    if (canonicalCode && canonicalCode !== tenantKey) {
      const base = import.meta.env.VITE_TENANT_BASE_DOMAIN as string | undefined;
      if (!base) return;
      const qs = params.toString();
      const port = window.location.port ? `:${window.location.port}` : '';
      window.location.assign(
        `${window.location.protocol}//${canonicalCode}.${base}${port}/login${qs ? `?${qs}` : ''}`,
      );
    }
  }, [canonicalCode, tenantKey, params]);
  const [loginError, setLoginError] = useState<LoginError | null>(null);
  // OAuth 未绑定时进入绑定流：保存 bindToken + 渠道资料，弹出补账密表单。
  const [bindPrompt, setBindPrompt] = useState<OAuthBindingPrompt | null>(null);
  // 密码通过但账号开了登录二步验证：进入验证码步（凭 preAuthToken 走 /api/auth/mfa/*）。
  const [mfa, setMfa] = useState<MfaChallengeState | null>(null);

  // 登录成功后的统一收尾：清空能力包残留 + 跳转目标页（账密 / OAuth 直登 / 绑定后共用）。
  const finishLogin = () => {
    useCapabilitiesStore.getState().reset();
    const from = params.get('from');
    const target = from && from.startsWith('/') && !from.endsWith('/login') ? from : '/workbench';
    void navigate(target, { replace: true });
  };

  const toLoginError = (err: unknown, fallback: string): LoginError => {
    if (err instanceof ApiError) {
      const code = typeof err.problem.code === 'string' ? err.problem.code : '';
      const attemptsLeft =
        typeof err.problem['attemptsLeft'] === 'number' ? err.problem['attemptsLeft'] : undefined;
      return {
        code,
        detail: err.problem.detail ?? err.problem.title ?? fallback,
        ...(attemptsLeft != null && { attemptsLeft }),
      };
    }
    return { code: '', detail: '网络错误，请稍后重试' };
  };

  const handleFinish = async (values: FormValues) => {
    setSubmitting(true);
    setLoginError(null);
    try {
      const body: LoginRequest = {
        username: values.username,
        password: values.password,
        tenantCode: tenantKey,
        rememberMe,
      };
      const res = await authService.login(body);
      if (res.mfaRequired && res.preAuthToken) {
        // 已开启二步验证：EMAIL 通道先发码，TOTP 直接进输码
        let masked: string | null = null;
        if (res.mfaMethod === 'EMAIL') {
          try {
            const ch = await authService.mfaChallenge({ preAuthToken: res.preAuthToken });
            masked = ch.maskedTarget ?? null;
          } catch {
            setLoginError({ code: '', detail: '发送验证码失败，请重试' });
            return;
          }
        }
        setMfa({
          preAuthToken: res.preAuthToken,
          method: res.mfaMethod ?? null,
          masked,
        });
        return;
      }
      finishLogin();
    } catch (err) {
      setLoginError(toLoginError(err, '登录失败'));
    } finally {
      setSubmitting(false);
    }
  };

  // 发起第三方登录（mock 下用占位 authCode 触发后端流程）：已绑直登，未绑弹出绑定表单。
  const handleOAuth = async (provider: string) => {
    const AUTH_RESOURCES_READY = false; // 暂无三方/短信认证资源，先统一提示占位
    if (!AUTH_RESOURCES_READY) {
      message.warning('暂未开放(三方登录&短信，暂无认证资源)');
      return;
    }
    setLoginError(null);
    try {
      const authCode = `mock-${provider}-${tenantKey || 'default'}`;
      const res = await authService.oauthLogin(provider, { authCode });
      if (res.bindingRequired && res.bindToken) {
        setBindPrompt({
          provider,
          authCode,
          bindToken: res.bindToken,
          nickname: res.profile?.nickname ?? null,
          avatarUrl: res.profile?.avatarUrl ?? null,
        });
        return;
      }
      finishLogin();
    } catch (err) {
      setLoginError(toLoginError(err, '第三方登录失败'));
    }
  };

  // 绑定流：补账密完成绑定并登录。
  const handleBindSubmit = async (username: string, password: string): Promise<string | null> => {
    if (!bindPrompt) return null;
    try {
      await authService.oauthBind(bindPrompt.provider, {
        bindToken: bindPrompt.bindToken,
        username,
        password,
        tenantCode: tenantKey,
      });
      setBindPrompt(null);
      finishLogin();
      return null;
    } catch (err) {
      return toLoginError(err, '绑定失败').detail;
    }
  };

  const isLocked = loginError?.code === 'LOGIN_LOCKED';

  return (
    <div style={{ minHeight: '100vh', display: 'flex', background: 'var(--bg-elev)' }}>
      {/* 左 · 品牌叙事面板（白标：仅 Logo + 欢迎语，不暴露租户业务数据） */}
      <div
        style={{
          flex: 1,
          minWidth: 360,
          padding: '64px 56px',
          display: 'flex',
          flexDirection: 'column',
          color: '#fff',
          position: 'relative',
          overflow: 'hidden',
          background: 'linear-gradient(135deg, oklch(0.45 0.18 250) 0%, oklch(0.52 0.20 268) 100%)',
        }}
      >
        <svg
          style={{ position: 'absolute', right: -120, top: -120, opacity: 0.18 }}
          width="500"
          height="500"
          viewBox="0 0 500 500"
          aria-hidden
        >
          <circle cx="250" cy="250" r="200" stroke="#fff" strokeWidth="1.5" fill="none" />
          <circle cx="250" cy="250" r="140" stroke="#fff" strokeWidth="1.5" fill="none" />
          <circle cx="250" cy="250" r="80" stroke="#fff" strokeWidth="1.5" fill="none" />
        </svg>
        <svg
          style={{ position: 'absolute', left: -80, bottom: -100, opacity: 0.14 }}
          width="320"
          height="320"
          viewBox="0 0 320 320"
          aria-hidden
        >
          <rect
            x="20"
            y="20"
            width="280"
            height="280"
            rx="40"
            stroke="#fff"
            strokeWidth="1.5"
            fill="none"
            transform="rotate(15 160 160)"
          />
        </svg>

        {/* Logo + 租户名 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, position: 'relative' }}>
          <div
            style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              background: '#fff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
              flexShrink: 0,
            }}
          >
            {publicTenant?.logoUrl ? (
              <img
                src={publicTenant.logoUrl}
                alt="logo"
                style={{ maxWidth: '82%', maxHeight: '82%' }}
              />
            ) : (
              <svg width="26" height="26" viewBox="0 0 26 26" aria-hidden>
                <path
                  d="M7 9 L11 17 L13 11 L15 17 L19 9"
                  stroke="oklch(0.45 0.18 250)"
                  strokeWidth="2.2"
                  fill="none"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            )}
          </div>
          <div>
            <div style={{ fontSize: 18, fontWeight: 700, letterSpacing: '-0.01em' }}>
              {publicTenant?.name || 'Wen SaaS Boot'}
            </div>
            <div style={{ fontSize: 12, opacity: 0.7, marginTop: 2 }}>
              Wen SaaS Boot · 多租户协同平台
            </div>
          </div>
        </div>

        {/* 主叙事 */}
        <div style={{ marginTop: 'auto', marginBottom: 'auto', position: 'relative' }}>
          <div style={{ fontSize: 40, fontWeight: 700, lineHeight: 1.2, letterSpacing: '-0.02em' }}>
            欢迎回来
          </div>
          <div
            style={{ marginTop: 14, fontSize: 16, opacity: 0.78, lineHeight: 1.7, maxWidth: 380 }}
          >
            登录进入您的工作空间。
          </div>
        </div>

        <div
          style={{
            marginTop: 'auto',
            fontSize: 11,
            opacity: 0.55,
            position: 'relative',
            fontFamily: 'var(--font-mono)',
          }}
        >
          {window.location.host}
        </div>
      </div>

      {/* 右 · 表单面板 */}
      <div
        style={{
          width: 480,
          flexShrink: 0,
          padding: '64px 56px',
          background: 'var(--bg-elev)',
          display: 'flex',
          flexDirection: 'column',
          position: 'relative',
        }}
      >
        {/* 右上：连接状态 + 使用帮助 */}
        <div
          style={{
            position: 'absolute',
            top: 24,
            right: 24,
            display: 'flex',
            gap: 12,
            alignItems: 'center',
            fontSize: 12,
            color: 'var(--text-tertiary)',
          }}
        >
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <span style={{ width: 6, height: 6, borderRadius: 3, background: 'var(--success)' }} />
            已连接
          </span>
          <span style={{ color: 'var(--border-strong)' }}>|</span>
          <a
            style={{ color: 'var(--text-secondary)', cursor: 'pointer' }}
            onClick={() => {
              message.info('如需帮助，请联系贵司主管理员或平台客服。');
            }}
          >
            使用帮助
          </a>
        </div>

        <div style={{ marginBottom: 28 }}>
          <div
            style={{
              fontSize: 24,
              fontWeight: 700,
              letterSpacing: '-0.01em',
              color: 'var(--text)',
            }}
          >
            登录
          </div>
          <div style={{ fontSize: 13, color: 'var(--text-tertiary)', marginTop: 8 }}>
            使用您的工作账号继续 · 租户{' '}
            <code style={{ color: 'var(--text-secondary)', fontWeight: 600 }}>
              {tenantKey || '—'}
            </code>
          </div>
        </div>

        {mfa ? (
          <MfaVerifyStep
            mfa={mfa}
            rememberMe={rememberMe}
            onSuccess={finishLogin}
            onBack={() => setMfa(null)}
          />
        ) : (
          <>
            {/* 登录方式切换（短信验证码后端暂无端点，先置灰） */}
            <Segmented
              value={mode}
              onChange={(v) => setMode(v as 'password' | 'sms')}
              options={[
                { label: '账号密码', value: 'password' },
                { label: '短信验证码（即将上线）', value: 'sms', disabled: true },
              ]}
              style={{ marginBottom: 20, alignSelf: 'flex-start' }}
            />

            {loginError && (
              <Alert
                type={isLocked ? 'error' : 'warning'}
                showIcon
                style={{ marginBottom: 16 }}
                message={
                  isLocked
                    ? loginError.detail
                    : loginError.attemptsLeft != null
                      ? `${loginError.detail}（还剩 ${loginError.attemptsLeft} 次机会）`
                      : loginError.detail
                }
              />
            )}

            <Form<FormValues>
              layout="vertical"
              requiredMark={false}
              onFinish={(values) => {
                void handleFinish(values);
              }}
              initialValues={{ username: '', password: '' }}
              disabled={isLocked}
            >
              <Form.Item
                name="username"
                label="账号"
                rules={[{ required: true, message: '请输入账号' }]}
              >
                <Input
                  prefix={<UserOutlined style={{ color: 'var(--text-tertiary)' }} />}
                  placeholder="账号 / 手机号"
                  autoComplete="username"
                  size="large"
                />
              </Form.Item>
              <Form.Item
                name="password"
                label="密码"
                rules={[{ required: true, message: '请输入密码' }]}
              >
                <Input.Password
                  prefix={<LockOutlined style={{ color: 'var(--text-tertiary)' }} />}
                  placeholder="密码"
                  autoComplete="current-password"
                  size="large"
                />
              </Form.Item>

              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: 16,
                }}
              >
                <Checkbox
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  disabled={isLocked}
                >
                  <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>7 天内免登录</span>
                </Checkbox>
                <a
                  style={{ fontSize: 13, color: 'var(--brand)', cursor: 'pointer' }}
                  onClick={() => {
                    modal.info({
                      title: '忘记密码',
                      content:
                        '请联系贵司主管理员为你重置密码；重置链接将发送到你账号绑定的手机 / 邮箱。',
                    });
                  }}
                >
                  忘记密码？
                </a>
              </div>

              <Form.Item style={{ marginBottom: 0 }}>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={submitting}
                  block
                  size="large"
                  style={{ height: 44, fontWeight: 600 }}
                >
                  登录
                </Button>
              </Form.Item>
            </Form>

            <OAuthLoginBlock
              disabled={isLocked}
              onLogin={(provider) => {
                void handleOAuth(provider);
              }}
            />
          </>
        )}

        <div
          style={{
            marginTop: 'auto',
            paddingTop: 24,
            fontSize: 12,
            color: 'var(--text-tertiary)',
            textAlign: 'center',
          }}
        >
          {/* 底座不承载具体条款内容；采用方接入真实文档时将两处改为 <a href target="_blank"> */}
          登录即表示同意本平台服务条款与隐私政策
          <span style={{ color: 'var(--text-disabled)' }}>（具体内容以部署方公示为准）</span>
          <div style={{ marginTop: 6 }}>
            © 2026 Wen SaaS Boot ·{' '}
            {publicTenant?.name ? `由 ${publicTenant.name} 部署` : '多租户协同平台'}
          </div>
        </div>
      </div>

      {bindPrompt && (
        <OAuthBindModal
          prompt={bindPrompt}
          onCancel={() => setBindPrompt(null)}
          onSubmit={handleBindSubmit}
        />
      )}
    </div>
  );
}
