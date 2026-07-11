import { useEffect, useRef, useState } from 'react';
import { Button, Result, Spin } from 'antd';
import { useNavigate, useSearchParams } from 'react-router';
import { ApiError, authApi } from '@wen-saas/api-client';
import { useAuthStore } from '@wen-saas/auth';
import { http } from '../../lib/http';

const centerStyle: React.CSSProperties = {
  minHeight: '100vh',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
};

/**
 * 运营代登录落地页（OPS-T-018）。
 * ops-web 调 `POST /ops/tenants/{id}/impersonate` 拿到一次性 nonce 后，
 * 通过 `window.open` 打开目标租户子域本页 `/impersonate-redeem?nonce=...`。
 * 本页消费 nonce 换取目标租户登录态，写入 authStore 后跳工作台。
 */
export function ImpersonateRedeemPage() {
  const [params] = useSearchParams();
  const nonce = params.get('nonce') ?? '';
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  // StrictMode 下 effect 会跑两次，nonce 一次性消费，需防重复兑换。
  const startedRef = useRef(false);

  useEffect(() => {
    // 缺 nonce 是可在渲染期派生的状态，不在 effect 里同步 setState（见 react-hooks/set-state-in-effect）。
    if (!nonce) return;
    if (startedRef.current) return;
    startedRef.current = true;

    const run = async () => {
      try {
        const res = await authApi(http).redeemImpersonate(nonce);
        if (!res.token) {
          setError('代登录失败：未获取到登录凭证。');
          return;
        }
        // 兜底：redeem 响应理论上带 user，缺失时再拉一次 /me。
        const user = res.user ?? (await authApi(http).me());
        useAuthStore.getState().setSession(res.token, user);
        void navigate('/workbench', { replace: true });
      } catch (err) {
        if (err instanceof ApiError) {
          setError(err.problem.detail ?? err.problem.title ?? '代登录凭证无效或已过期。');
        } else {
          setError('网络错误，请稍后重试。');
        }
      }
    };
    void run();
  }, [nonce, navigate]);

  const shownError = nonce ? error : '代登录链接缺少凭证（nonce），请回到运营端重新发起。';

  if (shownError) {
    return (
      <div style={centerStyle}>
        <Result
          status="error"
          title="代登录失败"
          subTitle={shownError}
          extra={
            <Button type="primary" onClick={() => void navigate('/login')}>
              去登录
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div style={{ ...centerStyle, flexDirection: 'column', gap: 16 }}>
      <Spin size="large" />
      <div style={{ color: 'var(--text-secondary)', fontSize: 14 }}>正在以代登录身份进入...</div>
    </div>
  );
}
