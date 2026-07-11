import { useState } from 'react';
import { Button, Card, Descriptions, Empty, Popconfirm } from 'antd';
import { StatusPill } from '@wen-saas/shared-ui';
import { ReloadOutlined, SwapOutlined, UnlockOutlined } from '@ant-design/icons';
import { USER_STATUS_MAP } from '../utils/statusOf';
import { TransferMainAdminModal } from '../modals/TransferMainAdminModal';
import { ReissueResetLinkModal } from '../ReissueResetLinkModal';
import { useUnlockLoginAttempt } from '../hooks/useTenantMutations';
import type { OpsMainAdminView } from '@wen-saas/api-client';

type Props = {
  tenantId: string;
  tenantCode: string;
  tenantName: string;
  mainAdmin: OpsMainAdminView | null;
};

export function TabMainAdmin({ tenantId, tenantCode, tenantName, mainAdmin }: Props) {
  const [reissueOpen, setReissueOpen] = useState(false);
  const [transferOpen, setTransferOpen] = useState(false);
  const { mutate: unlock, isPending: unlocking } = useUnlockLoginAttempt(tenantId);

  if (!mainAdmin) {
    return <Empty description="该租户暂未指定主管理员" />;
  }

  const statusBadge = USER_STATUS_MAP[mainAdmin.status] ?? {
    kind: 'neutral' as const,
    text: String(mainAdmin.status),
    color: '',
  };

  return (
    <div>
      <Card size="small">
        <Descriptions column={1} size="small">
          <Descriptions.Item label="账号">{mainAdmin.username}</Descriptions.Item>
          <Descriptions.Item label="姓名">{mainAdmin.realName ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="账号状态">
            <StatusPill badge={statusBadge} />
          </Descriptions.Item>
          <Descriptions.Item label="用户 ID">
            <code style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>{mainAdmin.userId}</code>
          </Descriptions.Item>
          <Descriptions.Item label="手机号">
            {mainAdmin.mobile ?? <span style={{ color: 'var(--text-tertiary)' }}>—</span>}
          </Descriptions.Item>
          <Descriptions.Item label="邮箱">
            {mainAdmin.email ?? <span style={{ color: 'var(--text-tertiary)' }}>—</span>}
          </Descriptions.Item>
        </Descriptions>
        <div
          style={{
            marginTop: 16,
            paddingTop: 12,
            borderTop: '1px solid var(--divider)',
            display: 'flex',
            gap: 8,
            flexWrap: 'wrap',
            alignItems: 'center',
          }}
        >
          <Button icon={<ReloadOutlined />} size="small" onClick={() => setReissueOpen(true)}>
            重发密码设置链接
          </Button>
          <Button icon={<SwapOutlined />} size="small" onClick={() => setTransferOpen(true)}>
            重新指定主管理员
          </Button>
          {mainAdmin.status === 1 && (
            <Popconfirm
              title="解除登录锁定"
              description="将清除该账号的登录失败计数，允许立即重新登录。"
              okText="确认解锁"
              cancelText="取消"
              onConfirm={() => unlock(mainAdmin.userId)}
            >
              <Button icon={<UnlockOutlined />} loading={unlocking} size="small">
                解除登录锁定
              </Button>
            </Popconfirm>
          )}
          {mainAdmin.status === 0 && (
            <span style={{ fontSize: 12, color: 'var(--text-tertiary)', alignSelf: 'center' }}>
              账号未激活 — 请先发送密码设置链接
            </span>
          )}
        </div>
      </Card>

      <ReissueResetLinkModal
        open={reissueOpen}
        tenantId={tenantId}
        tenantCode={tenantCode}
        adminUsername={mainAdmin.username}
        onClose={() => setReissueOpen(false)}
      />
      <TransferMainAdminModal
        open={transferOpen}
        tenantId={tenantId}
        tenantCode={tenantCode}
        tenantName={tenantName}
        currentAdminUsername={mainAdmin.username}
        onClose={() => setTransferOpen(false)}
      />
    </div>
  );
}
