import { type ReactNode, useState } from 'react';
import { Dialog } from 'antd-mobile';
import { useNavigate } from 'react-router';
import { useAuthStore } from '@wen-saas/auth';
import { useCapabilitiesStore } from '@wen-saas/permissions';
import { authService } from '../../lib/http';
import { Tag, SectionTitle } from '../../components/ui';
import { ChangePasswordModal } from './ChangePasswordModal';
import { OAuthManageModal } from './OAuthManageModal';
import {
  useOAuthBindings,
  useProfileBasic,
  useProfileSecurity,
  useProfileWork,
  useSessions,
} from './profileQueries';

const PROVIDER_LABEL: Record<string, string> = { wechat: '微信', wecom: '企微', dingtalk: '钉钉' };

export function ProfilePage() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const [pwdOpen, setPwdOpen] = useState(false);
  const [oauthOpen, setOauthOpen] = useState(false);

  const basicQ = useProfileBasic();
  const workQ = useProfileWork();
  const securityQ = useProfileSecurity();
  const sessionsQ = useSessions();
  const bindingsQ = useOAuthBindings();

  const basic = basicQ.data;
  const displayName = basic?.realName || basic?.username || user?.username || '—';
  const deptName = workQ.data?.mainDeptName ?? '';
  const roleNames = (workQ.data?.roles ?? []).map((r) => r.name).filter(Boolean);

  const mfaEnabled = securityQ.data?.mfaEnabled;
  const sessions = sessionsQ.data ?? [];
  const otherCount = sessions.filter((s) => !s.current).length;
  const bindings = bindingsQ.data ?? [];
  const boundLabel =
    bindings.length > 0
      ? bindings.map((b) => PROVIDER_LABEL[b.provider ?? ''] ?? b.provider).join('、') + ' 已绑定'
      : '未绑定任何第三方账号';

  const handleLogout = async () => {
    const ok = await Dialog.confirm({ content: '确定退出登录？' });
    if (!ok) return;
    await authService.logout();
    useCapabilitiesStore.getState().reset();
    void navigate(`/login`, { replace: true });
  };

  return (
    <div style={{ minHeight: '100%', background: 'var(--bg)' }}>
      {/* 用户卡（渐变）直接作为顶部，不用标题栏 */}
      <div
        style={{
          padding: 'calc(20px + env(safe-area-inset-top)) 16px 18px',
          background: 'linear-gradient(180deg, var(--brand) 0%, var(--brand-active) 100%)',
          color: '#fff',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div
            style={{
              width: 56,
              height: 56,
              borderRadius: 28,
              background: 'rgba(255,255,255,0.2)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 22,
              fontWeight: 600,
              flexShrink: 0,
            }}
          >
            {displayName.slice(0, 1).toUpperCase()}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 18, fontWeight: 600 }}>{displayName}</div>
            {deptName && (
              <div style={{ fontSize: 12, opacity: 0.85, marginTop: 3 }}>{deptName}</div>
            )}
            <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
              {roleNames.map((r) => (
                <span
                  key={r}
                  style={{
                    fontSize: 11,
                    padding: '2px 8px',
                    background: 'rgba(255,255,255,0.2)',
                    borderRadius: 999,
                  }}
                >
                  {r}
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div style={{ padding: 12 }}>
        <SectionTitle>协同入口</SectionTitle>
        <ListGroup>
          <Row
            label="通讯录"
            sub="查找成员与部门"
            onClick={() => void navigate(`/contacts`)}
          />
          <Row
            label="文件中心"
            sub="我的 / 共享文件"
            onClick={() => void navigate(`/files`)}
          />
        </ListGroup>

        <SectionTitle>账号与安全</SectionTitle>
        <ListGroup>
          <Row label="修改密码" sub="定期更换更安全" onClick={() => setPwdOpen(true)} />
          <Row
            label="登录二步验证"
            sub={
              mfaEnabled == null ? '加载中…' : mfaEnabled ? '已开启' : '未开启 · 请到网页端开启'
            }
            right={mfaEnabled === false ? <Tag tone="warning">建议开启</Tag> : undefined}
          />
          <Row label="第三方账号" sub={boundLabel} onClick={() => setOauthOpen(true)} />
        </ListGroup>

        <SectionTitle>当前会话</SectionTitle>
        <ListGroup>
          <Row label="当前设备" sub="本机 H5 端" />
          <Row label="其他设备" sub={`${otherCount} 台在线`} />
        </ListGroup>
        <div
          style={{
            fontSize: 11,
            color: 'var(--text-tertiary)',
            padding: '0 4px 12px',
            lineHeight: 1.6,
          }}
        >
          Web / H5 共用同一套登录会话。主管理员在 Web 端将你强制下线时，H5 端会同步失效。
        </div>

        <button
          onClick={() => void handleLogout()}
          style={{
            width: '100%',
            height: 46,
            marginTop: 4,
            marginBottom: 16,
            borderRadius: 12,
            background: 'var(--bg-elev)',
            border: '1px solid var(--border, #ebedf0)',
            color: 'var(--danger, #dc2626)',
            fontSize: 15,
            fontWeight: 500,
          }}
        >
          退出登录
        </button>
      </div>

      {pwdOpen && <ChangePasswordModal onClose={() => setPwdOpen(false)} />}
      {oauthOpen && <OAuthManageModal onClose={() => setOauthOpen(false)} />}
    </div>
  );
}

function ListGroup({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        background: 'var(--bg-elev)',
        borderRadius: 12,
        overflow: 'hidden',
        marginBottom: 12,
      }}
      className="h5-list-group"
    >
      {children}
    </div>
  );
}

function Row({
  label,
  sub,
  right,
  onClick,
}: {
  label: string;
  sub?: string;
  right?: ReactNode;
  onClick?: () => void;
}) {
  return (
    <div
      onClick={onClick}
      className="h5-list-row"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '14px 16px',
        borderTop: '1px solid var(--divider, #f5f5f5)',
      }}
    >
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 14, fontWeight: 500 }}>{label}</div>
        {sub && (
          <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 2 }}>{sub}</div>
        )}
      </div>
      {right}
      {onClick && <span style={{ color: 'var(--text-tertiary)', fontSize: 16 }}>›</span>}
    </div>
  );
}
