import { useMemo, useState } from 'react';
import { Alert, App, Button, Descriptions, Input, Modal } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { describeApiError } from '@wen-saas/api-client';
import { exportApi } from './exportApi';
import { taskKeys } from './taskQueries';

type Props = {
  /** 导出对象 code（与后端 ExportDataProvider / 审批开关声明对齐，如 member / audit_log / dict）。 */
  objectCode: string;
  /** 当前列表筛选条件（provider 自定义键），随任务/审批台账快照透传。 */
  filters?: Record<string, string>;
  /** 筛选条件人类可读摘要（审批台账展示）。 */
  filterSummary?: string;
  /** 按钮文案，默认「导出」。 */
  label?: string;
  size?: 'small' | 'middle' | 'large';
};

/**
 * 列表页「导出」入口：点开确认弹窗，携当前筛选发起导出。后端按合规策略
 * 分流——无需审批直投任务中心（去「导入导出」页下载）；需审批转 PENDING（理由必填，
 * 审批通过后自动以申请人身份执行并站内信通知）。对象的审批开关从 `/exports/objects` 读取。
 */
export function ExportEntryButton({
  objectCode,
  filters,
  filterSummary,
  label = '导出',
  size = 'small',
}: Props) {
  const { message } = App.useApp();
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');

  const objectsQuery = useQuery({
    queryKey: ['exports', 'objects'],
    queryFn: () => exportApi.objects(),
    enabled: open,
    staleTime: 60_000,
  });
  const option = useMemo(
    () => objectsQuery.data?.find((o) => o.code === objectCode) ?? null,
    [objectsQuery.data, objectCode],
  );
  const requiresApproval = option?.requiresApproval ?? false;

  const submit = useMutation({
    mutationFn: () =>
      exportApi.submit({
        objectCode,
        ...(filters && Object.keys(filters).length > 0 ? { filters } : {}),
        ...(filterSummary ? { filterSummary } : {}),
        ...(reason.trim() ? { reason: reason.trim() } : {}),
      }),
    onSuccess: (r) => {
      setOpen(false);
      setReason('');
      void qc.invalidateQueries({ queryKey: taskKeys.all });
      void qc.invalidateQueries({ queryKey: ['compliance'] });
      if (r.status === 'submitted') {
        message.success('导出任务已创建，请前往「导入导出」页查看进度与下载');
      } else {
        message.success('已提交导出审批，审批通过后将自动执行并站内信通知你');
      }
    },
    onError: (err) => {
      message.error(describeApiError(err, '发起导出失败'));
    },
  });

  return (
    <>
      <Button size={size} icon={<DownloadOutlined />} onClick={() => setOpen(true)}>
        {label}
      </Button>
      <Modal
        title={`导出${option?.name ?? ''}`}
        open={open}
        onCancel={() => {
          setOpen(false);
          setReason('');
        }}
        onOk={() => submit.mutate()}
        okText={requiresApproval ? '提交审批' : '开始导出'}
        okButtonProps={{
          loading: submit.isPending,
          disabled: objectsQuery.isLoading || !option || (requiresApproval && !reason.trim()),
        }}
        destroyOnHidden
      >
        {objectsQuery.isLoading ? (
          <div style={{ color: 'var(--text-tertiary)', fontSize: 13 }}>加载导出配置…</div>
        ) : !option ? (
          <Alert type="warning" showIcon message="当前账号无权导出该对象，或导出对象未注册" />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {requiresApproval && (
              <Alert
                type="info"
                showIcon
                message="该导出对象已开启审批"
                description="提交后将通知审批人，审批通过后自动执行，完成后站内信通知你前往「导入导出」页下载。"
              />
            )}
            <Descriptions column={1} size="small" bordered styles={{ label: { width: 96 } }}>
              <Descriptions.Item label="导出对象">{option.name}</Descriptions.Item>
              <Descriptions.Item label="筛选条件">{filterSummary || '全部'}</Descriptions.Item>
              <Descriptions.Item label="文件格式">xlsx（与列表口径一致）</Descriptions.Item>
            </Descriptions>
            {requiresApproval && (
              <Input.TextArea
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="申请理由（必填），供审批人参考"
                maxLength={500}
                rows={3}
                showCount
                // showCount 的计数器绝对定位在包装盒下方 22px，垫出空间以免被 Modal 底部按钮遮住
                style={{ marginBottom: 16 }}
              />
            )}
          </div>
        )}
      </Modal>
    </>
  );
}
