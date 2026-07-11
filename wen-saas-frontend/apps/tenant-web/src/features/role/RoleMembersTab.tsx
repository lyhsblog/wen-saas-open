import { useState } from 'react';
import { App, Popconfirm } from 'antd';
import { UserAddOutlined } from '@ant-design/icons';
import type { MemberRef } from '@wen-saas/domain';
import { DataTable } from '@wen-saas/shared-ui';
import { describeApiError } from '@wen-saas/api-client';
import { PermissionGate, ReadonlyButton } from '@wen-saas/permissions';
import { useRemoveRoleMembers } from './roleQueries';
import { RoleMemberAddModal } from './RoleMemberAddModal';

type Props = {
  roleId: string;
  members: MemberRef[];
  isReadonly: boolean;
};

export function RoleMembersTab({ roleId, members, isReadonly }: Props) {
  const { message } = App.useApp();
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const [addOpen, setAddOpen] = useState(false);
  const removeMutation = useRemoveRoleMembers(roleId);

  const handleRemove = async (userIds: number[]) => {
    try {
      await removeMutation.mutateAsync({ userIds });
      setSelectedKeys((prev) => prev.filter((k) => !userIds.includes(Number(k))));
      message.success(`已移除 ${userIds.length} 名成员`);
    } catch (err) {
      message.error(describeApiError(err, '移除失败'));
    }
  };

  const columns = [
    {
      title: '成员',
      key: 'member',
      render: (_: unknown, record: MemberRef) => {
        const name = record.realName || record.username;
        return (
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
            <span>{name ?? (record.userId != null ? `#${record.userId}` : '—')}</span>
            {record.username && record.realName ? (
              <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>{record.username}</span>
            ) : null}
          </div>
        );
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_: unknown, record: MemberRef) =>
        !isReadonly && record.userId != null ? (
          <PermissionGate permission="org.member.assign_role">
            <Popconfirm
              title="确认移除该成员？"
              okText="移除"
              okButtonProps={{ danger: true }}
              cancelText="取消"
              onConfirm={() => {
                void handleRemove([record.userId as number]);
              }}
            >
              <ReadonlyButton size="small" danger type="link">
                移除
              </ReadonlyButton>
            </Popconfirm>
          </PermissionGate>
        ) : null,
    },
  ];

  return (
    <div style={{ padding: '16px 24px' }}>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginBottom: 12 }}>
        {!isReadonly && selectedKeys.length > 0 && (
          <PermissionGate permission="org.member.assign_role">
            <Popconfirm
              title={`确认批量移除 ${selectedKeys.length} 名成员？`}
              okText="移除"
              okButtonProps={{ danger: true }}
              cancelText="取消"
              onConfirm={() => {
                void handleRemove(selectedKeys.map(Number));
              }}
            >
              <ReadonlyButton danger size="small" loading={removeMutation.isPending}>
                批量移除 ({selectedKeys.length})
              </ReadonlyButton>
            </Popconfirm>
          </PermissionGate>
        )}
        {!isReadonly && (
          <PermissionGate permission="org.member.assign_role">
            <ReadonlyButton
              size="small"
              type="primary"
              icon={<UserAddOutlined />}
              onClick={() => setAddOpen(true)}
            >
              添加成员
            </ReadonlyButton>
          </PermissionGate>
        )}
      </div>
      <RoleMemberAddModal open={addOpen} roleId={roleId} onClose={() => setAddOpen(false)} />
      <DataTable<MemberRef>
        columns={columns}
        dataSource={members}
        rowKey={(r) => (r.userId != null ? String(r.userId) : '')}
        {...(!isReadonly
          ? {
              rowSelection: {
                selectedRowKeys: selectedKeys,
                onChange: (keys) => setSelectedKeys(keys),
              },
            }
          : {})}
      />
    </div>
  );
}
