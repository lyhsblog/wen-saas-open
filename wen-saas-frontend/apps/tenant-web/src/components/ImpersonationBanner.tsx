import { useState } from 'react';
import { Alert, Button } from 'antd';
import { LogoutOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router';
import { authApi } from '@wen-saas/api-client';
import { useAuthStore } from '@wen-saas/auth';
import { useCapabilitiesStore, useImpersonation } from '@wen-saas/permissions';
import { http } from '../lib/http';

/**
 * 运营代登录标识条。镜像 {@link ReadonlyBanner} 的"全局会话级提示条"范式：
 * 非代登录态返回 null；代登录态渲染红色 banner + 操作人名 + 「退出代登录」按钮。
 * 代登录态由后端 capabilities（`Session.originalSessionId`）权威判定，刷新不丢。
 */
export function ImpersonationBanner() {
  const { impersonating, operatorName } = useImpersonation();
  const navigate = useNavigate();
  const [exiting, setExiting] = useState(false);

  if (!impersonating) return null;

  const onExit = async () => {
    setExiting(true);
    try {
      // 后端销毁代登录 session；原会话已过期等情况静默忽略，前端仍清本端登录态。
      await authApi(http).exitImpersonation();
    } catch {
      /* ignore */
    } finally {
      useAuthStore.getState().clear();
      // 同时清能力包：caps 是内存态，RequireAuth 仅在 caps==null 时重拉。
      // 不清则代登录的 impersonating/权限会残留到同标签页下一个会话（错误的红条 + 越权）。
      useCapabilitiesStore.getState().reset();
      await navigate('/login', { replace: true });
    }
  };

  return (
    <Alert
      banner
      type="error"
      showIcon
      style={{ borderRadius: 0 }}
      message={
        <span>
          ⚠ 运营代登录中 · 操作人：<strong>{operatorName ?? '未知'}</strong> · 你的所有操作将被审计
        </span>
      }
      action={
        <Button
          size="small"
          danger
          icon={<LogoutOutlined />}
          loading={exiting}
          onClick={() => void onExit()}
        >
          退出代登录
        </Button>
      }
    />
  );
}
