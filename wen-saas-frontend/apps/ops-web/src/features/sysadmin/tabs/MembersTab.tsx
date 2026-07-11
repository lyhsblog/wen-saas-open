import { useState } from 'react';
import { Alert, App, Button, Input, Popconfirm, Select, Space, Table, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { EyeOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@wen-saas/auth';
import {
  describeApiError,
  opsTeamMemberApi,
  type OpsMemberContactReveal,
  type OpsMemberListItem,
  type OpsMemberStatus,
} from '@wen-saas/api-client';
import { CountChip, formatDateTime, StatusPill } from '@wen-saas/shared-ui';
import { http } from '../../../lib/http';
import { MEMBER_STATUS_OPTIONS, memberStatusBadge } from '../utils/sysadminMeta';
import { useToggleMemberStatus } from '../hooks/useMemberMutations';
import { MemberFormModal } from '../modals/MemberFormModal';
import { MemberResetPasswordModal } from '../modals/MemberResetPasswordModal';
import { MemberOAuthModal } from '../modals/MemberOAuthModal';

export function MembersTab() {
  const { message } = App.useApp();
  const currentUserId = useAuthStore((s) => s.user?.id ?? null);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<OpsMemberStatus | undefined>(undefined);
  const [createOpen, setCreateOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [resetMember, setResetMember] = useState<OpsMemberListItem | null>(null);
  const [oauthMember, setOauthMember] = useState<OpsMemberListItem | null>(null);
  // 列表默认脱敏；按需解密单行明文（后端 @RequirePermission(ops.team.member.contact.reveal) 门控 + 记审计）。
  // 本 Tab 仅 ops.team.member.list 持有者（超管）可见，故前端不再重复门控，由后端权威判定（无权 → 403 提示）。
  const [revealed, setRevealed] = useState<Record<string, OpsMemberContactReveal>>({});
  const [revealing, setRevealing] = useState<string | null>(null);

  const onReveal = async (id: string) => {
    setRevealing(id);
    try {
      const c = await opsTeamMemberApi(http).revealContact(id);
      setRevealed((m) => ({ ...m, [id]: c }));
    } catch (err) {
      void message.error(describeApiError(err, '获取完整联系方式失败'));
    } finally {
      setRevealing(null);
    }
  };

  const query = {
    ...(keyword ? { keyword } : {}),
    ...(status != null ? { status } : {}),
  };
  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'team-members', query],
    queryFn: () => opsTeamMemberApi(http).listMembers(query),
  });

  const { data: editingDetail } = useQuery({
    queryKey: ['ops', 'team-member', editingId],
    queryFn: () => opsTeamMemberApi(http).getMember(editingId as string),
    enabled: editingId != null,
  });

  const toggle = useToggleMemberStatus();

  // 掩码值旁的小眼睛：后端 /contact 一次返回手机+邮箱明文，点任一字段图标整行揭示。
  const renderContact = (
    masked: string | null,
    row: OpsMemberListItem,
    field: 'mobile' | 'email',
  ) => {
    const plain = revealed[row.id];
    if (plain) return plain[field] ?? masked ?? '—';
    if (masked == null) return '—';
    return (
      <Space size={2}>
        <span>{masked}</span>
        <Tooltip title="查看完整">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            aria-label="查看完整"
            loading={revealing === row.id}
            onClick={() => void onReveal(row.id)}
          />
        </Tooltip>
      </Space>
    );
  };

  const columns: ColumnsType<OpsMemberListItem> = [
    {
      title: '姓名',
      dataIndex: 'realName',
      width: 200,
      render: (realName: string, row) => (
        <Space size={6}>
          <Tooltip title={realName}>
            <span
              style={{
                fontWeight: 500,
                maxWidth: 140,
                display: 'inline-block',
                verticalAlign: 'bottom',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {realName}
            </span>
          </Tooltip>
          {row.roleCodes.includes('OPS_SUPER_ADMIN') && <CountChip tone="brand">超管</CountChip>}
        </Space>
      ),
    },
    { title: '账号', dataIndex: 'username' },
    {
      title: '角色',
      dataIndex: 'roleCodes',
      render: (codes: string[]) =>
        codes.length === 0 ? (
          <span style={{ color: 'var(--text-tertiary)' }}>—</span>
        ) : (
          <Space size={4} wrap>
            {codes.map((c) => (
              <CountChip key={c}>{c}</CountChip>
            ))}
          </Space>
        ),
    },
    {
      title: '手机',
      dataIndex: 'mobile',
      render: (v: string | null, row) => renderContact(v, row, 'mobile'),
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      render: (v: string | null, row) => renderContact(v, row, 'email'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (s: OpsMemberStatus) => <StatusPill badge={memberStatusBadge(s)} />,
    },
    {
      title: '最后登录',
      dataIndex: 'lastLoginAt',
      width: 170,
      render: (v: string | null) =>
        v ? formatDateTime(v) : <span style={{ color: 'var(--text-tertiary)' }}>从未</span>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 280,
      render: (_v, row) => {
        const isSelf = currentUserId != null && row.id === currentUserId;
        const enabled = row.status === 1;
        return (
          <Space size={0}>
            <Button type="link" size="small" onClick={() => setEditingId(row.id)}>
              编辑
            </Button>
            <Button type="link" size="small" onClick={() => setResetMember(row)}>
              重置密码
            </Button>
            <Button type="link" size="small" onClick={() => setOauthMember(row)}>
              第三方账号
            </Button>
            {enabled ? (
              <Tooltip title={isSelf ? '不能停用自己' : undefined}>
                <Popconfirm
                  title={`确认停用「${row.realName}」？`}
                  okText="停用"
                  disabled={isSelf}
                  onConfirm={() => toggle.mutate({ userId: row.id, enable: false })}
                >
                  <Button type="link" size="small" disabled={isSelf}>
                    停用
                  </Button>
                </Popconfirm>
              </Tooltip>
            ) : (
              <Button
                type="link"
                size="small"
                loading={toggle.isPending && toggle.variables?.userId === row.id}
                onClick={() => toggle.mutate({ userId: row.id, enable: true })}
              >
                启用
              </Button>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <div style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        <Input.Search
          allowClear
          placeholder="搜索姓名 / 账号 / 手机 / 邮箱"
          style={{ width: 280 }}
          onSearch={setKeyword}
        />
        <Select
          allowClear
          placeholder="全部状态"
          style={{ width: 130 }}
          options={MEMBER_STATUS_OPTIONS}
          value={status ?? null}
          onChange={(v) => setStatus(v ?? undefined)}
        />
        <div style={{ flex: 1 }} />
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
          添加成员
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
        message="运营团队无组织架构，简化为一个列表。新成员不发邀请通知，由超管线下传递初始密码。"
      />

      <MemberFormModal
        open={createOpen || (editingId != null && editingDetail != null)}
        editing={editingId != null ? (editingDetail ?? null) : null}
        onClose={() => {
          setCreateOpen(false);
          setEditingId(null);
        }}
      />
      {resetMember && (
        <MemberResetPasswordModal open member={resetMember} onClose={() => setResetMember(null)} />
      )}
      {oauthMember && (
        <MemberOAuthModal open member={oauthMember} onClose={() => setOauthMember(null)} />
      )}
    </div>
  );
}
