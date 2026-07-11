import { useState } from 'react';
import {
  CopyableText,
  formatDateTime,
  shouldSkipRowNav,
  StatusPill,
  toneBadge,
  type SemanticTone,
} from '@wen-saas/shared-ui';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  Segmented,
  Space,
  Table,
  Tabs,
  Typography,
} from 'antd';
import { CloseOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import { opsComplianceApi } from '@wen-saas/api-client';
import type {
  ExportRequestStatus,
  ExportRequestView,
  MaskRuleExample,
  SensitiveFieldRow,
} from '@wen-saas/api-client';
import { http } from '../../lib/http';

const STATUS_META: Record<ExportRequestStatus, { label: string; tone: SemanticTone }> = {
  PENDING: { label: '审批中', tone: 'warning' },
  APPROVED: { label: '已批准', tone: 'success' },
  REJECTED: { label: '已拒绝', tone: 'danger' },
};

const STATUS_FILTER = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '审批中' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'REJECTED', label: '已拒绝' },
];

const PAGE_SIZE = 20;

function fmtTime(v: string | null): string {
  return formatDateTime(v);
}

/**
 * 运营端 · 数据隐私与合规审计。两个 Tab：①跨租户导出审批审计（按运营 dataScope
 * 收敛，OWNED 仅看 csm_user_id=self 的租户）；②敏感字段加密底座自查（平台事实，全平台一致，
 * 含算法 / 密钥版本等运维信息，不在租户端暴露）。需 ops.compliance.view。
 */
export function OpsCompliancePage() {
  return (
    <div style={{ padding: 16, height: '100%', overflow: 'auto' }}>
      <Tabs
        items={[
          { key: 'audit', label: '导出审批审计', children: <ExportAuditTab /> },
          { key: 'encryption', label: '敏感字段加密', children: <EncryptionSelfCheckTab /> },
        ]}
      />
    </div>
  );
}

/** 跨租户导出审批审计：master-detail。 */
function ExportAuditTab() {
  const [status, setStatus] = useState<string>('ALL');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const query = {
    page,
    size: PAGE_SIZE,
    ...(status !== 'ALL' ? { status: status as ExportRequestStatus } : {}),
    ...(keyword ? { keyword } : {}),
  };
  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'compliance', 'list', query],
    queryFn: () => opsComplianceApi(http).list(query),
  });

  const columns: ColumnsType<ExportRequestView> = [
    { title: '租户', dataIndex: 'tenantName', width: 140, render: (v: string | null) => v ?? '—' },
    {
      title: '申请编号',
      dataIndex: 'requestNo',
      width: 160,
      render: (v: string) => <CopyableText value={v} />,
    },
    { title: '导出对象', dataIndex: 'objectType' },
    {
      title: '预估条数',
      dataIndex: 'estimatedCount',
      width: 110,
      render: (v: number) => (v > 0 ? v.toLocaleString('zh-CN') : '—'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: ExportRequestStatus) => (
        <StatusPill badge={toneBadge(STATUS_META[v].tone, STATUS_META[v].label)} />
      ),
    },
    {
      title: '申请人',
      dataIndex: 'applicantName',
      width: 110,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '申请时间',
      dataIndex: 'appliedAt',
      width: 170,
      render: (v: string | null) => fmtTime(v),
    },
  ];

  return (
    <Card
      size="small"
      title={
        <Segmented
          options={STATUS_FILTER}
          value={status}
          onChange={(v) => {
            setStatus(String(v));
            setPage(1);
          }}
        />
      }
      extra={
        <Input.Search
          allowClear
          placeholder="申请编号 / 导出对象"
          style={{ width: 240 }}
          onSearch={(v) => {
            setKeyword(v.trim());
            setPage(1);
          }}
        />
      }
    >
      <div style={{ display: 'flex', gap: 16 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <Table<ExportRequestView>
            rowKey="id"
            size="small"
            columns={columns}
            dataSource={data?.items}
            loading={isLoading}
            onRow={(row) => ({
              onClick: () => {
                if (shouldSkipRowNav()) return;
                setSelectedId(row.id);
              },
              style: { cursor: 'pointer' },
            })}
            rowClassName={(row) => (row.id === selectedId ? 'ant-table-row-selected' : '')}
            pagination={{
              current: page,
              pageSize: PAGE_SIZE,
              total: data?.total ?? 0,
              showSizeChanger: false,
              showTotal: (n) => `共 ${n} 条`,
              onChange: (p) => setPage(p),
            }}
          />
        </div>
        {selectedId && (
          <div style={{ width: 420, flexShrink: 0 }}>
            <OpsRequestDetail id={selectedId} onClose={() => setSelectedId(null)} />
          </div>
        )}
      </div>
    </Card>
  );
}

function OpsRequestDetail({ id, onClose }: { id: string; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'compliance', 'detail', id],
    queryFn: () => opsComplianceApi(http).getDetail(id),
  });

  return (
    <Card
      size="small"
      title="申请详情"
      extra={
        <Button
          type="text"
          size="small"
          icon={<CloseOutlined />}
          onClick={onClose}
          aria-label="关闭"
        />
      }
      loading={isLoading}
    >
      {!data ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="租户">{data.tenantName ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="申请编号">{data.requestNo}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <StatusPill
              badge={toneBadge(STATUS_META[data.status].tone, STATUS_META[data.status].label)}
            />
          </Descriptions.Item>
          <Descriptions.Item label="导出对象">{data.objectType}</Descriptions.Item>
          <Descriptions.Item label="筛选条件">{data.filterSummary ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="预估条数">
            {data.estimatedCount > 0 ? data.estimatedCount.toLocaleString('zh-CN') : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="申请理由">{data.reason}</Descriptions.Item>
          <Descriptions.Item label="申请人">{data.applicantName ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="申请时间">{fmtTime(data.appliedAt)}</Descriptions.Item>
          <Descriptions.Item label="审批人">{data.approverName ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="审批时间">{fmtTime(data.decidedAt)}</Descriptions.Item>
          <Descriptions.Item label="审批意见">{data.approvalComment ?? '—'}</Descriptions.Item>
        </Descriptions>
      )}
    </Card>
  );
}

/**
 * 敏感字段加密底座自查：算法 / 密钥版本 + 字段登记表 + 列表掩码规则。
 * 全平台一致的底座事实；密钥版本等运维信息仅运营可见，不在租户端暴露。
 */
function EncryptionSelfCheckTab() {
  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'compliance', 'encryption'],
    queryFn: () => opsComplianceApi(http).encryption(),
  });

  const fieldColumns: ColumnsType<SensitiveFieldRow> = [
    { title: '敏感字段', dataIndex: 'field', width: 160 },
    { title: '来源模块', dataIndex: 'sourceModule' },
    {
      title: '落库加密',
      dataIndex: 'encryptedAtRest',
      width: 200,
      render: (enc: boolean, row) =>
        enc ? (
          <span title={row.cipher ?? undefined}>
            <StatusPill badge={toneBadge('success', row.cipher ?? 'AES 加密')} />
          </span>
        ) : (
          <StatusPill badge={toneBadge('neutral', '明文')} />
        ),
    },
    {
      title: '列表掩码示例',
      dataIndex: 'maskExample',
      width: 160,
      render: (v: string | null) => (v ? <Typography.Text code>{v}</Typography.Text> : '—'),
    },
  ];

  const maskColumns: ColumnsType<MaskRuleExample> = [
    { title: '字段类型', dataIndex: 'fieldType', width: 120 },
    {
      title: '原值',
      dataIndex: 'sample',
      render: (v: string) => <Typography.Text type="secondary">{v}</Typography.Text>,
    },
    {
      title: '掩码后',
      dataIndex: 'masked',
      render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Alert
        type={data?.keyConfigured ? 'success' : 'warning'}
        showIcon
        message={
          data?.keyConfigured
            ? `落库加密已启用 · ${data.algorithm}`
            : '落库加密密钥未配置（敏感字段将以明文存储，请注入密钥）'
        }
        description={
          <Space direction="vertical" size={2}>
            <span>
              手机号 / 邮箱经确定性 AES-256-GCM 透明加解密落库（既支持唯一性校验，又不泄漏明文）；
              API Secret、Webhook / OSS 凭证、MFA 密钥由 CredentialCipher（随机 IV）加密。
            </span>
            {data ? (
              <span>
                当前密钥版本：<Typography.Text code>{data.activeKeyId}</Typography.Text>
                （支持多版本密钥平滑轮转）
              </span>
            ) : null}
          </Space>
        }
      />

      <Card size="small" title="敏感字段登记表">
        <Table<SensitiveFieldRow>
          rowKey="field"
          size="small"
          columns={fieldColumns}
          dataSource={data?.fields}
          loading={isLoading}
          pagination={false}
        />
      </Card>

      <Card size="small" title="列表掩码规则">
        <Table<MaskRuleExample>
          rowKey="fieldType"
          size="small"
          columns={maskColumns}
          dataSource={data?.maskRules}
          loading={isLoading}
          pagination={false}
        />
      </Card>
    </div>
  );
}
