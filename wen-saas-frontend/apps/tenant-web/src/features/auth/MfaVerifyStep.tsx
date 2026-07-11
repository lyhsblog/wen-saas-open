import { useState } from 'react';
import { Alert, Button, Input } from 'antd';
import { ApiError } from '@wen-saas/api-client';
import { authService } from '../../lib/http';

export type MfaChallengeState = {
  preAuthToken: string;
  /** 推导的登录验证通道：TOTP / EMAIL。 */
  method: string | null;
  /** EMAIL 通道的发码掩码目标。 */
  masked: string | null;
};

/**
 * 登录二步验证步：输入动态码/邮箱验证码完成登录，支持恢复码兜底。
 * 会话 token 由 authService.mfaVerify 写入，成功后调用方直接收尾跳转。
 */
export function MfaVerifyStep({
  mfa,
  rememberMe,
  onSuccess,
  onBack,
}: {
  mfa: MfaChallengeState;
  rememberMe: boolean;
  onSuccess: () => void;
  onBack: () => void;
}) {
  const [code, setCode] = useState('');
  const [useRecovery, setUseRecovery] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [masked, setMasked] = useState(mfa.masked);
  const [resending, setResending] = useState(false);

  const isTotp = mfa.method === 'TOTP';
  const ready = useRecovery ? code.trim().length >= 8 : code.trim().length === 6;

  const resend = async () => {
    if (resending) return;
    setResending(true);
    try {
      const ch = await authService.mfaChallenge({ preAuthToken: mfa.preAuthToken });
      setMasked(ch.maskedTarget ?? null);
      setError(null);
    } catch {
      setError('发送验证码失败，请稍后重试');
    } finally {
      setResending(false);
    }
  };

  const submit = async () => {
    if (!ready || loading) return;
    setLoading(true);
    setError(null);
    try {
      await authService.mfaVerify({
        preAuthToken: mfa.preAuthToken,
        code: code.trim(),
        rememberMe,
      });
      onSuccess();
    } catch (err) {
      setError(
        err instanceof ApiError
          ? useRecovery
            ? '恢复码错误或已被使用，请换一个重试'
            : '验证码错误或已过期，请重新输入'
          : '网络错误，请稍后重试',
      );
      setCode('');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--text)', marginBottom: 6 }}>
        二步验证
      </div>
      <div style={{ fontSize: 13, color: 'var(--text-tertiary)', marginBottom: 16 }}>
        {useRecovery
          ? '请输入一个未使用过的恢复码完成验证'
          : isTotp
            ? '请输入身份验证器 (Authenticator) 显示的 6 位动态码'
            : `6 位验证码已发送至 ${masked ?? '账号绑定邮箱'}，请在 5 分钟内输入`}
        {!useRecovery && !isTotp && (
          <a
            style={{ fontSize: 12, color: 'var(--brand)', cursor: 'pointer', marginLeft: 8 }}
            onClick={() => void resend()}
          >
            {resending ? '发送中…' : '重新发送'}
          </a>
        )}
      </div>

      {error && <Alert type="warning" showIcon style={{ marginBottom: 16 }} message={error} />}

      <Input
        value={code}
        onChange={(e) =>
          setCode(
            useRecovery
              ? e.target.value.replace(/[^0-9a-zA-Z-]/g, '').slice(0, 24)
              : e.target.value.replace(/\D/g, '').slice(0, 6),
          )
        }
        placeholder={useRecovery ? '请输入恢复码（如 xxxx-xxxx-xxxx-xxxx）' : '请输入 6 位验证码'}
        size="large"
        style={{ fontFamily: 'var(--font-mono)', letterSpacing: '0.2em', textAlign: 'center' }}
        maxLength={useRecovery ? 24 : 6}
        autoFocus
        onPressEnter={() => void submit()}
      />

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          fontSize: 12,
          margin: '12px 0 16px',
        }}
      >
        <a
          style={{ color: 'var(--brand)', cursor: 'pointer' }}
          onClick={() => {
            setUseRecovery((v) => !v);
            setCode('');
            setError(null);
          }}
        >
          {useRecovery ? '返回验证码输入' : '通道不可用？使用备用码'}
        </a>
        <a style={{ color: 'var(--text-tertiary)', cursor: 'pointer' }} onClick={onBack}>
          ← 返回重新登录
        </a>
      </div>

      <Button
        type="primary"
        block
        size="large"
        style={{ height: 44, fontWeight: 600 }}
        disabled={!ready}
        loading={loading}
        onClick={() => void submit()}
      >
        验证并登录
      </Button>
    </div>
  );
}
