import { type ReactElement, useCallback, useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router';
import { Button, DotLoading, ErrorBlock } from 'antd-mobile';
import { useAuthStore, useSessionLockStore } from '@wen-saas/auth';
import { loadCapabilities, useCapabilities, useCapabilitiesStore } from '@wen-saas/permissions';
import { ApiError } from '@wen-saas/api-client';
import { http } from '../../lib/http';

type Phase = 'loading' | 'error';

/**
 * H5 路由守卫 + 启动门控。进受保护区前必须成功拉到能力包：成功才渲染；
 * 401（会话失效，含静默刷新已失败）→ 清态回登录页；网络 / 5xx → 出可重试错误页。
 * 与租户 PC 端 RequireAuth 同构，UI 换成 antd-mobile。
 */
export function RequireAuth({ children }: { children: ReactElement }) {
  const token = useAuthStore((s) => s.token);
  const location = useLocation();
  const caps = useCapabilities();
  const [phase, setPhase] = useState<Phase>('loading');
  const [attempt, setAttempt] = useState(0);

  const bootstrap = useCallback(() => {
    let cancelled = false;
    loadCapabilities(http)
      .then(() => undefined)
      .catch((err: unknown) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.isUnauthorized()) {
          useSessionLockStore.getState().unlock();
          useAuthStore.getState().clear();
          useCapabilitiesStore.getState().reset();
        } else {
          setPhase('error');
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (token && !caps) return bootstrap();
  }, [token, caps, attempt, bootstrap]);

  if (!token) {
    const from = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?from=${from}`} replace />;
  }

  if (caps) return children;

  if (phase === 'error') {
    return (
      <div style={{ padding: '64px 24px', textAlign: 'center' }}>
        <ErrorBlock
          status="default"
          title="加载失败"
          description="无法获取你的账号权限，请检查网络后重试。"
        />
        <Button
          color="primary"
          style={{ marginTop: 16 }}
          onClick={() => {
            setPhase('loading');
            setAttempt((n) => n + 1);
          }}
        >
          重试
        </Button>
      </div>
    );
  }

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100dvh',
        color: 'var(--brand)',
      }}
    >
      <DotLoading color="primary" />
    </div>
  );
}
