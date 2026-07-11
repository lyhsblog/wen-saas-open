import { Button, CenterPopup, Dialog, Toast } from 'antd-mobile';
import { ALL_PROVIDERS, PROVIDER_META } from '../auth/oauth';
import { useBindOAuth, useOAuthBindings, useUnbindOAuth } from './profileQueries';

/** 第三方账号绑定管理（绑定 / 解绑），对接 mock OAuth。 */
export function OAuthManageModal({ onClose }: { onClose: () => void }) {
  const bindingsQ = useOAuthBindings();
  const bind = useBindOAuth();
  const unbind = useUnbindOAuth();
  // 绑定状态未知（加载中）时显示占位，避免把所有 provider 误显示成「未绑定」
  const bindingsLoading = bindingsQ.isLoading;
  const bound = new Set((bindingsQ.data ?? []).map((b) => b.provider));
  const busy = bind.isPending || unbind.isPending;

  const handleBind = async (provider: string) => {
    try {
      await bind.mutateAsync(provider);
      Toast.show({ icon: 'success', content: '已绑定' });
    } catch {
      Toast.show({ content: '绑定失败' });
    }
  };

  const handleUnbind = async (provider: string, name: string) => {
    const ok = await Dialog.confirm({ content: `确定解绑${name}账号？` });
    if (!ok) return;
    try {
      await unbind.mutateAsync(provider);
      Toast.show({ icon: 'success', content: '已解绑' });
    } catch {
      Toast.show({ content: '解绑失败' });
    }
  };

  return (
    <CenterPopup visible onClose={onClose} onMaskClick={onClose} style={{ '--max-width': '340px' }}>
      <div style={{ padding: 20 }}>
        <div style={{ fontSize: 17, fontWeight: 600, marginBottom: 16 }}>第三方账号</div>
        {ALL_PROVIDERS.map((p) => {
          const m = PROVIDER_META[p];
          const isBound = bound.has(p);
          return (
            <div
              key={p}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '10px 0',
                borderTop: '1px solid var(--divider, #f5f5f5)',
              }}
            >
              <span
                style={{
                  width: 36,
                  height: 36,
                  borderRadius: 18,
                  background: m.color + '1a',
                  color: m.color,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 12,
                  fontWeight: 700,
                  flexShrink: 0,
                }}
              >
                {m.name.slice(0, 1)}
              </span>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 14, fontWeight: 500 }}>{m.name}</div>
                <div
                  style={{
                    fontSize: 11,
                    color:
                      !bindingsLoading && isBound
                        ? 'var(--success, #16a34a)'
                        : 'var(--text-tertiary)',
                  }}
                >
                  {bindingsLoading ? '加载中…' : isBound ? '已绑定' : '未绑定'}
                </div>
              </div>
              {bindingsLoading ? null : isBound ? (
                <Button
                  size="small"
                  fill="outline"
                  color="danger"
                  disabled={busy}
                  onClick={() => void handleUnbind(p, m.name)}
                >
                  解绑
                </Button>
              ) : (
                <Button
                  size="small"
                  color="primary"
                  disabled={busy}
                  onClick={() => void handleBind(p)}
                >
                  绑定
                </Button>
              )}
            </div>
          );
        })}
        <Button block style={{ marginTop: 16 }} onClick={onClose}>
          关闭
        </Button>
      </div>
    </CenterPopup>
  );
}
