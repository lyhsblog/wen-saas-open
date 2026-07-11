import { useEffect, useMemo, useState } from 'react';
import { App, Empty, Spin } from 'antd';
import type { RoleCreateRequest, RoleDetail, RoleUpdateRequest } from '@wen-saas/domain';
import { describeApiError } from '@wen-saas/api-client';
import { RoleListPane } from './RoleListPane';
import { RoleHeader } from './RoleHeader';
import type { RoleTabKey } from './RoleHeader';
import { RoleBasicTab } from './RoleBasicTab';
import { RolePermissionsTab } from './RolePermissionsTab';
import { RoleDataScopeTab } from './RoleDataScopeTab';
import { RoleMembersTab } from './RoleMembersTab';
import { RoleFormModal } from './RoleFormModal';
import { RecommendedRolesModal } from './RecommendedRolesModal';
import {
  useCreateRole,
  useDeleteRole,
  useRoleDetail,
  useRoleMembers,
  useRoles,
  useSetRoleStatus,
  useUpdateRole,
} from './roleQueries';

export function RolePage() {
  const { message, modal } = App.useApp();
  const [requestedId, setRequestedId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<RoleTabKey>('basic');
  const [formModalOpen, setFormModalOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<RoleDetail | null>(null);
  const [recommendedOpen, setRecommendedOpen] = useState(false);
  // 权限/数据范围 Tab 编辑中有未保存改动时为 true，切角色/切 Tab 前二次确认，防止静默丢弃
  const [tabDirty, setTabDirty] = useState(false);

  // dirty 时先 modal.confirm，确认后才执行切换；干净时直接切
  const confirmIfDirty = (action: () => void) => {
    if (!tabDirty) {
      action();
      return;
    }
    modal.confirm({
      title: '放弃未保存的修改？',
      content: '当前配置尚未保存，切换后修改将丢失。',
      okText: '放弃修改',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: action,
    });
  };

  const rolesQuery = useRoles();
  const roles = useMemo(() => rolesQuery.data ?? [], [rolesQuery.data]);

  // 派生选中 ID：若 requestedId 在列表中则用它，否则回退到第一个，避免 effect 反向同步
  const selectedId = useMemo<string | null>(() => {
    if (roles.length === 0) return null;
    if (requestedId && roles.some((r) => String(r.id) === requestedId)) return requestedId;
    const firstId = roles[0]?.id;
    return firstId != null ? String(firstId) : null;
  }, [roles, requestedId]);

  const detailQuery = useRoleDetail(selectedId);
  const detail = detailQuery.data ?? null;

  const membersQuery = useRoleMembers(selectedId);
  const members = membersQuery.data ?? [];

  useEffect(() => {
    if (rolesQuery.error) message.error(describeApiError(rolesQuery.error, '加载角色列表失败'));
  }, [rolesQuery.error, message]);
  useEffect(() => {
    if (detailQuery.error) message.error(describeApiError(detailQuery.error, '加载角色详情失败'));
  }, [detailQuery.error, message]);

  const createRole = useCreateRole();
  const updateRole = useUpdateRole(selectedId ?? '');
  const deleteRole = useDeleteRole();
  const setRoleStatus = useSetRoleStatus(selectedId ?? '');

  // 错误向上抛给 modal 处理（422 → 字段内联，其它 → modal 自己 toast）；成功路径在这里处理
  const handleSubmit = async (body: RoleCreateRequest | RoleUpdateRequest) => {
    if (editingRole) {
      await updateRole.mutateAsync(body);
      message.success('已更新角色');
    } else {
      const created = await createRole.mutateAsync(body as RoleCreateRequest);
      message.success('已创建角色');
      if (created.id != null) setRequestedId(String(created.id));
    }
    setFormModalOpen(false);
    setEditingRole(null);
  };

  const handleToggleStatus = async (enabled: boolean) => {
    if (!selectedId) return;
    try {
      await setRoleStatus.mutateAsync(enabled);
      message.success(enabled ? '已启用' : '已停用');
    } catch (err) {
      message.error(describeApiError(err, '状态切换失败'));
    }
  };

  const handleDelete = async () => {
    if (!selectedId) return;
    try {
      await deleteRole.mutateAsync(selectedId);
      message.success('已删除角色');
      setRequestedId(null);
    } catch (err) {
      message.error(describeApiError(err, '删除失败'));
    }
  };

  return (
    <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>
      <Spin spinning={rolesQuery.isPending} delay={120}>
        <RoleListPane
          roles={roles}
          selectedId={selectedId}
          onSelect={(id) => {
            if (id === selectedId) return;
            confirmIfDirty(() => setRequestedId(id));
          }}
          onCreateRole={() => {
            setEditingRole(null);
            setFormModalOpen(true);
          }}
          onOpenRecommended={() => setRecommendedOpen(true)}
        />
      </Spin>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {selectedId == null ? (
          <Empty description="暂无角色，请先新建" style={{ marginTop: 120 }} />
        ) : detailQuery.isPending ? (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Spin />
          </div>
        ) : detail ? (
          <>
            <RoleHeader
              detail={detail}
              activeTab={activeTab}
              onTabChange={(tab) => {
                if (tab === activeTab) return;
                confirmIfDirty(() => setActiveTab(tab));
              }}
              onToggleStatus={(enabled) => {
                void handleToggleStatus(enabled);
              }}
              onDelete={() => {
                void handleDelete();
              }}
            />
            <div style={{ flex: 1, overflow: 'auto' }}>
              {activeTab === 'basic' && (
                <RoleBasicTab
                  detail={detail}
                  onEdit={() => {
                    setEditingRole(detail);
                    setFormModalOpen(true);
                  }}
                />
              )}
              {activeTab === 'permissions' && (
                <RolePermissionsTab
                  // key-remount：切角色时整段重挂，确认「放弃修改」后本地编辑态真正丢弃
                  key={String(detail.id ?? '')}
                  roleId={String(detail.id ?? '')}
                  permissions={detail.permissions ?? []}
                  readonly={detail.code === 'SYSTEM_OWNER'}
                  onDirtyChange={setTabDirty}
                />
              )}
              {activeTab === 'dataScope' && (
                <RoleDataScopeTab
                  key={String(detail.id ?? '')}
                  roleId={String(detail.id ?? '')}
                  detail={detail}
                  onDirtyChange={setTabDirty}
                />
              )}
              {activeTab === 'members' && (
                <RoleMembersTab
                  roleId={String(detail.id ?? '')}
                  members={members}
                  isReadonly={detail.code === 'SYSTEM_OWNER'}
                />
              )}
            </div>
          </>
        ) : null}
      </div>
      <RoleFormModal
        open={formModalOpen}
        editing={editingRole}
        onCancel={() => {
          setFormModalOpen(false);
          setEditingRole(null);
        }}
        onSubmit={handleSubmit}
      />
      <RecommendedRolesModal
        open={recommendedOpen}
        onClose={() => setRecommendedOpen(false)}
        onActivated={(roleId) => {
          void rolesQuery.refetch();
          setRequestedId(roleId);
          setRecommendedOpen(false);
        }}
        onViewRole={(roleId) => {
          setRequestedId(roleId);
          setRecommendedOpen(false);
        }}
      />
    </div>
  );
}
