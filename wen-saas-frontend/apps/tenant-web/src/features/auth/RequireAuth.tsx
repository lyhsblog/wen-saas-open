import { type ReactElement, useCallback, useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router';
import { Button, Result, Spin } from 'antd';
import { useAuthStore, useSessionLockStore } from '@wen-saas/auth';
import { loadCapabilities, useCapabilities, useCapabilitiesStore } from '@wen-saas/permissions';
import { ApiError } from '@wen-saas/api-client';
import { http } from '../../lib/http';

type Phase = 'loading' | 'error';

/**
 * 租户端路由守卫 + 启动门控。进受保护区前必须成功拉到能力包：成功才渲染；
 * 401（会话失效，含静默刷新已失败）→ 清态回租户登录页；网络 / 5xx → 出可重试错误页。
 */
export function RequireAuth({ children }: { children: ReactElement }) {
  const token = useAuthStore((s) => s.token);
  const location = useLocation();
  const caps = useCapabilities();
  const [phase, setPhase] = useState<Phase>('loading');
  const [attempt, setAttempt] = useState(0);

  const bootstrap = useCallback(() => {
    // 不在此处同步置 loading（phase 初值即 loading；重试时由按钮置回），避免 effect 内同步 setState。
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
      <Result
        status="warning"
        title="加载失败"
        subTitle="无法获取你的账号权限，请检查网络后重试。"
        extra={
          <Button
            type="primary"
            onClick={() => {
              setPhase('loading');
              setAttempt((n) => n + 1);
            }}
          >
            重试
          </Button>
        }
      />
    );
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
      }}
    >
      <Spin size="large" />
      <span style={{ color: 'var(--text-tertiary)' }}>加载中…</span>
    </div>
  );
}
