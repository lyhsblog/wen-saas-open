import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Popconfirm } from 'antd';
import { CountChip, StatusPill, toneBadge } from '@wen-saas/shared-ui';
import {
  DeleteOutlined,
  EditOutlined,
  FolderOutlined,
  PlusOutlined,
  SolutionOutlined,
  UsergroupAddOutlined,
} from '@ant-design/icons';
import type { DeptView } from '@wen-saas/domain';
import { PermissionGate, ReadonlyButton } from '@wen-saas/permissions';
import { MemberSection } from '../member/MemberSection';
import { DeptLeadersModal } from '../member/DeptLeadersModal';
import { deptApi } from './deptApi';

type Props = {
  dept: DeptView;
  treeData: DeptView[];
  isRoot: boolean;
  parentName: string | null;
  onEdit: () => void;
  onCreateChild: () => void;
  onDelete: () => void;
  onToggleStatus: (enabled: boolean) => void;
  onSelectMember: (memberId: string) => void;
};

export function DeptDetailPane({
  dept,
  treeData,
  isRoot,
  parentName,
  onEdit,
  onCreateChild,
  onDelete,
  onToggleStatus,
  onSelectMember,
}: Props) {
  const isEnabled = (dept.status ?? 1) === 1;
  const [leadersOpen, setLeadersOpen] = useState(false);

  // 部门负责人：复用已有 leaders 端点（无需后端改动），用于头部「N 位负责人」徽章与「部门负责人」一行。
  // key 末尾加 'refs' 与 DeptLeadersModal 的 ['dept-leaders', deptId]（返回 {members,leaderIds}）区分，
  // 避免同 key 不同返回结构串味；同时仍被 modal 保存后的 invalidate（前缀匹配）刷新。
  const leadersQuery = useQuery({
    queryKey: ['dept-leaders', String(dept.id), 'refs'],
    queryFn: () => deptApi.listLeaders(String(dept.id)),
    enabled: !isRoot,
  });
  const leaders = leadersQuery.data ?? [];
  const leaderCount = leaders.length;
  const firstLeaderName = leaders[0]?.realName || leaders[0]?.username || '';
  const leaderLabel =
    leaderCount === 0
      ? '未设置'
      : leaderCount === 1
        ? firstLeaderName
        : `${firstLeaderName} 等 ${leaderCount} 人`;

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {/* 部门信息条：标题与操作同处一行（向 UI 原型看齐，压缩头部高度） */}
      <div
        style={{
          padding: '14px 24px',
          borderBottom: '1px solid var(--divider)',
          background: 'var(--bg-elev)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <FolderOutlined style={{ fontSize: 20, color: 'var(--brand)' }} />
          <span style={{ fontSize: 18, fontWeight: 600 }}>{dept.name}</span>
          {!isEnabled && <StatusPill badge={toneBadge('danger', '已停用')} />}
          {isRoot && <CountChip>根节点</CountChip>}
          <CountChip>{dept.memberCount ?? 0} 人</CountChip>
          {!isRoot && leaderCount > 0 && <CountChip>{leaderCount} 位负责人</CountChip>}
          <div style={{ flex: 1 }} />
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
            <PermissionGate permission="org.dept.update">
              <ReadonlyButton size="small" icon={<EditOutlined />} onClick={onEdit}>
                编辑
              </ReadonlyButton>
            </PermissionGate>
            <PermissionGate permission="org.dept.create">
              <ReadonlyButton size="small" icon={<PlusOutlined />} onClick={onCreateChild}>
                新增子部门
              </ReadonlyButton>
            </PermissionGate>
            {!isRoot && (
              <PermissionGate permission="org.dept.set_leader">
                <ReadonlyButton
                  size="small"
                  icon={<SolutionOutlined />}
                  onClick={() => setLeadersOpen(true)}
                >
                  部门负责人
                </ReadonlyButton>
              </PermissionGate>
            )}
            {!isRoot && (
              <PermissionGate permission="org.dept.update">
                <Popconfirm
                  title={isEnabled ? '确认停用此部门？' : '确认启用此部门？'}
                  onConfirm={() => onToggleStatus(!isEnabled)}
                  okText="确认"
                  cancelText="取消"
                >
                  <ReadonlyButton size="small" icon={<UsergroupAddOutlined />}>
                    {isEnabled ? '停用' : '启用'}
                  </ReadonlyButton>
                </Popconfirm>
              </PermissionGate>
            )}
            {!isRoot && (
              <PermissionGate permission="org.dept.delete">
                <Popconfirm
                  title="确认删除此部门？"
                  description="该部门下必须无任何成员才能删除"
                  onConfirm={onDelete}
                  okText="确认删除"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                >
                  <ReadonlyButton size="small" danger icon={<DeleteOutlined />}>
                    删除
                  </ReadonlyButton>
                </Popconfirm>
              </PermissionGate>
            )}
          </div>
        </div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 24,
            fontSize: 13,
            color: 'var(--text-secondary)',
            marginTop: 8,
          }}
        >
          {!isRoot && (
            <span>
              上级部门：<b style={{ color: 'var(--text)' }}>{parentName ?? '—'}</b>
            </span>
          )}
          {!isRoot && (
            <span>
              部门负责人：<b style={{ color: 'var(--text)' }}>{leaderLabel}</b>
            </span>
          )}
          <span>
            排序：<b style={{ color: 'var(--text)' }}>{dept.sortOrder ?? 0}</b>
          </span>
        </div>
      </div>

      {/* 成员列表（真实数据） */}
      <MemberSection dept={dept} treeData={treeData} onSelectMember={onSelectMember} />

      <DeptLeadersModal
        open={leadersOpen}
        deptId={String(dept.id)}
        deptName={dept.name ?? ''}
        onClose={() => setLeadersOpen(false)}
      />
    </div>
  );
}
