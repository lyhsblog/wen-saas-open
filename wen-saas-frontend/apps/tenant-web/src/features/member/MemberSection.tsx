import { useMemo, useState } from 'react';
import { App, Button, Checkbox, ConfigProvider, Input, Select, Space, Tooltip } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { Key } from 'react';
import type { DeptView } from '@wen-saas/domain';
import type { MemberListItem } from '@wen-saas/api-client';
import { describeApiError } from '@wen-saas/api-client';
import { CountChip, DataTable, formatDateTime } from '@wen-saas/shared-ui';
import { PermissionGate, ReadonlyButton } from '@wen-saas/permissions';
import { ImportEntryButton } from '../taskCenter/ImportEntryButton';
import { ExportEntryButton } from '../taskCenter/ExportEntryButton';
import { useMemberList, useSetMemberStatus } from './memberQueries';
import { useMemberActions, type MemberActionTarget } from './memberActions';
import { MemberAvatar } from './MemberAvatar';
import { MemberStatusPills } from './memberStatus';

type Props = {
  /** null = 全部成员模式：不按部门过滤，查全租户成员（含未归属部门的成员）。 */
  dept: DeptView | null;
  treeData: DeptView[];
  onSelectMember: (memberId: string) => void;
};

const toTarget = (r: MemberListItem): MemberActionTarget => ({
  id: r.id,
  status: r.status,
  username: r.username,
  realName: r.realName,
  owner: r.owner,
  leaderDeptCount: r.leaderDeptCount,
});

export function MemberSection({ dept, treeData, onSelectMember }: Props) {
  const { message, modal } = App.useApp();
  const deptId = dept ? String(dept.id) : undefined;

  const [includeSub, setIncludeSub] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<number | undefined>(undefined);
  const [roleId, setRoleId] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(1);
  const [selectedKeys, setSelectedKeys] = useState<Key[]>([]);

  const actions = useMemberActions(treeData);
  const setStatusMutation = useSetMemberStatus();

  const listQuery = useMemberList({
    // deptId 缺省 = 后端不加部门约束，查全租户成员
    ...(deptId ? { deptId, includeSubDepts: includeSub } : {}),
    ...(keyword ? { keyword } : {}),
    ...(status != null ? { status } : {}),
    ...(roleId ? { roleId } : {}),
    page,
    pageSize: 10,
  });
  const data = listQuery.data;

  const resetPage = () => {
    setPage(1);
    setSelectedKeys([]);
  };

  // 批量停用 / 启用（仅可逆操作；跳过主管理员与已是目标状态者）。
  const batchSetStatus = (enable: boolean) => {
    const targets = (data?.items ?? []).filter(
      (r) => selectedKeys.includes(r.id) && !r.owner && (r.status === 1) !== enable,
    );
    if (!targets.length) {
      message.info('所选成员无需变更');
      return;
    }
    modal.confirm({
      title: enable
        ? `确认启用所选 ${targets.length} 名成员？`
        : `确认停用所选 ${targets.length} 名成员？`,
      content: enable
        ? '启用后可正常登录，原有角色与部门负责人身份立即恢复。'
        : '停用后将立即被强制下线、无法登录；角色、部门、数据均保留，可随时恢复启用。',
      okText: enable ? '确认启用' : '确认停用',
      cancelText: '取消',
      okButtonProps: enable ? {} : { danger: true },
      onOk: async () => {
        try {
          await Promise.all(
            targets.map((t) => setStatusMutation.mutateAsync({ userId: t.id, enabled: enable })),
          );
          message.success(enable ? '已批量启用' : '已批量停用');
          setSelectedKeys([]);
        } catch (err) {
          message.error(describeApiError(err, '批量操作失败'));
        }
      },
    });
  };

  const columns = useMemo(
    () => [
      {
        title: '姓名',
        key: 'name',
        width: 150,
        render: (_: unknown, r: MemberListItem) => (
          <Space size={8}>
            <MemberAvatar name={r.realName || r.username} size={24} />
            <span
              role="button"
              tabIndex={0}
              onClick={() => onSelectMember(r.id)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') onSelectMember(r.id);
              }}
              style={{ fontWeight: 500, color: 'var(--text)', cursor: 'pointer' }}
            >
              {r.realName || r.username}
            </span>
            {r.owner && <CountChip tone="brand">主</CountChip>}
          </Space>
        ),
      },
      {
        title: '账号',
        dataIndex: 'username',
        width: 110,
        render: (v: string) => <code style={{ color: 'var(--text-tertiary)' }}>{v}</code>,
      },
      {
        title: '岗位',
        dataIndex: 'positionName',
        width: 88,
        render: (v: string | null) => v || <span style={{ color: 'var(--text-disabled)' }}>—</span>,
      },
      {
        title: '主部门',
        key: 'dept',
        width: 150,
        render: (_: unknown, r: MemberListItem) => (
          <span>
            {r.mainDeptName || '—'}
            {r.secondaryDeptCount > 0 && <CountChip>兼 {r.secondaryDeptCount}</CountChip>}
          </span>
        ),
      },
      {
        title: '角色',
        dataIndex: 'roleNames',
        width: 140,
        render: (v: string[]) =>
          v.length ? (
            <Space size={4} wrap>
              {v.slice(0, 2).map((r) => (
                <CountChip key={r}>{r}</CountChip>
              ))}
              {v.length > 2 && (
                <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>+{v.length - 2}</span>
              )}
            </Space>
          ) : (
            <span style={{ color: 'var(--text-disabled)' }}>—</span>
          ),
      },
      {
        title: '手机号',
        dataIndex: 'mobile',
        width: 116,
        render: (v: string | null) =>
          v ? <code style={{ color: 'var(--text-tertiary)' }}>{v}</code> : '—',
      },
      {
        title: '状态',
        key: 'status',
        width: 110,
        render: (_: unknown, r: MemberListItem) => (
          <MemberStatusPills
            status={r.status}
            loginLocked={r.loginLocked}
            leaderDeptCount={r.leaderDeptCount}
          />
        ),
      },
      {
        title: '最后登录',
        dataIndex: 'lastLoginAt',
        width: 140,
        render: (v: string | null) =>
          v ? (
            <span style={{ color: 'var(--text-tertiary)' }}>{formatDateTime(v)}</span>
          ) : (
            <span style={{ color: 'var(--text-disabled)' }}>从未登录</span>
          ),
      },
      {
        title: '操作',
        key: 'actions',
        width: 150,
        render: (_: unknown, r: MemberListItem) => (
          <Space size={4}>
            <PermissionGate permission="org.member.update">
              <ReadonlyButton type="link" size="small" onClick={() => void actions.openEdit(r.id)}>
                编辑
              </ReadonlyButton>
            </PermissionGate>
            <PermissionGate permission="org.member.reset_password">
              <ReadonlyButton type="link" size="small" onClick={() => void actions.resetLink(r.id)}>
                重置链接
              </ReadonlyButton>
            </PermissionGate>
            {r.loginLocked && (
              <PermissionGate permission="org.member.reset_password">
                <ReadonlyButton type="link" size="small" onClick={() => void actions.unlock(r.id)}>
                  解除锁定
                </ReadonlyButton>
              </PermissionGate>
            )}
            <PermissionGate permission="org.member.toggle_status">
              {r.owner ? (
                <Tooltip title="主管理员不可停用">
                  <Button type="link" size="small" disabled>
                    {r.status === 1 ? '停用' : '启用'}
                  </Button>
                </Tooltip>
              ) : (
                <ReadonlyButton
                  type="link"
                  size="small"
                  onClick={() => actions.confirmToggle(toTarget(r))}
                >
                  {r.status === 1 ? '停用' : '启用'}
                </ReadonlyButton>
              )}
            </PermissionGate>
          </Space>
        ),
      },
    ],
    [actions, onSelectMember],
  );

  const hasSelection = selectedKeys.length > 0;
  const total = data?.total ?? 0;

  return (
    <div
      style={{
        flex: 1,
        minHeight: 0,
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {/* 头部：筛选条与主操作同处一行（向 UI 原型看齐，去掉额外标题带） */}
      <div
        style={{
          padding: '12px 24px',
          background: 'var(--bg-elev)',
          borderBottom: '1px solid var(--divider)',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          flexWrap: 'wrap',
        }}
      >
        <Input.Search
          allowClear
          placeholder="姓名 / 账号 / 手机 / 邮箱"
          style={{ width: 220 }}
          onSearch={(v) => {
            setKeyword(v.trim());
            resetPage();
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 100 }}
          value={status}
          onChange={(v) => {
            setStatus(v);
            resetPage();
          }}
          options={[
            { value: 1, label: '在职' },
            { value: 0, label: '停用' },
          ]}
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="角色"
          style={{ width: 150 }}
          value={roleId}
          onChange={(v) => {
            setRoleId(v);
            resetPage();
          }}
          options={actions.roleOptions.map((r) => ({ value: r.id, label: r.name }))}
        />
        {dept != null && (
          <Checkbox
            checked={includeSub}
            onChange={(e) => {
              setIncludeSub(e.target.checked);
              resetPage();
            }}
          >
            含子部门
          </Checkbox>
        )}
        <div style={{ flex: 1 }} />
        {hasSelection && (
          <PermissionGate permission="org.member.toggle_status">
            <Space style={{ marginInlineEnd: 8 }}>
              <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                已选 {selectedKeys.length}
              </span>
              <ReadonlyButton size="small" onClick={() => batchSetStatus(true)}>
                批量启用
              </ReadonlyButton>
              <ReadonlyButton size="small" danger onClick={() => batchSetStatus(false)}>
                批量停用
              </ReadonlyButton>
            </Space>
          </PermissionGate>
        )}
        <PermissionGate permission="org.member.list">
          <ExportEntryButton
            objectCode="member"
            filters={{
              ...(deptId ? { deptId, includeSubDepts: String(includeSub) } : {}),
              ...(keyword ? { keyword } : {}),
              ...(status != null ? { status: String(status) } : {}),
              ...(roleId ? { roleId } : {}),
            }}
            filterSummary={
              dept
                ? `部门=${dept.name}${includeSub ? '（含子部门）' : ''}${
                    keyword ? ` · 关键词=${keyword}` : ''
                  }`
                : `范围=全部成员${keyword ? ` · 关键词=${keyword}` : ''}`
            }
          />
        </PermissionGate>
        <PermissionGate permission="org.member.create">
          <ImportEntryButton entityCode="sys.member" onImported={() => void listQuery.refetch()} />
        </PermissionGate>
        <PermissionGate permission="org.member.create">
          {dept == null && treeData.length === 0 ? (
            // 后端要求成员必须归属主部门，无部门时无法创建
            <Tooltip title="成员需归属主部门，请先创建部门">
              <Button type="primary" size="small" icon={<PlusOutlined />} disabled>
                添加成员
              </Button>
            </Tooltip>
          ) : (
            <ReadonlyButton
              type="primary"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => actions.openCreate(deptId ?? '')}
            >
              添加成员
            </ReadonlyButton>
          )}
        </PermissionGate>
      </div>

      {/* 局部主题：只作用于本表，向 UI 原型靠拢（表头浅灰底 + 紧凑行高 + 字号 13），不影响全局 antd */}
      <ConfigProvider
        theme={{
          token: { fontSize: 13 },
          components: {
            Table: {
              headerBg: 'var(--bg-muted)',
              headerColor: 'var(--text-secondary)',
              headerSplitColor: 'transparent',
              borderColor: 'var(--divider)',
              rowHoverBg: 'var(--bg-muted)',
              rowSelectedBg: 'var(--brand-bg)',
              rowSelectedHoverBg: 'var(--brand-bg)',
              cellPaddingBlockSM: 10,
            },
          },
        }}
      >
        <div style={{ flex: 1, minHeight: 0 }}>
          <DataTable<MemberListItem>
            columns={columns}
            dataSource={data?.items ?? []}
            rowKey="id"
            density="compact"
            loading={listQuery.isPending}
            rowSelection={{
              selectedRowKeys: selectedKeys,
              onChange: (keys) => setSelectedKeys(keys),
              getCheckboxProps: (r) => ({ disabled: r.owner }),
            }}
            pagination={{
              current: page,
              pageSize: 10,
              total,
              onChange: setPage,
              showSizeChanger: false,
              showTotal: (n) => `共 ${n} 人`,
            }}
            empty={{ description: dept ? '该部门暂无成员' : '暂无成员' }}
          />
        </div>
      </ConfigProvider>
      {actions.modals}
    </div>
  );
}
