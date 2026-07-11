import { useEffect, useRef, useState } from 'react';
import { App, QRCode } from 'antd';
import {
  CheckCircleFilled,
  ExclamationCircleFilled,
  EyeInvisibleOutlined,
  EyeOutlined,
  KeyOutlined,
  LockOutlined,
  SafetyOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router';
import { ApiError, type LoginMfaMethod, type LoginMfaSetupChallenge } from '@wen-saas/api-client';
import { authService } from '../../lib/http';

type Step = 'login' | 'mfa' | 'success';

const D = {
  bg: '#0E1320',
  card: '#15192A',
  border: '#232A3D',
  inputBorder: '#2C3245',
  text: '#E5E7EE',
  textSub: '#C8CCD7',
  textTert: '#7A8194',
  textDiv: '#3A4156',
  link: 'oklch(0.75 0.14 250)',
};

// 平台支持邮箱：部署方构建期经 VITE_OPS_SUPPORT_EMAIL 注入自己的值
const SUPPORT_EMAIL =
  (import.meta.env.VITE_OPS_SUPPORT_EMAIL as string | undefined) ?? 'support@example.com';

type ServiceHealth = 'checking' | 'up' | 'down';

export function OpsLoginPage() {
  const { modal } = App.useApp();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [step, setStep] = useState<Step>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPwd, setShowPwd] = useState(false);
  const [mfaCode, setMfaCode] = useState<string[]>(Array(6).fill(''));
  const [loading, setLoading] = useState(false);
  const [errorBanner, setErrorBanner] = useState<string | null>(null);
  const [locked, setLocked] = useState(false);
  const [remember, setRemember] = useState(true);
  // 二步验证流程态（凭 login 返回的 preAuthToken 走 setup/challenge/verify）。
  const [preAuthToken, setPreAuthToken] = useState<string | null>(null);
  const [mfaEnabled, setMfaEnabled] = useState(true);
  const [mfaMethod, setMfaMethod] = useState<LoginMfaMethod>('TOTP');
  const [setupChallenge, setSetupChallenge] = useState<LoginMfaSetupChallenge | null>(null);
  const [challengeMasked, setChallengeMasked] = useState<string | null>(null);
  const [mfaError, setMfaError] = useState<string | null>(null);
  const [mfaLoading, setMfaLoading] = useState(false);
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  // 备用码兜底：丢失验证通道时改输恢复码（仅已开启账号可用）
  const [useRecovery, setUseRecovery] = useState(false);
  const [recoveryInput, setRecoveryInput] = useState('');
  const mfaPreppedRef = useRef(false);
  // 后端健康探测（/actuator/health 免鉴权；生产网关需将该路径代理到后端）
  const [health, setHealth] = useState<ServiceHealth>('checking');

  useEffect(() => {
    const ctrl = new AbortController();
    fetch('/actuator/health', { signal: ctrl.signal })
      .then((r) => r.json() as Promise<{ status?: string }>)
      .then((body) => setHealth(body?.status === 'UP' ? 'up' : 'down'))
      .catch(() => {
        if (!ctrl.signal.aborted) setHealth('down');
      });
    return () => ctrl.abort();
  }, []);

  const redirectTarget = (() => {
    const from = params.get('from');
    return from && from.startsWith('/') && !from.endsWith('/login') ? from : '/workbench';
  })();

  // 进入成功页后自动跳转；首次配置返回恢复码时停留，待用户保存后手动进入。
  useEffect(() => {
    if (step !== 'success' || recoveryCodes.length > 0) return;
    const t = setTimeout(() => {
      void navigate(redirectTarget, { replace: true });
    }, 1200);
    return () => clearTimeout(t);
  }, [step, navigate, redirectTarget, recoveryCodes.length]);

  const resetMfaFlow = () => {
    setMfaCode(Array(6).fill(''));
    setPreAuthToken(null);
    setSetupChallenge(null);
    setChallengeMasked(null);
    setMfaError(null);
    setRecoveryCodes([]);
    setUseRecovery(false);
    setRecoveryInput('');
    mfaPreppedRef.current = false;
  };

  // 进入 MFA 步骤时备好通道：已开启且推导为 EMAIL → 发码；未开启（强制模式）→ 等用户选定通道后 setup。
  useEffect(() => {
    if (step !== 'mfa' || !preAuthToken || mfaPreppedRef.current) return;
    mfaPreppedRef.current = true;
    if (mfaEnabled && (mfaMethod === 'SMS' || mfaMethod === 'EMAIL')) {
      void (async () => {
        try {
          const ch = await authService.mfaChallenge({ preAuthToken });
          setChallengeMasked(ch.maskedTarget ?? null);
        } catch {
          setMfaError('发送验证码失败，请返回重试');
        }
      })();
    }
  }, [step, preAuthToken, mfaEnabled, mfaMethod]);

  // 首次配置：用户选定通道后发起 setup（TOTP 出二维码；EMAIL 发码）
  const chooseSetupMethod = async (m: LoginMfaMethod) => {
    if (mfaLoading || !preAuthToken) return;
    setMfaLoading(true);
    setMfaError(null);
    try {
      const ch = await authService.mfaSetup({ preAuthToken, method: m });
      setSetupChallenge(ch);
      setMfaMethod(m);
      setMfaCode(Array(6).fill(''));
    } catch (err) {
      setMfaError(
        err instanceof ApiError
          ? (err.problem.detail ?? '发起二步验证配置失败，请重试')
          : '网络错误，请稍后重试',
      );
    } finally {
      setMfaLoading(false);
    }
  };

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (loading || locked) return;
    if (!email.trim() || !password) {
      setErrorBanner(
        !email.trim() && !password
          ? '请输入企业邮箱和密码'
          : !email.trim()
            ? '请输入企业邮箱'
            : '请输入密码',
      );
      return;
    }
    setLoading(true);
    setErrorBanner(null);
    try {
      const res = await authService.login({
        username: email.trim(),
        password,
        tenantCode: '',
        rememberMe: remember,
      });
      // 未开启二步验证（且平台未强制）时直登返回 token；否则 mfaPending 进二步验证。
      if (res.token && res.user) {
        setStep('success');
        return;
      }
      setPreAuthToken(res.preAuthToken ?? null);
      setMfaEnabled(res.mfaEnabled ?? false);
      setMfaMethod((res.mfaMethod as LoginMfaMethod) || 'TOTP');
      mfaPreppedRef.current = false;
      setStep('mfa');
    } catch (err) {
      if (err instanceof ApiError) {
        const code = typeof err.problem.code === 'string' ? err.problem.code : '';
        const attemptsLeft =
          typeof (err.problem as Record<string, unknown>).attemptsLeft === 'number'
            ? ((err.problem as Record<string, unknown>).attemptsLeft as number)
            : undefined;
        if (code === 'LOGIN_LOCKED') {
          setLocked(true);
          setErrorBanner('账号已被锁定，请约 10 分钟后重试');
        } else {
          setErrorBanner(
            attemptsLeft != null
              ? `账号或密码错误（剩余 ${attemptsLeft} 次 · 5 次失败将锁定 10 分钟）`
              : '账号或密码错误',
          );
        }
      } else {
        setErrorBanner('网络错误，请稍后重试');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleMfaSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!mfaReady || !preAuthToken || mfaLoading) return;
    setMfaLoading(true);
    setMfaError(null);
    try {
      const res = await authService.mfaVerify({
        preAuthToken,
        code: useRecovery ? recoveryInput.trim() : mfaCode.join(''),
        rememberMe: remember,
      });
      if (res.recoveryCodes && res.recoveryCodes.length > 0) {
        setRecoveryCodes(res.recoveryCodes);
      }
      setStep('success');
    } catch (err) {
      setMfaError(
        err instanceof ApiError
          ? useRecovery
            ? '恢复码错误或已被使用，请换一个重试'
            : '验证码错误或已过期，请重新输入'
          : '网络错误，请稍后重试',
      );
      if (useRecovery) {
        setRecoveryInput('');
      } else {
        setMfaCode(Array(6).fill(''));
        (document.getElementById('ops-mfa-0') as HTMLInputElement | null)?.focus();
      }
    } finally {
      setMfaLoading(false);
    }
  };

  const setMfaDigit = (idx: number, v: string) => {
    const digit = v.replace(/\D/g, '').slice(-1);
    const next = [...mfaCode];
    next[idx] = digit;
    setMfaCode(next);
    if (digit && idx < 5) {
      (document.getElementById(`ops-mfa-${idx + 1}`) as HTMLInputElement | null)?.focus();
    }
  };

  const handleMfaKeyDown = (idx: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !mfaCode[idx] && idx > 0) {
      (document.getElementById(`ops-mfa-${idx - 1}`) as HTMLInputElement | null)?.focus();
    }
  };

  const mfaFull = mfaCode.join('').length === 6;
  // 备用码为 16 位分组字符串（如 xxxx-xxxx-xxxx-xxxx），长度校验放宽到 ≥8
  const mfaReady = useRecovery ? recoveryInput.trim().length >= 8 : mfaFull;
  // 首次配置且尚未选定通道：先出通道选择
  const showMfaChooser = step === 'mfa' && !mfaEnabled && !setupChallenge;

  return (
    <>
      <style>{`
        .ops-inp::placeholder { color: ${D.inputBorder}; }
        .ops-mfa-inp:focus { border-color: var(--brand) !important; }
      `}</style>

      <div
        style={{
          minHeight: '100vh',
          background: D.bg,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 24,
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        {/* Background grid */}
        <svg
          aria-hidden
          style={{
            position: 'absolute',
            inset: 0,
            width: '100%',
            height: '100%',
            opacity: 0.05,
            pointerEvents: 'none',
          }}
          preserveAspectRatio="none"
        >
          <defs>
            <pattern id="ops-grid" width="40" height="40" patternUnits="userSpaceOnUse">
              <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#fff" strokeWidth="0.5" />
            </pattern>
          </defs>
          <rect width="100%" height="100%" fill="url(#ops-grid)" />
        </svg>

        {/* Blue radial glow */}
        <div
          aria-hidden
          style={{
            position: 'absolute',
            top: '-10%',
            left: '50%',
            transform: 'translateX(-50%)',
            width: 720,
            height: 360,
            background: 'radial-gradient(ellipse, rgba(43,95,255,0.18) 0%, transparent 65%)',
            pointerEvents: 'none',
          }}
        />

        {/* Card */}
        <div
          style={{
            position: 'relative',
            zIndex: 1,
            width: 420,
            padding: 40,
            background: D.card,
            border: `1px solid ${D.border}`,
            borderRadius: 'var(--r-lg)',
            boxShadow: '0 20px 50px rgba(0,0,0,0.4)',
            color: D.text,
          }}
        >
          {/* Brand header */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 28 }}>
            <div
              style={{
                width: 40,
                height: 40,
                borderRadius: 10,
                background: 'var(--brand)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
                boxShadow: '0 6px 16px rgba(43,95,255,0.4)',
              }}
            >
              <svg width="22" height="22" viewBox="0 0 26 26" aria-hidden>
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
            <div>
              <div style={{ fontSize: 14, fontWeight: 700 }}>Wen SaaS Boot · 运营控制台</div>
              <div
                style={{
                  fontSize: 11,
                  color: D.textTert,
                  marginTop: 2,
                  fontFamily: 'var(--font-mono)',
                }}
              >
                {window.location.host}
              </div>
            </div>
          </div>

          {/* Security banner */}
          <div
            style={{
              marginBottom: 24,
              padding: '10px 12px',
              display: 'flex',
              alignItems: 'flex-start',
              gap: 8,
              background: 'rgba(43,95,255,0.10)',
              border: '1px solid rgba(43,95,255,0.30)',
              borderRadius: 'var(--r-sm)',
              fontSize: 11,
              color: D.textSub,
              lineHeight: 1.6,
            }}
          >
            <SafetyOutlined style={{ color: D.link, fontSize: 14, marginTop: 2, flexShrink: 0 }} />
            <span>
              您正在访问 <b>内部运营系统</b>
              。会话与操作均会记录于运营审计日志（永久保留）。仅限授权员工访问。
            </span>
          </div>

          {/* ——— Login step ——— */}
          {step === 'login' && (
            <form
              onSubmit={(e) => {
                void handleLoginSubmit(e);
              }}
            >
              <div
                style={{ fontSize: 22, fontWeight: 700, marginBottom: 4, letterSpacing: '-0.01em' }}
              >
                登录
              </div>
              <div style={{ fontSize: 12, color: D.textTert, marginBottom: 20 }}>
                请使用企业邮箱与单点密码
              </div>

              {errorBanner && (
                <div
                  style={{
                    marginBottom: 14,
                    padding: '10px 12px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    background: 'rgba(220,38,38,0.10)',
                    border: '1px solid rgba(220,38,38,0.30)',
                    borderRadius: 'var(--r-sm)',
                    color: '#FCA5A5',
                    fontSize: 12,
                  }}
                >
                  <ExclamationCircleFilled
                    style={{ color: '#EF4444', fontSize: 14, flexShrink: 0 }}
                  />
                  <span>{errorBanner}</span>
                </div>
              )}

              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {/* Email */}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    height: 42,
                    padding: '0 12px',
                    background: D.bg,
                    border: `1px solid ${errorBanner ? '#DC2626' : D.inputBorder}`,
                    borderRadius: 'var(--r-md)',
                  }}
                >
                  <UserOutlined style={{ color: D.textTert, fontSize: 14, flexShrink: 0 }} />
                  <input
                    className="ops-inp"
                    type="text"
                    placeholder="企业邮箱"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    autoFocus
                    autoComplete="username"
                    disabled={locked}
                    style={{
                      flex: 1,
                      border: 'none',
                      background: 'transparent',
                      fontSize: 13,
                      fontFamily: 'inherit',
                      color: '#fff',
                      minWidth: 0,
                    }}
                  />
                </div>

                {/* Password */}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    height: 42,
                    padding: '0 12px',
                    background: D.bg,
                    border: `1px solid ${errorBanner ? '#DC2626' : D.inputBorder}`,
                    borderRadius: 'var(--r-md)',
                  }}
                >
                  <LockOutlined style={{ color: D.textTert, fontSize: 14, flexShrink: 0 }} />
                  <input
                    className="ops-inp"
                    type={showPwd ? 'text' : 'password'}
                    placeholder="密码"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete="current-password"
                    disabled={locked}
                    style={{
                      flex: 1,
                      border: 'none',
                      background: 'transparent',
                      fontSize: 13,
                      fontFamily: 'inherit',
                      color: '#fff',
                      minWidth: 0,
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPwd((v) => !v)}
                    style={{
                      background: 'transparent',
                      border: 'none',
                      cursor: 'pointer',
                      color: D.textTert,
                      padding: 0,
                      display: 'flex',
                      alignItems: 'center',
                    }}
                  >
                    {showPwd ? (
                      <EyeInvisibleOutlined style={{ fontSize: 14 }} />
                    ) : (
                      <EyeOutlined style={{ fontSize: 14 }} />
                    )}
                  </button>
                </div>

                {/* Remember + forgot */}
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    fontSize: 12,
                    marginTop: 2,
                  }}
                >
                  <label
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                      color: D.textSub,
                      cursor: 'pointer',
                    }}
                  >
                    <input
                      type="checkbox"
                      checked={remember}
                      onChange={(e) => setRemember(e.target.checked)}
                      style={{ accentColor: 'var(--brand)' }}
                    />
                    <span>记住当前设备 30 天</span>
                  </label>
                  <button
                    type="button"
                    onClick={() => {
                      modal.info({
                        title: '忘记密码',
                        content: `运营后台账号由平台 IT 统一管理，暂不支持自助找回。如需重置密码，请发送邮件至 ${SUPPORT_EMAIL} 协助处理。`,
                        okText: '我知道了',
                      });
                    }}
                    style={{
                      background: 'transparent',
                      border: 'none',
                      cursor: 'pointer',
                      color: D.link,
                      fontSize: 12,
                      fontFamily: 'inherit',
                      padding: 0,
                    }}
                  >
                    忘记密码？
                  </button>
                </div>

                {/* Submit */}
                <div style={{ marginTop: 14 }}>
                  <button
                    type="submit"
                    disabled={loading || locked || !email.trim() || !password}
                    style={{
                      width: '100%',
                      height: 42,
                      background:
                        loading || locked || !email.trim() || !password
                          ? 'rgba(43,95,255,0.4)'
                          : 'var(--brand)',
                      border: 'none',
                      borderRadius: 'var(--r-md)',
                      color: '#fff',
                      fontSize: 14,
                      fontWeight: 600,
                      cursor:
                        loading || locked || !email.trim() || !password ? 'not-allowed' : 'pointer',
                      fontFamily: 'inherit',
                      transition: 'background 0.15s',
                    }}
                  >
                    {loading ? '登录中…' : '下一步：二步验证'}
                  </button>
                </div>
              </div>

              {/* SSO */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  margin: '28px 0 16px',
                  color: D.textTert,
                }}
              >
                <div style={{ flex: 1, height: 1, background: D.border }} />
                <span style={{ fontSize: 11 }}>或使用企业 SSO</span>
                <div style={{ flex: 1, height: 1, background: D.border }} />
              </div>
              <button
                type="button"
                onClick={() => {
                  modal.info({
                    title: '暂未开放',
                    content: '暂未开放(三方登录&短信，暂无认证资源)',
                  });
                }}
                style={{
                  width: '100%',
                  height: 40,
                  background: 'transparent',
                  border: `1px solid ${D.inputBorder}`,
                  color: D.textSub,
                  borderRadius: 'var(--r-md)',
                  cursor: 'pointer',
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 8,
                  fontSize: 13,
                  fontFamily: 'inherit',
                }}
              >
                <KeyOutlined style={{ fontSize: 14 }} />
                企业 SSO 单点登录（占位）
              </button>
            </form>
          )}

          {/* ——— MFA step ——— */}
          {step === 'mfa' && (
            <form
              onSubmit={(e) => {
                void handleMfaSubmit(e);
              }}
            >
              <div
                style={{ fontSize: 22, fontWeight: 700, marginBottom: 4, letterSpacing: '-0.01em' }}
              >
                二步验证
              </div>
              <div style={{ fontSize: 12, color: D.textTert, marginBottom: 20 }}>
                {showMfaChooser
                  ? '首次登录需开启二步验证，请选择一种验证方式'
                  : !mfaEnabled
                    ? mfaMethod === 'TOTP'
                      ? '用身份验证器扫码或手动添加密钥后，输入 6 位动态码'
                      : `6 位验证码已发送至 ${setupChallenge?.maskedTarget ?? '账号绑定邮箱'}，请在 5 分钟内输入`
                    : useRecovery
                      ? '请输入一个未使用过的恢复码完成验证'
                      : mfaMethod === 'TOTP'
                        ? '请在身份验证器 (Authenticator) 中查看 6 位动态码'
                        : `6 位验证码已发送至 ${challengeMasked ?? '账号绑定通道'}，请在 5 分钟内输入`}
              </div>

              {/* 首次强制配置：展示 otpauth 二维码 + 密钥 */}
              {!mfaEnabled && setupChallenge?.totpSecret && (
                <div
                  style={{
                    display: 'flex',
                    gap: 14,
                    alignItems: 'center',
                    marginBottom: 16,
                    padding: 14,
                    background: D.bg,
                    border: `1px solid ${D.inputBorder}`,
                    borderRadius: 'var(--r-md)',
                  }}
                >
                  {setupChallenge.otpauthUri && (
                    <div style={{ background: '#fff', padding: 6, borderRadius: 6, flexShrink: 0 }}>
                      <QRCode value={setupChallenge.otpauthUri} size={96} bordered={false} />
                    </div>
                  )}
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontSize: 11, color: D.textTert, marginBottom: 4 }}>
                      手动添加密钥：
                    </div>
                    <code
                      style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: 12,
                        color: D.textSub,
                        wordBreak: 'break-all',
                        letterSpacing: '0.04em',
                      }}
                    >
                      {setupChallenge.totpSecret}
                    </code>
                  </div>
                </div>
              )}

              {mfaError && (
                <div
                  style={{
                    marginBottom: 14,
                    padding: '10px 12px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    background: 'rgba(220,38,38,0.10)',
                    border: '1px solid rgba(220,38,38,0.30)',
                    borderRadius: 'var(--r-sm)',
                    color: '#FCA5A5',
                    fontSize: 12,
                  }}
                >
                  <ExclamationCircleFilled
                    style={{ color: '#EF4444', fontSize: 14, flexShrink: 0 }}
                  />
                  <span>{mfaError}</span>
                </div>
              )}

              {/* 首次配置 · 通道选择（短信暂未开放置灰） */}
              {showMfaChooser && (
                <div
                  style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 20 }}
                >
                  {(
                    [
                      {
                        v: 'TOTP',
                        t: '身份验证器 (TOTP)',
                        s: '推荐 · 用 Microsoft Authenticator 扫码绑定',
                        disabled: false,
                      },
                      { v: 'EMAIL', t: '邮箱验证码', s: '发送到账号绑定邮箱', disabled: false },
                      {
                        v: 'SMS',
                        t: '短信验证码',
                        s: '暂未开放(短信，暂无认证资源)',
                        disabled: true,
                      },
                    ] as { v: LoginMfaMethod; t: string; s: string; disabled: boolean }[]
                  ).map((c) => (
                    <button
                      key={c.v}
                      type="button"
                      disabled={c.disabled || mfaLoading}
                      onClick={() => void chooseSetupMethod(c.v)}
                      style={{
                        textAlign: 'left',
                        padding: '12px 14px',
                        background: D.bg,
                        border: `1px solid ${D.inputBorder}`,
                        borderRadius: 'var(--r-md)',
                        color: D.text,
                        cursor: c.disabled ? 'not-allowed' : 'pointer',
                        opacity: c.disabled ? 0.5 : 1,
                        fontFamily: 'inherit',
                      }}
                    >
                      <div style={{ fontSize: 14, fontWeight: 600 }}>{c.t}</div>
                      <div style={{ fontSize: 12, color: D.textTert, marginTop: 2 }}>{c.s}</div>
                    </button>
                  ))}
                </div>
              )}

              {!showMfaChooser && useRecovery && (
                <input
                  className="ops-mfa-inp"
                  value={recoveryInput}
                  onChange={(e) =>
                    setRecoveryInput(e.target.value.replace(/[^0-9a-zA-Z-]/g, '').slice(0, 24))
                  }
                  placeholder="请输入恢复码（如 xxxx-xxxx-xxxx-xxxx）"
                  autoFocus
                  style={{
                    width: '100%',
                    height: 48,
                    marginBottom: 16,
                    padding: '0 14px',
                    fontSize: 15,
                    background: D.bg,
                    border: `1px solid ${recoveryInput ? 'var(--brand)' : D.inputBorder}`,
                    borderRadius: 'var(--r-md)',
                    color: '#fff',
                    fontFamily: 'var(--font-mono)',
                    letterSpacing: '0.06em',
                  }}
                />
              )}

              {!showMfaChooser && !useRecovery && (
                <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
                  {mfaCode.map((digit, i) => (
                    <input
                      key={i}
                      id={`ops-mfa-${i}`}
                      className="ops-mfa-inp"
                      value={digit}
                      onChange={(e) => setMfaDigit(i, e.target.value)}
                      onKeyDown={(e) => handleMfaKeyDown(i, e)}
                      inputMode="numeric"
                      maxLength={1}
                      autoFocus={i === 0}
                      style={{
                        width: 48,
                        height: 56,
                        textAlign: 'center',
                        fontSize: 22,
                        fontWeight: 600,
                        background: D.bg,
                        border: `1px solid ${digit ? 'var(--brand)' : D.inputBorder}`,
                        borderRadius: 'var(--r-md)',
                        color: '#fff',
                        fontFamily: 'var(--font-mono)',
                      }}
                    />
                  ))}
                </div>
              )}

              {!showMfaChooser && (
                <div
                  style={{
                    fontSize: 12,
                    color: D.textTert,
                    display: 'flex',
                    justifyContent: 'space-between',
                    marginBottom: 20,
                  }}
                >
                  <span>
                    {mfaEnabled ? (
                      <button
                        type="button"
                        onClick={() => {
                          setUseRecovery((v) => !v);
                          setMfaError(null);
                          setMfaCode(Array(6).fill(''));
                          setRecoveryInput('');
                        }}
                        style={{
                          background: 'transparent',
                          border: 'none',
                          cursor: 'pointer',
                          color: D.link,
                          fontSize: 12,
                          fontFamily: 'inherit',
                          padding: 0,
                        }}
                      >
                        {useRecovery ? '返回验证码输入' : '通道不可用？使用备用码'}
                      </button>
                    ) : (
                      <button
                        type="button"
                        onClick={() => {
                          setSetupChallenge(null);
                          setMfaError(null);
                          setMfaCode(Array(6).fill(''));
                        }}
                        style={{
                          background: 'transparent',
                          border: 'none',
                          cursor: 'pointer',
                          color: D.link,
                          fontSize: 12,
                          fontFamily: 'inherit',
                          padding: 0,
                        }}
                      >
                        换一种验证方式
                      </button>
                    )}
                  </span>
                  <span>
                    账号 <b style={{ color: D.textSub }}>{email}</b>
                  </span>
                </div>
              )}

              {!showMfaChooser && (
                <button
                  type="submit"
                  disabled={!mfaReady || mfaLoading}
                  style={{
                    width: '100%',
                    height: 42,
                    background: mfaReady && !mfaLoading ? 'var(--brand)' : 'rgba(43,95,255,0.4)',
                    border: 'none',
                    borderRadius: 'var(--r-md)',
                    color: '#fff',
                    fontSize: 14,
                    fontWeight: 600,
                    cursor: mfaReady && !mfaLoading ? 'pointer' : 'not-allowed',
                    fontFamily: 'inherit',
                  }}
                >
                  {mfaLoading ? '验证中…' : '验证并进入运营控制台'}
                </button>
              )}
              <button
                type="button"
                onClick={() => {
                  setStep('login');
                  resetMfaFlow();
                }}
                style={{
                  marginTop: 12,
                  width: '100%',
                  height: 36,
                  background: 'transparent',
                  border: 'none',
                  color: D.textTert,
                  fontSize: 12,
                  cursor: 'pointer',
                  fontFamily: 'inherit',
                }}
              >
                ← 返回上一步
              </button>
            </form>
          )}

          {/* ——— Success step ——— */}
          {step === 'success' && (
            <div style={{ textAlign: 'center', padding: '20px 0' }}>
              <div
                style={{
                  width: 64,
                  height: 64,
                  borderRadius: 32,
                  background: 'rgba(22,163,74,0.16)',
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <CheckCircleFilled style={{ color: '#4ADE80', fontSize: 32 }} />
              </div>
              <div style={{ fontSize: 18, fontWeight: 700, marginTop: 16 }}>
                {recoveryCodes.length > 0 ? '二步验证已开启' : '验证通过'}
              </div>
              <div style={{ fontSize: 12, color: D.textTert, marginTop: 6 }}>
                {recoveryCodes.length > 0
                  ? '请妥善保存以下恢复码，仅此一次完整显示'
                  : '会话已创建 · 正在进入运营控制台'}
              </div>

              {recoveryCodes.length > 0 && (
                <>
                  <div
                    style={{
                      marginTop: 16,
                      padding: 14,
                      background: D.bg,
                      border: `1px solid ${D.inputBorder}`,
                      borderRadius: 'var(--r-md)',
                      display: 'grid',
                      gridTemplateColumns: 'repeat(2, 1fr)',
                      gap: 8,
                      fontFamily: 'var(--font-mono)',
                      fontSize: 13,
                      letterSpacing: '0.05em',
                      textAlign: 'center',
                    }}
                  >
                    {recoveryCodes.map((c) => (
                      <div
                        key={c}
                        style={{
                          padding: '6px 8px',
                          background: D.card,
                          border: `1px solid ${D.border}`,
                          borderRadius: 6,
                          color: D.textSub,
                        }}
                      >
                        {c}
                      </div>
                    ))}
                  </div>
                  <button
                    type="button"
                    onClick={() => void navigate(redirectTarget, { replace: true })}
                    style={{
                      marginTop: 16,
                      width: '100%',
                      height: 42,
                      background: 'var(--brand)',
                      border: 'none',
                      borderRadius: 'var(--r-md)',
                      color: '#fff',
                      fontSize: 14,
                      fontWeight: 600,
                      cursor: 'pointer',
                      fontFamily: 'inherit',
                    }}
                  >
                    我已妥善保存，进入运营控制台
                  </button>
                </>
              )}
            </div>
          )}

          {/* Card footer */}
          <div
            style={{
              marginTop: 32,
              paddingTop: 16,
              borderTop: `1px solid ${D.border}`,
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              fontSize: 11,
              color: D.textTert,
              flexWrap: 'wrap',
            }}
          >
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              <span
                style={{
                  width: 6,
                  height: 6,
                  borderRadius: 3,
                  background:
                    health === 'up' ? '#4ADE80' : health === 'down' ? '#F87171' : D.textTert,
                  display: 'inline-block',
                }}
              />
              {health === 'up' ? '服务正常' : health === 'down' ? '服务异常' : '状态检测中'}
            </span>
            <span style={{ color: D.textDiv }}>·</span>
            <span style={{ fontFamily: 'var(--font-mono)' }}>
              v{__APP_VERSION__} · {__BUILD_DATE__}
            </span>
            <span style={{ color: D.textDiv }}>·</span>
            <span>建议浏览器 Chrome / Edge 100+</span>
            <span style={{ marginLeft: 'auto', color: D.textTert }}>
              需帮助？
              <span
                style={{ color: D.link, cursor: 'pointer' }}
                onClick={() => {
                  modal.info({
                    title: '联系平台 IT',
                    content: `请发送邮件至 ${SUPPORT_EMAIL}，说明账号与问题现象。`,
                    okText: '我知道了',
                  });
                }}
              >
                联系平台 IT
              </span>
            </span>
          </div>
        </div>

        {/* Bottom-left copyright */}
        <div
          style={{
            position: 'absolute',
            bottom: 16,
            left: 24,
            fontSize: 11,
            color: D.textDiv,
            fontFamily: 'var(--font-mono)',
            zIndex: 1,
          }}
        >
          © 2026 Wen SaaS Boot · Internal Console · 仅限授权访问
        </div>
      </div>
    </>
  );
}
