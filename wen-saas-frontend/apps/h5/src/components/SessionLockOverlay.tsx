import { Button, CenterPopup } from 'antd-mobile';
import { useSessionLockStore } from '@wen-saas/auth';
import { currentTenantKeyFromLocation } from '../lib/tenant-path';

function loginPath(): string {
  return currentTenantKeyFromLocation() ? '/login' : '/';
}

/**
 * 会话锁定 / 重认证浮层（H5）。由 http 拦截器（expired）与身份 watcher（account-switched /
 * logged-out）驱动。H5 单域、单页栈，统一引导回本端登录页重新登录。
 */
export function SessionLockOverlay() {
  const { locked, reason, otherName } = useSessionLockStore();
  if (!locked) return null;

  const gotoLogin = () => window.location.assign(loginPath());

  const title =
    reason === 'account-switched'
      ? '账号已在别处切换'
      : reason === 'logged-out'
        ? '账号已退出'
        : '登录已过期';

  const body =
    reason === 'account-switched'
      ? `检测到你登录了账号 ${otherName ?? ''}，当前会话已锁定以防数据串号，请重新登录。`
      : reason === 'logged-out'
        ? '你已退出登录，请重新登录后继续。'
        : '登录状态已过期，请重新登录后继续。';

  return (
    <CenterPopup
      visible
      closeOnMaskClick={false}
      showCloseButton={false}
      style={{ '--max-width': '320px' }}
    >
      <div style={{ padding: 24 }}>
        <div style={{ fontSize: 17, fontWeight: 600, marginBottom: 10 }}>{title}</div>
        <p
          style={{
            fontSize: 14,
            color: 'var(--text-secondary)',
            lineHeight: 1.7,
            margin: '0 0 20px',
          }}
        >
          {body}
        </p>
        <Button block color="primary" onClick={gotoLogin}>
          重新登录
        </Button>
      </div>
    </CenterPopup>
  );
}
