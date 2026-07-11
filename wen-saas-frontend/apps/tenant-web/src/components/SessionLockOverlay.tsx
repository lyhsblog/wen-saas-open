import { Button, Modal } from 'antd';
import { useSessionLockStore } from '@wen-saas/auth';
import { currentTenantKeyFromLocation } from '../lib/tenant-path';

/** 登录页路径；有租户子域时走 /login，裸域（无租户）回站点根。 */
function loginPath(): string {
  return currentTenantKeyFromLocation() ? '/login' : '/';
}

/**
 * 会话锁定 / 重认证浮层（租户端）。逻辑同运营端，重登走当前租户的规范登录页。
 */
export function SessionLockOverlay() {
  const { locked, reason, expectedName, otherName } = useSessionLockStore();
  if (!locked) return null;

  const openLogin = () => window.open(loginPath(), '_blank', 'noopener');
  const gotoLogin = () => window.location.assign(loginPath());
  const reload = () => window.location.reload();

  const title =
    reason === 'account-switched'
      ? '账号已在别处切换'
      : reason === 'logged-out'
        ? '账号已在别处退出'
        : '登录已过期';

  const body =
    reason === 'account-switched'
      ? `检测到你在其他标签页登录了账号 ${otherName ?? ''}，当前页面属于 ${expectedName ?? '原账号'}，已锁定以防数据串号。`
      : reason === 'logged-out'
        ? '你已在其他标签页退出登录。请重新登录后继续。'
        : '登录状态已过期。在新标签页重新登录后，回到本页即可自动解锁，未保存的内容不会丢失。';

  return (
    <Modal
      open
      title={title}
      closable={false}
      mask={{ closable: false }}
      keyboard={false}
      footer={null}
      zIndex={3000}
    >
      <p style={{ marginBottom: 20, lineHeight: 1.7 }}>{body}</p>
      <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
        {reason === 'account-switched' ? (
          <>
            <Button onClick={openLogin}>登回 {expectedName ?? '原账号'}</Button>
            <Button type="primary" onClick={reload}>
              切到新账号
            </Button>
          </>
        ) : reason === 'logged-out' ? (
          <Button type="primary" onClick={gotoLogin}>
            重新登录
          </Button>
        ) : (
          <>
            <Button onClick={gotoLogin}>返回登录页</Button>
            <Button type="primary" onClick={openLogin}>
              去重新登录
            </Button>
          </>
        )}
      </div>
    </Modal>
  );
}
