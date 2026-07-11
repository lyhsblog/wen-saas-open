import { useState } from 'react';
import { Modal, Spin, Transfer } from 'antd';
import type { Key } from 'react';
import { useQuery } from '@tanstack/react-query';
import { opsTeamMemberApi, opsTeamRoleApi } from '@wen-saas/api-client';
import { http } from '../../../lib/http';
import { useAssignRoleMembers } from '../hooks/useRoleMutations';

type Props = {
  open: boolean;
  roleId: string;
  roleName: string;
  onClose: () => void;
};

export function RoleMembersModal({ open, roleId, roleName, onClose }: Props) {
  // 未改动前以服务端成员为准；用户穿梭后以 override 为准。
  const [override, setOverride] = useState<string[] | null>(null);

  const { data: members, isLoading: membersLoading } = useQuery({
    queryKey: ['ops', 'team-members'],
    queryFn: () => opsTeamMemberApi(http).listMembers(),
    enabled: open,
  });
  const { data: roleMembers, isLoading: roleMembersLoading } = useQuery({
    queryKey: ['ops', 'team-role-members', roleId],
    queryFn: () => opsTeamRoleApi(http).listRoleMembers(roleId),
    enabled: open,
  });

  const targetKeys = override ?? roleMembers?.map((m) => m.userId) ?? [];
  const assign = useAssignRoleMembers(roleId);

  const submit = async () => {
    await assign.mutateAsync({ userIds: targetKeys });
    onClose();
  };

  const dataSource = (members ?? []).map((m) => ({
    key: m.id,
    title: `${m.realName}（${m.username}）`,
    disabled: m.status === 0,
  }));

  const loading = membersLoading || roleMembersLoading;

  return (
    <Modal
      open={open}
      title={`分配成员 · ${roleName}`}
      onCancel={onClose}
      onOk={() => void submit()}
      okText="保存"
      confirmLoading={assign.isPending}
      mask={{ closable: false }}
      destroyOnHidden
      width={640}
    >
      {loading ? (
        <div style={{ padding: 48, textAlign: 'center' }}>
          <Spin />
        </div>
      ) : (
        <Transfer
          dataSource={dataSource}
          targetKeys={targetKeys}
          onChange={(keys: Key[]) => setOverride(keys.map(String))}
          render={(item) => item.title}
          titles={['可选成员', '已分配']}
          showSearch
          filterOption={(input, item) => item.title.includes(input)}
          listStyle={{ width: 280, height: 360 }}
        />
      )}
    </Modal>
  );
}
