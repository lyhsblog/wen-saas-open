import { useMemo, useState } from 'react';
import { Alert, Empty, Modal, Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { opsTeamRoleApi, permissionCatalogTreeApi } from '@wen-saas/api-client';
import { http } from '../../../lib/http';
import { useUpdateRolePermissions } from '../hooks/useRoleMutations';
import { PermissionCatalogTree } from '../../permission/PermissionCatalogTree';

type Props = {
  open: boolean;
  roleId: string;
  roleName: string;
  onClose: () => void;
};

export function RolePermissionsModal({ open, roleId, roleName, onClose }: Props) {
  // 本地修改前以服务端权限为准；用户一旦勾选改动则以 override 为准。
  const [override, setOverride] = useState<string[] | null>(null);

  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ['ops', 'team-role', roleId],
    queryFn: () => opsTeamRoleApi(http).getRole(roleId),
    enabled: open,
  });
  const { data: catalog, isLoading: treeLoading } = useQuery({
    queryKey: ['ops', 'permission-catalog-tree'],
    queryFn: () => permissionCatalogTreeApi(http).ops(),
    enabled: open,
  });

  const checked = useMemo(
    () => override ?? detail?.permissions ?? [],
    [override, detail?.permissions],
  );
  const checkedSet = useMemo(() => new Set(checked), [checked]);
  const tree = catalog?.tree ?? [];
  const update = useUpdateRolePermissions(roleId);

  const setCodes = (codes: string[], on: boolean) => {
    const next = new Set(checked);
    if (on) codes.forEach((c) => next.add(c));
    else codes.forEach((c) => next.delete(c));
    setOverride([...next]);
  };

  const submit = async () => {
    await update.mutateAsync({ permissionCodes: checked });
    onClose();
  };

  const loading = detailLoading || treeLoading;

  return (
    <Modal
      open={open}
      title={`编辑权限 · ${roleName}`}
      onCancel={onClose}
      onOk={() => void submit()}
      okText="保存"
      confirmLoading={update.isPending}
      mask={{ closable: false }}
      destroyOnHidden
      width={680}
    >
      {loading ? (
        <div style={{ padding: 48, textAlign: 'center' }}>
          <Spin />
        </div>
      ) : tree.length === 0 ? (
        <Empty description="当前无可分配的 ops 权限点" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <PermissionCatalogTree tree={tree} checked={checkedSet} onSet={setCodes} />
      )}
      {!loading && (detail?.systemPreset ?? false) && (
        <Alert
          type="info"
          showIcon
          style={{ marginTop: 12 }}
          message="这是系统预置角色，可调整权限点，但请谨慎——超级管理员通常应保留全部权限。"
        />
      )}
    </Modal>
  );
}
