import { useState } from 'react';
import { Alert, App, Button, Modal, QRCode, Statistic, Typography } from 'antd';
import { CopyOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { ApiError, opsTenantApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { tenantWebUrl } from '../../lib/tenantWebUrl';

type Props = {
  open: boolean;
  tenantId: string;
  tenantCode: string;
  adminUsername: string;
  onClose: () => void;
  /** 成功后回调（调用方 refetch 详情让 mainAdmin.status 等保持最新）。 */
  onSuccess?: () => void;
};

type ResultData = {
  resetToken: string;
  resetExpiresAt: string;
  adminUsername: string;
};

export function ReissueResetLinkModal({
  open,
  tenantId,
  tenantCode,
  adminUsername,
  onClose,
  onSuccess,
}: Props) {
  const { message } = App.useApp();
  const [result, setResult] = useState<ResultData | null>(null);
  const [errorText, setErrorText] = useState<string | null>(null);

  // 关闭时清状态（下次打开是干净的 confirm 状态）
  const handleClose = () => {
    setResult(null);
    setErrorText(null);
    onClose();
  };

  const { mutateAsync, isPending } = useMutation({
    mutationFn: () => opsTenantApi(http).reissueResetLink(tenantId),
  });

  const handleConfirm = async () => {
    setErrorText(null);
    try {
      const r = await mutateAsync();
      setResult(r);
      onSuccess?.();
    } catch (err) {
      const text =
        err instanceof ApiError
          ? (err.problem.detail ?? err.problem.title ?? '重发失败')
          : '网络错误，请稍后重试';
      setErrorText(text);
    }
  };

  const copyToClipboard = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      void message.success(`${label}已复制到剪贴板`);
    } catch {
      void message.error('复制失败，请手动复制');
    }
  };

  return (
    <Modal
      open={open}
      title="重新生成密码设置链接"
      onCancel={handleClose}
      width={560}
      mask={{ closable: false }}
      footer={
        result ? (
          <Button type="primary" onClick={handleClose}>
            完成
          </Button>
        ) : (
          <>
            <Button onClick={handleClose}>取消</Button>
            <Button type="primary" danger loading={isPending} onClick={() => void handleConfirm()}>
              确认重新生成
            </Button>
          </>
        )
      }
    >
      {!result ? (
        <div>
          <Alert
            type="warning"
            showIcon
            message="操作不可撤销"
            description={
              <div>
                重新生成新链接后，<strong>该管理员现有的所有未使用链接将立即作废</strong>
                。请确认有渠道将新链接发送给主管理员后再继续。
              </div>
            }
            style={{ marginBottom: 16 }}
          />
          <Typography.Paragraph type="secondary" style={{ fontSize: 13, marginBottom: 0 }}>
            主管理员账号：<strong>{adminUsername}</strong>
          </Typography.Paragraph>
          {errorText && (
            <Alert type="error" showIcon message={errorText} style={{ marginTop: 12 }} />
          )}
        </div>
      ) : (
        <ResultPanel
          tenantCode={tenantCode}
          data={result}
          onCopy={(text, label) => {
            void copyToClipboard(text, label);
          }}
        />
      )}
    </Modal>
  );
}

function ResultPanel({
  tenantCode,
  data,
  onCopy,
}: {
  tenantCode: string;
  data: ResultData;
  onCopy: (text: string, label: string) => void;
}) {
  const resetUrl = tenantWebUrl(tenantCode, `/reset-password/${data.resetToken}`);
  const expiresAt = new Date(data.resetExpiresAt).getTime();
  return (
    <div>
      <Alert
        type="success"
        showIcon
        message={
          <span>
            新链接已生成，账号 <strong>{data.adminUsername}</strong> 的旧链接已作废
          </span>
        }
        style={{ marginBottom: 16 }}
      />
      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <Typography.Paragraph
            type="secondary"
            style={{ fontSize: 12, marginBottom: 6, marginTop: 0 }}
          >
            密码设置链接（48 小时内有效）
          </Typography.Paragraph>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 10px',
              background: 'var(--bg-muted)',
              borderRadius: 'var(--r-md)',
              border: '1px solid var(--border)',
              wordBreak: 'break-all',
            }}
          >
            <Typography.Text style={{ flex: 1, fontFamily: 'var(--font-mono)', fontSize: 12 }}>
              {resetUrl}
            </Typography.Text>
            <Button
              icon={<CopyOutlined />}
              size="small"
              onClick={() => onCopy(resetUrl, '密码设置链接')}
            >
              复制
            </Button>
          </div>
          <div style={{ marginTop: 12 }}>
            <Statistic.Countdown
              title="链接有效期剩余"
              value={expiresAt}
              format="D 天 H:mm:ss"
              valueStyle={{ fontSize: 16 }}
            />
          </div>
        </div>
        <div style={{ flexShrink: 0, textAlign: 'center' }}>
          <QRCode value={resetUrl} size={120} />
          <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 4 }}>扫码打开</div>
        </div>
      </div>
    </div>
  );
}

/** 触发按钮（与 Modal 解耦；方便在 PageHeader extra / Card 内重复用）。 */
export function ReissueResetLinkTriggerButton({
  onClick,
  disabled = false,
}: {
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <Button
      icon={<ReloadOutlined />}
      onClick={onClick}
      disabled={disabled}
      {...(disabled ? { title: '当前租户未指定主管理员，无法重发链接' } : {})}
    >
      重发密码设置链接
    </Button>
  );
}
