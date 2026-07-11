import { Button, Descriptions, Empty, Spin, Table, Tabs } from 'antd';
import { AppstoreOutlined, CloseOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { opsBusinessModuleApi } from '@wen-saas/api-client';
import type {
  BusinessModuleDetail,
  ModuleContribution,
  RelatedPackageItem,
} from '@wen-saas/api-client';
import { CountChip, ErrorState, StatusPill } from '@wen-saas/shared-ui';
import { http } from '../../lib/http';
import { packageStatusBadge } from '../packages/utils/statusMeta';
import { fmtDateTime } from './utils/statusMeta';

type Props = {
  code: string;
  onClose: () => void;
};

export function OpsModuleDetailPanel({ code, onClose }: Props) {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['ops', 'module', code],
    queryFn: () => opsBusinessModuleApi(http).getModule(code),
  });

  return (
    <div
      style={{
        flex: 1,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--bg)',
        overflow: 'hidden',
      }}
    >
      {isLoading ? (
        <div style={{ padding: 80, textAlign: 'center' }}>
          <Spin size="large" />
        </div>
      ) : isError || !data ? (
        <ErrorState
          title="加载模块详情失败"
          description={error instanceof Error ? error.message : undefined}
          primaryAction={{ label: '重试', onClick: () => void refetch() }}
        />
      ) : (
        <>
          {/* Header */}
          <div
            style={{
              padding: '16px 24px',
              borderBottom: '1px solid var(--divider)',
              display: 'flex',
              alignItems: 'flex-start',
              gap: 12,
              flexShrink: 0,
            }}
          >
            <AppstoreOutlined style={{ fontSize: 22, color: 'var(--brand)', marginTop: 2 }} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                <span style={{ fontWeight: 600, fontSize: 18 }}>{data.name}</span>
                <CountChip>
                  <span style={{ fontFamily: 'var(--font-mono)' }}>v{data.version}</span>
                </CountChip>
              </div>
              <div
                style={{
                  fontSize: 12,
                  color: 'var(--text-tertiary)',
                  display: 'flex',
                  gap: 12,
                  flexWrap: 'wrap',
                }}
              >
                <code style={{ fontFamily: 'var(--font-mono)' }}>{data.code}</code>
                <span>注册 {fmtDateTime(data.registeredAt)}</span>
              </div>
              {data.description && (
                <div style={{ marginTop: 8, color: 'var(--text-secondary)', fontSize: 13 }}>
                  {data.description}
                </div>
              )}
            </div>
            <Button type="text" icon={<CloseOutlined />} onClick={onClose} aria-label="关闭" />
          </div>

          {/* 统计卡 */}
          <div
            style={{
              padding: '16px 24px',
              display: 'grid',
              gridTemplateColumns: 'repeat(3, 1fr)',
              gap: 12,
              flexShrink: 0,
            }}
          >
            <StatCard label="贡献扩展点" value={`${totalContribution(data.contribution)} 项`} />
            <StatCard label="依赖模块" value={`${data.dependencies.length} 个`} />
            <StatCard label="关联套餐" value={`${data.relatedPackages.length} 个`} />
          </div>

          {/* Tabs */}
          <div style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: '0 24px 24px' }}>
            <Tabs
              defaultActiveKey="basic"
              items={[
                { key: 'basic', label: '基本信息', children: <BasicTab data={data} /> },
                {
                  key: 'contribution',
                  label: '贡献能力',
                  children: <ContributionTab contribution={data.contribution} />,
                },
                {
                  key: 'packages',
                  label: `关联套餐${data.relatedPackages.length ? ` (${data.relatedPackages.length})` : ''}`,
                  children: <RelatedPackagesTab packages={data.relatedPackages} />,
                },
              ]}
            />
          </div>
        </>
      )}
    </div>
  );
}

function BasicTab({ data }: { data: BusinessModuleDetail }) {
  return (
    <Descriptions column={1} bordered size="small" style={{ marginTop: 8 }}>
      <Descriptions.Item label="编码">
        <code style={{ fontFamily: 'var(--font-mono)' }}>{data.code}</code>
      </Descriptions.Item>
      <Descriptions.Item label="名称">{data.name}</Descriptions.Item>
      <Descriptions.Item label="版本">v{data.version}</Descriptions.Item>
      <Descriptions.Item label="注册时间">{fmtDateTime(data.registeredAt)}</Descriptions.Item>
      <Descriptions.Item label="依赖模块">
        {data.dependencies.length === 0 ? (
          <span style={{ color: 'var(--text-tertiary)' }}>无</span>
        ) : (
          <span style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap' }}>
            {data.dependencies.map((d) => (
              <CountChip key={d}>
                <span style={{ fontFamily: 'var(--font-mono)' }}>{d}</span>
              </CountChip>
            ))}
          </span>
        )}
      </Descriptions.Item>
      <Descriptions.Item label="API 前缀">
        {data.apiPrefixes.length === 0 ? (
          <span style={{ color: 'var(--text-tertiary)' }}>无</span>
        ) : (
          <span style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap' }}>
            {data.apiPrefixes.map((p) => (
              <CountChip key={p}>
                <span style={{ fontFamily: 'var(--font-mono)' }}>{p}</span>
              </CountChip>
            ))}
          </span>
        )}
      </Descriptions.Item>
      <Descriptions.Item label="描述">
        {data.description || <span style={{ color: 'var(--text-tertiary)' }}>—</span>}
      </Descriptions.Item>
    </Descriptions>
  );
}

const CONTRIBUTION_FIELDS: { key: keyof ModuleContribution; label: string }[] = [
  { key: 'permissions', label: '权限点' },
  { key: 'dictGroups', label: '字典分组' },
  { key: 'dashboardCards', label: '工作台卡片' },
  { key: 'h5Pages', label: 'H5 路由' },
  { key: 'notificationTemplates', label: '通知模板' },
];

function ContributionTab({ contribution }: { contribution: ModuleContribution }) {
  return (
    <div
      style={{
        marginTop: 8,
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: 12,
      }}
    >
      {CONTRIBUTION_FIELDS.map((f) => (
        <StatCard key={f.key} label={f.label} value={`${contribution[f.key]} 个`} />
      ))}
    </div>
  );
}

function RelatedPackagesTab({ packages }: { packages: RelatedPackageItem[] }) {
  const navigate = useNavigate();
  if (packages.length === 0) {
    return (
      <Empty
        description="暂无套餐包含该模块"
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        style={{ padding: '32px 0' }}
      />
    );
  }
  return (
    <Table<RelatedPackageItem>
      style={{ marginTop: 8 }}
      size="small"
      rowKey="code"
      pagination={false}
      dataSource={packages}
      columns={[
        {
          title: '套餐名',
          dataIndex: 'name',
          render: (name: string, row) => (
            <Button
              type="link"
              style={{ padding: 0, height: 'auto' }}
              onClick={() => {
                void navigate(`/plans?selected=${encodeURIComponent(row.code)}`);
              }}
            >
              {name}
            </Button>
          ),
        },
        {
          title: '编码',
          dataIndex: 'code',
          render: (c: string) => <code style={{ fontFamily: 'var(--font-mono)' }}>{c}</code>,
        },
        {
          title: '状态',
          dataIndex: 'status',
          width: 100,
          // 与套餐列表/详情同一 badge 真源，渲染同一 StatusPill
          render: (s: string) => <StatusPill badge={packageStatusBadge(s)} />,
        },
      ]}
    />
  );
}

function totalContribution(c: ModuleContribution): number {
  return c.permissions + c.dictGroups + c.dashboardCards + c.h5Pages + c.notificationTemplates;
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-md)',
        padding: '12px 16px',
        background: 'var(--bg-elev)',
      }}
    >
      <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 18, fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </div>
    </div>
  );
}
