import { useState } from 'react';
import { App, Button, Descriptions, Empty, Space, Spin, Table, Tabs, Tooltip } from 'antd';
import { ArrowLeftOutlined, EyeOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/lib/table';
import type { DeptView } from '@wen-saas/domain';
import type { ContactReveal, MemberLoginLog } from '@wen-saas/api-client';
import { describeApiError } from '@wen-saas/api-client';
import { CountChip, formatDateTime, StatusPill } from '@wen-saas/shared-ui';
import { PermissionGate, ReadonlyButton, useHasPermission } from '@wen-saas/permissions';
import { useOperationLogs } from '../audit/auditQueries';
import type { OperationLogItem } from '../audit/auditApi';
import { memberApi } from './memberApi';
import { MemberStatusPills } from './memberStatus';
import { useMemberDetail, useMemberLoginLogs, useMemberOAuthBindings } from './memberQueries';
import { useMemberActions, type MemberActionTarget } from './memberActions';
import { MemberAvatar } from './MemberAvatar';

const LOGIN_METHOD: Record<string, string> = {
  password: '密码',
  sms: '短信验证码',
  wechat: '微信',
  wecom: '企业微信',
  dingtalk: '钉钉',
};
const LOGIN_ACTION: Record<string, string> = {
  login: '登录',
  logout: '退出',
  session_expired: '会话过期',
};
const FAIL_REASON: Record<string, string> = {
  bad_credentials: '账号或密码错误',
  disabled: '账号已停用',
  locked: '账号已锁定',
  oauth_failed: '第三方校验失败',
  other: '其他',
};
const OAUTH_PROVIDER: Record<string, string> = {
  dingtalk: '钉钉',
  wechat: '微信',
  wecom: '企业微信',
};

function genderText(g: number | null | undefined): string {
  if (g === 1) return '男';
  if (g === 2) return '女';
  return '—';
}

type Props = {
  memberId: string;
  treeData: DeptView[];
  onBack: () => void;
};

export function MemberDetailPane({ memberId, treeData, onBack }: Props) {
  const { message } = App.useApp();
  const [tab, setTab] = useState('basic');
  const [reveal, setReveal] = useState<ContactReveal | null>(null);

  const detailQuery = useMemberDetail(memberId);
  const m = detailQuery.data;
  const actions = useMemberActions(treeData);
  const canAudit = useHasPermission('audit.view');
  const canReveal = useHasPermission('org.member.contact.reveal');

  if (detailQuery.isPending) {
    return (
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Spin />
      </div>
    );
  }
  if (!m) {
    return (
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        <BackBar onBack={onBack} />
        <Empty description="成员不存在或已被移除" style={{ marginTop: 120 }} />
      </div>
    );
  }

  const toggleTarget: MemberActionTarget = {
    id: m.id,
    status: m.status,
    username: m.username,
    realName: m.realName,
    owner: m.owner,
    leaderDeptCount: m.leaderDepts.length,
  };

  const doReveal = async () => {
    try {
      setReveal(await memberApi.revealContact(m.id));
    } catch (err) {
      message.error(describeApiError(err, '查看联系方式失败'));
    }
  };

  const tabItems = [
    {
      key: 'basic',
      label: '基本信息',
      children: (
        <Descriptions column={2} size="small" bordered styles={{ label: { width: 110 } }}>
          <Descriptions.Item label="姓名">{m.realName || '—'}</Descriptions.Item>
          <Descriptions.Item label="账号">
            <code>{m.username}</code>
          </Descriptions.Item>
          <Descriptions.Item label="岗位">{m.positionName || '—'}</Descriptions.Item>
          <Descriptions.Item label="工号">{m.empNo || '—'}</Descriptions.Item>
          <Descriptions.Item label="性别">{genderText(m.gender)}</Descriptions.Item>
          <Descriptions.Item label="生日">{m.birthday || '—'}</Descriptions.Item>
          <Descriptions.Item label="手机号">
            <Space size={6}>
              <span>{reveal?.mobile ?? m.mobile ?? '—'}</span>
              {canReveal && !reveal && (m.mobile || m.email) && (
                <Button
                  type="link"
                  size="small"
                  icon={<EyeOutlined />}
                  onClick={() => void doReveal()}
                >
                  查看明文
                </Button>
              )}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="邮箱">{reveal?.email ?? m.email ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{formatDateTime(m.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{formatDateTime(m.updatedAt)}</Descriptions.Item>
        </Descriptions>
      ),
    },
    {
      key: 'depts',
      label: `所属部门 (${1 + m.secondaryDepts.length})`,
      children: (
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <DeptBlock title="主部门">
            {m.mainDept ? <CountChip>{m.mainDept.deptName || m.mainDept.deptId}</CountChip> : '—'}
          </DeptBlock>
          <DeptBlock title="兼任部门">
            {m.secondaryDepts.length
              ? m.secondaryDepts.map((d) => (
                  <CountChip key={d.deptId}>{d.deptName || d.deptId}</CountChip>
                ))
              : '—'}
          </DeptBlock>
          <DeptBlock title="负责的部门">
            {m.leaderDepts.length
              ? m.leaderDepts.map((d) => (
                  <CountChip key={d.deptId}>{d.deptName || d.deptId}</CountChip>
                ))
              : '—'}
          </DeptBlock>
        </Space>
      ),
    },
    {
      key: 'roles',
      label: `角色与权限 (${m.roles.length})`,
      children: m.roles.length ? (
        <Space wrap>
          {m.roles.map((r) => (
            <CountChip key={r.roleId}>{r.name}</CountChip>
          ))}
        </Space>
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未分配角色" />
      ),
    },
    {
      key: 'login',
      label: '登录信息',
      children: <LoginTab memberId={m.id} active={tab === 'login'} loginLocked={m.loginLocked} />,
    },
  ];

  if (canAudit) {
    tabItems.push({
      key: 'audit',
      label: '操作日志',
      children: <AuditTab operatorId={m.id} />,
    });
  }

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <BackBar onBack={onBack} />
      {/* 成员信息条 */}
      <div style={{ padding: '12px 24px 0' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <MemberAvatar name={m.realName || m.username} size={48} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 18, fontWeight: 600 }}>{m.realName || m.username}</span>
              <MemberStatusPills
                status={m.status}
                loginLocked={m.loginLocked}
                leaderDeptCount={m.leaderDepts.length}
              />
              {m.owner && <CountChip tone="brand">主管理员</CountChip>}
            </div>
            <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 4 }}>
              {[m.positionName, m.mainDept?.deptName, `账号 ${m.username}`]
                .filter(Boolean)
                .join(' · ')}
            </div>
          </div>
          <Space>
            <PermissionGate permission="org.member.update">
              <ReadonlyButton size="small" onClick={() => void actions.openEdit(m.id)}>
                编辑
              </ReadonlyButton>
            </PermissionGate>
            <PermissionGate permission="org.member.reset_password">
              <ReadonlyButton size="small" onClick={() => void actions.resetLink(m.id)}>
                重置链接
              </ReadonlyButton>
            </PermissionGate>
            {m.loginLocked && (
              <PermissionGate permission="org.member.reset_password">
                <ReadonlyButton size="small" onClick={() => void actions.unlock(m.id)}>
                  解除锁定
                </ReadonlyButton>
              </PermissionGate>
            )}
            <PermissionGate permission="org.member.toggle_status">
              {m.owner ? (
                <Tooltip title="主管理员不可停用">
                  <Button size="small" disabled>
                    {m.status === 1 ? '停用' : '启用'}
                  </Button>
                </Tooltip>
              ) : (
                <ReadonlyButton
                  size="small"
                  danger={m.status === 1}
                  onClick={() => actions.confirmToggle(toggleTarget)}
                >
                  {m.status === 1 ? '停用' : '启用'}
                </ReadonlyButton>
              )}
            </PermissionGate>
          </Space>
        </div>
      </div>
      <div style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: '0 24px 24px' }}>
        <Tabs activeKey={tab} onChange={setTab} items={tabItems} size="small" />
      </div>
      {actions.modals}
    </div>
  );
}

function BackBar({ onBack }: { onBack: () => void }) {
  return (
    <div style={{ padding: '12px 24px 0' }}>
      <Button
        type="link"
        size="small"
        icon={<ArrowLeftOutlined />}
        onClick={onBack}
        style={{ paddingLeft: 0 }}
      >
        返回成员列表
      </Button>
    </div>
  );
}

function DeptBlock({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 6 }}>{title}</div>
      <Space wrap>{children}</Space>
    </div>
  );
}

function LoginTab({
  memberId,
  active,
  loginLocked,
}: {
  memberId: string;
  active: boolean;
  loginLocked: boolean;
}) {
  const oauthQuery = useMemberOAuthBindings(memberId, active);
  const logsQuery = useMemberLoginLogs(memberId, 20, active);

  const columns: ColumnsType<MemberLoginLog> = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 160,
      render: (v: string) => formatDateTime(v),
    },
    {
      title: '方式',
      dataIndex: 'loginMethod',
      width: 100,
      render: (v: string | null) => (v ? (LOGIN_METHOD[v] ?? v) : '—'),
    },
    {
      title: '类型',
      dataIndex: 'action',
      width: 90,
      render: (v: string | null) => (v ? (LOGIN_ACTION[v] ?? v) : '—'),
    },
    {
      title: '结果',
      dataIndex: 'result',
      width: 110,
      render: (_: unknown, r: MemberLoginLog) =>
        r.result === 'success' ? (
          <StatusPill badge={{ kind: 'success', text: '成功', color: '' }} />
        ) : (
          <Tooltip title={r.failReason ? (FAIL_REASON[r.failReason] ?? r.failReason) : undefined}>
            <span>
              <StatusPill badge={{ kind: 'danger', text: '失败', color: '' }} />
            </span>
          </Tooltip>
        ),
    },
    { title: 'IP', dataIndex: 'ip', width: 130, render: (v: string | null) => v || '—' },
    {
      title: '设备',
      dataIndex: 'userAgent',
      ellipsis: true,
      render: (v: string | null) => v || '—',
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Descriptions column={2} size="small">
        <Descriptions.Item label="账号锁定">
          <StatusPill
            badge={{
              kind: loginLocked ? 'warning' : 'neutral',
              text: loginLocked ? '已锁定' : '正常',
              color: '',
            }}
          />
        </Descriptions.Item>
      </Descriptions>

      <div>
        <div style={{ fontWeight: 600, marginBottom: 8 }}>第三方账号绑定</div>
        {oauthQuery.isPending ? (
          <Spin size="small" />
        ) : oauthQuery.data?.length ? (
          <Space direction="vertical" style={{ width: '100%' }}>
            {oauthQuery.data.map((b) => (
              <Space key={b.provider} size={8}>
                <CountChip>{OAUTH_PROVIDER[b.provider] ?? b.provider}</CountChip>
                <span>{b.nickname || '—'}</span>
                <span style={{ color: 'var(--text-secondary)', fontSize: 12 }}>
                  绑定于 {formatDateTime(b.boundAt)}
                </span>
              </Space>
            ))}
          </Space>
        ) : (
          <span style={{ color: 'var(--text-secondary)' }}>未绑定第三方账号</span>
        )}
      </div>

      <div>
        <div style={{ fontWeight: 600, marginBottom: 8 }}>最近登录记录</div>
        <Table<MemberLoginLog>
          size="small"
          rowKey={(r) => `${r.createdAt}-${r.action}-${r.result}`}
          columns={columns}
          dataSource={logsQuery.data ?? []}
          loading={logsQuery.isPending}
          pagination={false}
          locale={{ emptyText: '近 180 天无登录记录' }}
        />
      </div>
    </Space>
  );
}

function AuditTab({ operatorId }: { operatorId: string }) {
  // antd Tabs 惰性挂载非激活面板：本组件仅在「操作日志」Tab 首次点开时才挂载并发起查询。
  const [page, setPage] = useState(1);
  const query = useOperationLogs('tenant', { operatorId, page, size: 20 });

  const columns: ColumnsType<OperationLogItem> = [
    { title: '时间', dataIndex: 'createdAt', width: 160, render: (v: string) => formatDateTime(v) },
    { title: '操作', dataIndex: 'action', width: 140, render: (v: string | null) => v || '—' },
    {
      title: '对象',
      key: 'object',
      ellipsis: true,
      render: (_: unknown, r: OperationLogItem) => r.objectDesc ?? r.objectId ?? '—',
    },
    { title: '模块', dataIndex: 'module', width: 100, render: (v: string | null) => v || '—' },
    { title: 'IP', dataIndex: 'ip', width: 130, render: (v: string | null) => v || '—' },
  ];

  return (
    <Table<OperationLogItem>
      size="small"
      rowKey="id"
      columns={columns}
      dataSource={query.data?.records ?? []}
      loading={query.isPending}
      pagination={{
        current: page,
        pageSize: 20,
        total: query.data?.total ?? 0,
        onChange: setPage,
        showSizeChanger: false,
      }}
      locale={{ emptyText: '暂无操作记录' }}
    />
  );
}
