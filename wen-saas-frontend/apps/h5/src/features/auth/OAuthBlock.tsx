import {
  ALL_PROVIDERS,
  ENV_HINT,
  PROVIDER_META,
  type OAuthProvider,
  type RuntimeEnv,
} from './oauth';

/** 登录页第三方登录区块：环境识别置顶推荐 + 其他渠道。 */
export function OAuthBlock({
  env,
  busy,
  onLogin,
}: {
  env: RuntimeEnv;
  busy: boolean;
  onLogin: (provider: OAuthProvider) => void;
}) {
  const recommended = env !== 'browser' ? env : null;
  const others = ALL_PROVIDERS.filter((p) => p !== recommended);

  return (
    <div>
      {recommended && (
        <>
          <div
            style={{
              padding: 12,
              background: 'var(--success-bg, #ecfdf3)',
              border: '1px solid var(--success-border, #bbf7d0)',
              borderRadius: 12,
              fontSize: 12,
              color: 'var(--success, #16a34a)',
              marginBottom: 14,
            }}
          >
            {ENV_HINT[recommended]}
          </div>
          <button
            disabled={busy}
            onClick={() => onLogin(recommended)}
            style={{
              width: '100%',
              height: 48,
              borderRadius: 12,
              background: PROVIDER_META[recommended].color,
              color: '#fff',
              border: 'none',
              fontSize: 16,
              fontWeight: 600,
              marginBottom: 16,
              opacity: busy ? 0.6 : 1,
            }}
          >
            {PROVIDER_META[recommended].name}一键登录
          </button>
        </>
      )}

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          margin: '8px 0 16px',
          color: 'var(--text-tertiary)',
        }}
      >
        <div style={{ flex: 1, height: 1, background: 'var(--border, #ebedf0)' }} />
        <span style={{ fontSize: 11 }}>{recommended ? '其他登录方式' : '第三方账号登录'}</span>
        <div style={{ flex: 1, height: 1, background: 'var(--border, #ebedf0)' }} />
      </div>

      <div style={{ display: 'flex', gap: 24, justifyContent: 'center' }}>
        {others.map((p) => {
          const m = PROVIDER_META[p];
          return (
            <button
              key={p}
              disabled={busy}
              onClick={() => onLogin(p)}
              style={{
                background: 'transparent',
                border: 'none',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 6,
                opacity: busy ? 0.6 : 1,
              }}
            >
              <span
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: 22,
                  background: m.color + '1a',
                  color: m.color,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 13,
                  fontWeight: 700,
                }}
              >
                {m.name.slice(0, 1)}
              </span>
              <span style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>{m.name}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
