import { Button, Empty, Modal, Popconfirm, Skeleton } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { formatDateTime, StatusPill, toneBadge } from '@wen-saas/shared-ui';
import { opsTeamMemberApi, type OpsMemberListItem } from '@wen-saas/api-client';
import { http } from '../../../lib/http';
import { useUnbindMemberOauth } from '../hooks/useMemberMutations';

const PROVIDER_LABEL: Record<string, string> = {
  dingtalk: '钉钉',
  wechat: '微信',
  wecom: '企业微信',
};

type Props = {
  open: boolean;
  member: OpsMemberListItem;
  onClose: () => void;
};

/** 成员第三方账号绑定查看 + 解绑（运营成员常态仅企业 SSO，多为空态）。 */
export function MemberOAuthModal({ open, member, onClose }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'team-member-oauth', member.id],
    queryFn: () => opsTeamMemberApi(http).listOauthBindings(member.id),
    enabled: open,
  });
  const unbind = useUnbindMemberOauth();
  const bindings = data ?? [];

  return (
    <Modal
      open={open}
      title={`第三方账号 · ${member.realName}`}
      onCancel={onClose}
      footer={null}
      destroyOnHidden
      width={520}
    >
      {isLoading ? (
        <Skeleton active />
      ) : bindings.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="该成员暂无第三方账号绑定（运营成员通常仅企业 SSO）"
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {bindings.map((b) => (
            <div
              key={b.provider}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 14,
                padding: '12px 16px',
                border: '1px solid var(--border)',
                borderRadius: 8,
              }}
            >
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ fontWeight: 600 }}>
                    {PROVIDER_LABEL[b.provider] ?? b.provider}
                  </span>
                  <StatusPill badge={toneBadge('success', '已绑定')} />
                </div>
                <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>
                  {b.nickname ?? '—'} · 绑定于 {formatDateTime(b.boundAt)}
                </div>
              </div>
              <Popconfirm
                title={`解绑${PROVIDER_LABEL[b.provider] ?? b.provider}`}
                description="解绑后该成员将无法用此账号登录，确定继续？"
                okText="解绑"
                cancelText="取消"
                okButtonProps={{ danger: true }}
                onConfirm={() => unbind.mutate({ userId: member.id, provider: b.provider })}
              >
                <Button size="small" danger loading={unbind.isPending}>
                  解绑
                </Button>
              </Popconfirm>
            </div>
          ))}
        </div>
      )}
    </Modal>
  );
}
