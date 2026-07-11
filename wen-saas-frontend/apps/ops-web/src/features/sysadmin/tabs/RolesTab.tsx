import { useState } from 'react';
import { Alert, Button, Popconfirm, Space, Table, Tooltip } from 'antd';
import { CopyableText, CountChip } from '@wen-saas/shared-ui';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { opsTeamRoleApi, type OpsRoleListItem } from '@wen-saas/api-client';
import { http } from '../../../lib/http';
import { dataScopeLabel } from '../utils/sysadminMeta';
import { useDeleteRole } from '../hooks/useRoleMutations';
import { RoleFormModal } from '../modals/RoleFormModal';
import { RolePermissionsModal } from '../modals/RolePermissionsModal';
import { RoleMembersModal } from '../modals/RoleMembersModal';

type RoleRef = { id: string; name: string };

export function RolesTab() {
  const [createOpen, setCreateOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [permRole, setPermRole] = useState<RoleRef | null>(null);
  const [membersRole, setMembersRole] = useState<RoleRef | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'team-roles'],
    queryFn: () => opsTeamRoleApi(http).listRoles(),
  });

  const { data: editingDetail } = useQuery({
    queryKey: ['ops', 'team-role', editingId],
    queryFn: () => opsTeamRoleApi(http).getRole(editingId as string),
    enabled: editingId != null,
  });

  const del = useDeleteRole();

  const columns: ColumnsType<OpsRoleListItem> = [
    {
      title: '角色',
      dataIndex: 'name',
      render: (name: string, row) => (
        <div>
          <Space size={6}>
            <span style={{ fontWeight: 600 }}>{name}</span>
            {row.code === 'OPS_SUPER_ADMIN' && <CountChip tone="brand">特权</CountChip>}
          </Space>
          {row.description && (
            <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 2 }}>
              {row.description}
            </div>
          )}
        </div>
      ),
    },
    {
      title: '编码',
      dataIndex: 'code',
      render: (c: string) => <CopyableText value={c} style={{ fontSize: 12 }} />,
    },
    { title: '成员数', dataIndex: 'memberCount', width: 80 },
    {
      title: '数据范围',
      dataIndex: 'dataScope',
      width: 110,
      render: (s: OpsRoleListItem['dataScope']) => <CountChip>{dataScopeLabel(s)}</CountChip>,
    },
    { title: '权限数', dataIndex: 'permissionCount', width: 80 },
    {
      title: '来源',
      dataIndex: 'systemPreset',
      width: 100,
      render: (preset: boolean) =>
        preset ? <CountChip tone="brand">系统预置</CountChip> : <CountChip>自定义</CountChip>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      render: (_v, row) => {
        const deletable = !row.systemPreset && row.memberCount === 0;
        const deleteHint = row.systemPreset
          ? '系统预置角色不可删除'
          : row.memberCount > 0
            ? '该角色仍有成员，无法删除'
            : undefined;
        return (
          <Space size={0}>
            <Button type="link" size="small" onClick={() => setEditingId(row.id)}>
              编辑
            </Button>
            <Button
              type="link"
              size="small"
              onClick={() => setPermRole({ id: row.id, name: row.name })}
            >
              编辑权限
            </Button>
            <Button
              type="link"
              size="small"
              onClick={() => setMembersRole({ id: row.id, name: row.name })}
            >
              分配成员
            </Button>
            <Tooltip title={deleteHint}>
              <Popconfirm
                title={`确认删除角色「${row.name}」？`}
                okText="删除"
                okButtonProps={{ danger: true }}
                disabled={!deletable}
                onConfirm={() => del.mutate(row.id)}
              >
                <Button type="link" size="small" danger disabled={!deletable}>
                  删除
                </Button>
              </Popconfirm>
            </Tooltip>
          </Space>
        );
      },
    },
  ];

  return (
    <div style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex' }}>
        <div style={{ flex: 1 }} />
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
          新建自定义角色
        </Button>
      </div>

      <Table
        rowKey="id"
        size="middle"
        loading={isLoading}
        columns={columns}
        dataSource={data ?? []}
        pagination={false}
      />

      <Alert
        type="info"
        showIcon
        message="预置角色（超管 / 客户经理）不可删除；客户经理的数据范围为「仅本人负责」，仅能看到自己负责（csm_user_id=self）的租户。"
      />

      <RoleFormModal
        open={createOpen || (editingId != null && editingDetail != null)}
        editing={editingId != null ? (editingDetail ?? null) : null}
        onClose={() => {
          setCreateOpen(false);
          setEditingId(null);
        }}
      />
      {permRole && (
        <RolePermissionsModal
          open
          roleId={permRole.id}
          roleName={permRole.name}
          onClose={() => setPermRole(null)}
        />
      )}
      {membersRole && (
        <RoleMembersModal
          open
          roleId={membersRole.id}
          roleName={membersRole.name}
          onClose={() => setMembersRole(null)}
        />
      )}
    </div>
  );
}
