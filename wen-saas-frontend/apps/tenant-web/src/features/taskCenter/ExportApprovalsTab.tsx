import { useState } from 'react';
import {
  Alert,
  App,
  Button,
  Descriptions,
  Empty,
  Input,
  Modal,
  Segmented,
  Space,
  Table,
  Tooltip,
} from 'antd';
import { StatusPill } from '@wen-saas/shared-ui';
import { useNavigate } from 'react-router';
import type { ColumnsType } from 'antd/lib/table';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { ExportRequestStatus, ExportRequestView } from '@wen-saas/api-client';
import { describeApiError } from '@wen-saas/api-client';
import { useHasPermission } from '@wen-saas/permissions';
import { complianceApi } from '../compliance/complianceApi';
import { complianceKeys, useAllExportRequests } from '../compliance/complianceQueries';
import { exportRequestStatusBadge, fmtTime } from '../compliance/utils';
import { taskKeys } from './taskQueries';

const PAGE_SIZE = 20;

type Decision = { request: ExportRequestView; approve: boolean };

/**
 * 导入导出页 ·「待审批」Tab（导出审批在「任务中心 → 待审批」的落点）。
 * 审批人（主管理员 / 持 base.compliance.export.approve 的角色）处理导出申请：通过 → 后端自动以
 * 申请人身份投递执行任务（任务对申请人可见可下载）；拒绝 → 站内信通知申请人。
 */
export function ExportApprovalsTab() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const qc = useQueryClient();
  // 合规页按 config 门禁；纯审批人（如部门主管）不带跳转链接，只给说明
  const canConfig = useHasPermission('base.compliance.config');
  const [status, setStatus] = useState<ExportRequestStatus>('PENDING');
  const [page, setPage] = useState(1);
  const [decision, setDecision] = useState<Decision | null>(null);
  const [comment, setComment] = useState('');

  const { data, isLoading } = useAllExportRequests({ status, page, size: PAGE_SIZE }, true);

  const decide = useMutation({
    mutationFn: (d: Decision) =>
      d.approve
        ? complianceApi.approve(d.request.id, comment.trim() || undefined)
        : complianceApi.reject(d.request.id, comment.trim() || undefined),
    onSuccess: (_, d) => {
      message.success(d.approve ? '已批准，导出任务已自动创建' : '已拒绝，将通知申请人');
      setDecision(null);
      setComment('');
      void qc.invalidateQueries({ queryKey: complianceKeys.all });
      void qc.invalidateQueries({ queryKey: taskKeys.all });
    },
    onError: (err) => {
      message.error(describeApiError(err, '审批操作失败'));
    },
  });

  const columns: ColumnsType<ExportRequestView> = [
    { title: '申请单号', dataIndex: 'requestNo', width: 150 },
    {
      title: '导出对象',
      dataIndex: 'objectType',
      width: 110,
      render: (v: string | null) => v || '—',
    },
    {
      title: '筛选条件',
      dataIndex: 'filterSummary',
      ellipsis: true,
      render: (v: string | null) => v || '全部',
    },
    {
      title: '预估条数',
      dataIndex: 'estimatedCount',
      width: 90,
      align: 'right' as const,
      render: (v: number | null) => v?.toLocaleString() ?? '—',
    },
    {
      title: '申请理由',
      dataIndex: 'reason',
      ellipsis: true,
      render: (v: string | null) => (v ? <Tooltip title={v}>{v}</Tooltip> : '—'),
    },
    {
      title: '申请人',
      dataIndex: 'applicantName',
      width: 110,
      render: (v: string | null) => v || '—',
    },
    { title: '申请时间', dataIndex: 'appliedAt', width: 160, render: fmtTime },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: ExportRequestStatus) => <StatusPill badge={exportRequestStatusBadge(v)} />,
    },
  ];

  if (status === 'PENDING') {
    columns.push({
      title: '操作',
      key: 'actions',
      width: 130,
      render: (_, r) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            onClick={() => setDecision({ request: r, approve: true })}
          >
            通过
          </Button>
          <Button
            type="link"
            size="small"
            danger
            onClick={() => setDecision({ request: r, approve: false })}
          >
            拒绝
          </Button>
        </Space>
      ),
    });
  } else {
    columns.push(
      {
        title: '审批人',
        dataIndex: 'approverName',
        width: 110,
        render: (v: string | null) => v || '—',
      },
      {
        title: '审批意见',
        dataIndex: 'approvalComment',
        ellipsis: true,
        render: (v: string | null) => v || '—',
      },
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {/* 审批规则（哪些对象需审批 / 超时提醒）在合规页维护，这里只做申请的处理 */}
      <Alert
        type="info"
        showIcon
        message={
          canConfig ? (
            <span>
              审批规则（哪些对象需审批 / 超时提醒）在
              <a onClick={() => void navigate('/system/compliance?tab=export')}> 导出审批策略 </a>
              维护。
            </span>
          ) : (
            <span>
              审批规则（哪些对象需审批 / 超时提醒）由持「策略配置」权限的管理员在合规页维护。
            </span>
          )
        }
      />
      <Segmented
        value={status}
        onChange={(v) => {
          setStatus(v as ExportRequestStatus);
          setPage(1);
        }}
        options={[
          { label: '待审批', value: 'PENDING' },
          { label: '已批准', value: 'APPROVED' },
          { label: '已拒绝', value: 'REJECTED' },
        ]}
      />
      <Table<ExportRequestView>
        columns={columns}
        dataSource={data?.items ?? []}
        rowKey="id"
        size="small"
        loading={isLoading}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: data?.total ?? 0,
          showTotal: (n) => `共 ${n} 条`,
          onChange: setPage,
          showSizeChanger: false,
        }}
        locale={{
          emptyText: (
            <Empty description={status === 'PENDING' ? '暂无待审批的导出申请' : '暂无记录'} />
          ),
        }}
      />

      <Modal
        title={decision?.approve ? '批准导出申请' : '拒绝导出申请'}
        open={!!decision}
        onCancel={() => {
          setDecision(null);
          setComment('');
        }}
        onOk={() => decision && decide.mutate(decision)}
        okText={decision?.approve ? '批准' : '拒绝'}
        okButtonProps={{ loading: decide.isPending, danger: !decision?.approve }}
        destroyOnHidden
      >
        {decision && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Descriptions column={1} size="small" bordered styles={{ label: { width: 88 } }}>
              <Descriptions.Item label="申请人">
                {decision.request.applicantName || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="导出对象">
                {decision.request.objectType || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="筛选条件">
                {decision.request.filterSummary || '全部'}
              </Descriptions.Item>
              <Descriptions.Item label="预估条数">
                {decision.request.estimatedCount?.toLocaleString() ?? '—'} 条
              </Descriptions.Item>
              {/* 决策最需要的依据：申请理由（此前只在表格列 tooltip 里可见） */}
              <Descriptions.Item label="申请理由">
                {decision.request.reason || '—'}
              </Descriptions.Item>
            </Descriptions>
            <div style={{ fontSize: 12, color: 'var(--text-tertiary, #8c8c8c)' }}>
              {decision.approve
                ? '批准后将以申请人身份自动执行，完成后站内信通知其下载。'
                : '拒绝后任务终止，将站内信通知申请人。'}
            </div>
            <Input.TextArea
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="审批意见（选填）"
              maxLength={500}
              rows={3}
            />
          </div>
        )}
      </Modal>
    </div>
  );
}
