import { useMemo, useState } from 'react';
import { Button, Form, Input, Toast } from 'antd-mobile';
import { useNavigate, useSearchParams } from 'react-router';
import type { LoginRequest } from '@wen-saas/domain';
import { ApiError } from '@wen-saas/api-client';
import { useCapabilitiesStore } from '@wen-saas/permissions';
import { authService } from '../../lib/http';
import { useTenantKey } from '../../lib/tenant-path';
import { OAuthBlock } from './OAuthBlock';
import { OAuthBindModal, type BindPrompt } from './OAuthBindModal';
import { detectEnv, mockAuthCode, type OAuthProvider } from './oauth';

type FormValues = { username: string; password: string };

type MfaState = {
  preAuthToken: string;
  method: string | null; // 推导通道：TOTP / EMAIL
  masked: string | null;
};

export function LoginPage() {
  const navigate = useNavigate();
  const tenantKey = useTenantKey();
  const [params] = useSearchParams();
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [oauthBusy, setOauthBusy] = useState(false);
  const [bindPrompt, setBindPrompt] = useState<BindPrompt | null>(null);
  // 密码通过但账号开了登录二步验证：进入验证码步（凭 preAuthToken 走 /api/auth/mfa/*）。
  const [mfa, setMfa] = useState<MfaState | null>(null);
  const [mfaCode, setMfaCode] = useState('');
  const [useRecovery, setUseRecovery] = useState(false);
  const env = useMemo(() => detectEnv(), []);

  const finishLogin = () => {
    useCapabilitiesStore.getState().reset();
    const from = params.get('from');
    const target =
      from && from.startsWith('/') && !from.endsWith('/login') ? from : '/workspace';
    void navigate(target, { replace: true });
  };

  const toErrorMsg = (err: unknown): string => {
    if (err instanceof ApiError) {
      return err.problem.detail ?? err.problem.title ?? '登录失败';
    }
    return '网络错误，请稍后重试';
  };

  const handleFinish = async (values: FormValues) => {
    setSubmitting(true);
    setErrorMsg(null);
    try {
      const body: LoginRequest = {
        username: values.username.trim(),
        password: values.password,
        tenantCode: tenantKey,
      };
      const res = await authService.login(body);
      if (res.mfaRequired && res.preAuthToken) {
        // 已开启二步验证：EMAIL 通道先发码，TOTP 直接进输码
        let masked: string | null = null;
        if (res.mfaMethod === 'EMAIL') {
          const ch = await authService.mfaChallenge({ preAuthToken: res.preAuthToken });
          masked = ch.maskedTarget ?? null;
        }
        setMfa({ preAuthToken: res.preAuthToken, method: res.mfaMethod ?? null, masked });
        return;
      }
      finishLogin();
    } catch (err) {
      setErrorMsg(toErrorMsg(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleMfaVerify = async () => {
    if (!mfa || submitting) return;
    setSubmitting(true);
    setErrorMsg(null);
    try {
      await authService.mfaVerify({ preAuthToken: mfa.preAuthToken, code: mfaCode.trim() });
      finishLogin();
    } catch {
      setErrorMsg(useRecovery ? '恢复码错误或已被使用' : '验证码错误或已过期，请重新输入');
      setMfaCode('');
    } finally {
      setSubmitting(false);
    }
  };

  // 第三方一键登录（mock）：已绑直登；未绑返回 bindToken → 弹绑定补账密。
  const handleOAuth = async (provider: OAuthProvider) => {
    const AUTH_RESOURCES_READY = false; // 暂无三方/短信认证资源，先统一提示占位
    if (!AUTH_RESOURCES_READY) {
      Toast.show({ content: '暂未开放(三方登录&短信，暂无认证资源)' });
      return;
    }
    setErrorMsg(null);
    setOauthBusy(true);
    try {
      const res = await authService.oauthLogin(provider, {
        authCode: mockAuthCode(provider, tenantKey),
      });
      if (res.bindingRequired && res.bindToken) {
        setBindPrompt({
          provider,
          bindToken: res.bindToken,
          nickname: res.profile?.nickname ?? null,
        });
        return;
      }
      finishLogin();
    } catch (err) {
      setErrorMsg(toErrorMsg(err));
    } finally {
      setOauthBusy(false);
    }
  };

  // 绑定流：补账密完成绑定并登录；返回错误文案或 null。
  const handleBind = async (username: string, password: string): Promise<string | null> => {
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
      return toErrorMsg(err);
    }
  };

  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'flex',
        flexDirection: 'column',
        padding: '60px 28px 40px',
        background: 'linear-gradient(180deg, var(--brand-bg) 0%, var(--bg-elev) 40%)',
      }}
    >
      {/* Logo */}
      <div
        style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', marginBottom: 36 }}
      >
        <div
          style={{
            width: 76,
            height: 76,
            borderRadius: 22,
            background: 'var(--brand)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 12px 24px rgba(43,95,255,0.32)',
          }}
        >
          <svg width="44" height="44" viewBox="0 0 26 26">
            <path
              d="M7 9 L11 17 L13 11 L15 17 L19 9"
              stroke="#fff"
              strokeWidth="2.2"
              fill="none"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </div>
        <div style={{ marginTop: 14, fontSize: 18, fontWeight: 700 }}>Wen SaaS Boot</div>
        <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>
          多租户协同平台
        </div>
      </div>

      {errorMsg && (
        <div
          style={{
            padding: '10px 12px',
            background: 'var(--danger-bg, #fef2f2)',
            border: '1px solid var(--danger-border, #fecaca)',
            borderRadius: 10,
            fontSize: 13,
            color: 'var(--danger, #dc2626)',
            marginBottom: 14,
          }}
        >
          {errorMsg}
        </div>
      )}

      {mfa ? (
        <>
          <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 6 }}>二步验证</div>
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 12 }}>
            {useRecovery
              ? '请输入一个未使用过的恢复码完成验证'
              : mfa.method === 'TOTP'
                ? '请输入身份验证器显示的 6 位动态码'
                : `验证码已发送至 ${mfa.masked ?? '账号绑定邮箱'}，请在 5 分钟内输入`}
          </div>
          <div style={{ borderRadius: 12, overflow: 'hidden', marginBottom: 12 }}>
            <Input
              value={mfaCode}
              onChange={(v) =>
                setMfaCode(
                  useRecovery
                    ? v.replace(/[^0-9a-zA-Z-]/g, '').slice(0, 24)
                    : v.replace(/\D/g, '').slice(0, 6),
                )
              }
              placeholder={useRecovery ? '请输入恢复码' : '请输入 6 位验证码'}
              type={useRecovery ? 'text' : 'tel'}
              style={{
                '--text-align': 'center',
                letterSpacing: '0.2em',
                padding: 12,
                background: 'var(--bg-elev)',
              }}
            />
          </div>
          <Button
            block
            color="primary"
            size="large"
            loading={submitting}
            disabled={useRecovery ? mfaCode.trim().length < 8 : mfaCode.trim().length < 6}
            onClick={() => void handleMfaVerify()}
          >
            验证并登录
          </Button>
          <div
            style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginTop: 16 }}
          >
            <span
              style={{ color: 'var(--brand)' }}
              onClick={() => {
                setUseRecovery((v) => !v);
                setMfaCode('');
                setErrorMsg(null);
              }}
            >
              {useRecovery ? '返回验证码输入' : '使用备用码'}
            </span>
            <span
              style={{ color: 'var(--text-tertiary)' }}
              onClick={() => {
                setMfa(null);
                setMfaCode('');
                setUseRecovery(false);
                setErrorMsg(null);
              }}
            >
              返回重新登录
            </span>
          </div>
        </>
      ) : (
        <>
          <div style={{ borderRadius: 12, overflow: 'hidden' }}>
            <Form
              layout="horizontal"
              onFinish={(values: FormValues) => void handleFinish(values)}
              footer={
                <Button block type="submit" color="primary" size="large" loading={submitting}>
                  登录
                </Button>
              }
            >
              <Form.Item
                name="username"
                rules={[{ required: true, message: '请输入账号 / 手机号' }]}
              >
                <Input placeholder="账号 / 手机号" clearable />
              </Form.Item>
              <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
                <Input placeholder="密码" type="password" clearable />
              </Form.Item>
            </Form>
          </div>

          <div
            style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginTop: 16 }}
          >
            <span
              style={{ color: 'var(--text-tertiary)' }}
              onClick={() => Toast.show({ content: '暂未开放(三方登录&短信，暂无认证资源)' })}
            >
              手机号验证码登录
            </span>
            <span
              style={{ color: 'var(--text-tertiary)' }}
              onClick={() => Toast.show({ content: '请联系管理员重置密码' })}
            >
              忘记密码？
            </span>
          </div>
        </>
      )}

      <div style={{ flex: 1, minHeight: 24 }} />

      {/* 第三方一键登录（环境识别置顶推荐 + 其他渠道） */}
      {!mfa && <OAuthBlock env={env} busy={oauthBusy} onLogin={(p) => void handleOAuth(p)} />}

      {bindPrompt && (
        <OAuthBindModal
          prompt={bindPrompt}
          onCancel={() => setBindPrompt(null)}
          onSubmit={handleBind}
        />
      )}
    </div>
  );
}
