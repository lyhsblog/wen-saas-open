import { useState } from 'react';
import { Input, Modal } from 'antd';
import { DangerConfirm } from '@wen-saas/shared-ui';
import { useSuspendTenant, useResumeTenant } from '../hooks/useTenantMutations';

type Props = {
  open: boolean;
  tenantId: string;
  tenantName: string;
  /** 当前要切换到的状态：0=暂停 / 1=启用 */
  targetStatus: 0 | 1;
  onClose: () => void;
};

export function UpdateStatusModal({ open, tenantId, tenantName, targetStatus, onClose }: Props) {
  const suspend = useSuspendTenant(tenantId);
  const resume = useResumeTenant(tenantId);
  const isPause = targetStatus === 0;
  const isPending = suspend.isPending || resume.isPending;
  const [reason, setReason] = useState('');

  const closeAndReset = () => {
    setReason('');
    onClose();
  };

  const handleConfirmPause = async () => {
    const trimmed = reason.trim();
    await suspend.mutateAsync({ reason: trimmed || null });
    closeAndReset();
  };

  const handleConfirmEnable = async () => {
    await resume.mutateAsync();
    onClose();
  };

  if (isPause) {
    return (
      <DangerConfirm
        open={open}
        title={`暂停租户：${tenantName}`}
        lead="您即将暂停该租户，所有成员将立即无法登录"
        consequences={[
          '该租户下所有用户登录将被拒绝（提示已暂停）',
          'API Key 调用将返回 403 TENANT_INACTIVE',
          'Webhook、定时任务将被跳过',
          '启用后立即恢复，数据不受影响',
        ]}
        extra={
          <div>
            <label
              style={{
                display: 'block',
                fontSize: 13,
                color: 'var(--text-secondary)',
                marginBottom: 6,
              }}
            >
              暂停原因（选填，将记入操作日志）
            </label>
            <Input.TextArea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="例如：客户欠费 / 应客户要求临时停用"
              maxLength={500}
              showCount
              autoSize={{ minRows: 2, maxRows: 4 }}
              disabled={isPending}
            />
          </div>
        }
        confirmPhrase={tenantName}
        okText="确认暂停"
        confirming={isPending}
        onCancel={closeAndReset}
        onConfirm={() => void handleConfirmPause()}
      />
    );
  }

  return (
    <Modal
      open={open}
      title={`启用租户：${tenantName}`}
      onCancel={onClose}
      onOk={() => void handleConfirmEnable()}
      confirmLoading={isPending}
      mask={{ closable: false }}
      destroyOnHidden
      okText="确认启用"
      width={400}
    >
      <div style={{ padding: '8px 0', color: 'var(--text-secondary)', fontSize: 14 }}>
        启用后，该租户下所有成员将可正常登录，功能恢复正常。
      </div>
    </Modal>
  );
}
