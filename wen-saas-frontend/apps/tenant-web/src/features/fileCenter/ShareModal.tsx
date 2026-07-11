import { useState } from 'react';
import { App, Alert, Form, Input, InputNumber, Modal, Select, Typography } from 'antd';
import { describeApiError, type FileShareInfo } from '@wen-saas/api-client';
import type { FileCenterItem } from '@wen-saas/api-client';
import { formatDateTime } from '@wen-saas/shared-ui';
import { useCreateShare } from './fileCenterQueries';

type Props = {
  open: boolean;
  file: FileCenterItem | null;
  onClose: () => void;
};

const EXPIRY_OPTIONS = [
  { label: '7 天', value: 7 * 86400 },
  { label: '30 天', value: 30 * 86400 },
  { label: '永久', value: 0 },
];

/** 链接分享弹窗：创建分享并展示对外免登录落地页链接（FC-901）。 */
export function ShareModal({ open, file, onClose }: Props) {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [created, setCreated] = useState<FileShareInfo | null>(null);
  const createShare = useCreateShare();

  // 对外免登录落地页：/s/:shareToken（token 自携租户，无需登录 / tenantKey）
  const shareUrl = created ? `${window.location.origin}/s/${created.shareToken}` : '';

  const handleOk = async () => {
    if (!file) return;
    const v = (await form.validateFields()) as {
      expiry: number;
      password?: string;
      maxDownloads?: number;
    };
    try {
      const info = await createShare.mutateAsync({
        fileId: file.fileId,
        body: {
          expiresAfterSeconds: v.expiry === 0 ? null : v.expiry,
          ...(v.password ? { password: v.password } : {}),
          ...(v.maxDownloads ? { maxDownloads: v.maxDownloads } : {}),
        },
      });
      setCreated(info);
    } catch (e) {
      message.error(describeApiError(e, '创建分享失败'));
    }
  };

  const handleClose = () => {
    form.resetFields();
    setCreated(null);
    onClose();
  };

  return (
    <Modal
      title={`分享「${file?.fileName ?? ''}」`}
      open={open}
      onCancel={handleClose}
      onOk={created ? handleClose : handleOk}
      okText={created ? '完成' : '创建分享链接'}
      confirmLoading={createShare.isPending}
      width={520}
    >
      {created ? (
        <div>
          <Alert
            type="success"
            showIcon
            message="分享已创建"
            description="任何人可凭下方链接免登录访问下载；如需限制，建议设置访问密码 / 有效期 / 下载次数。"
            style={{ marginBottom: 12 }}
          />
          <div style={{ fontSize: 13, marginBottom: 4 }}>分享链接</div>
          <Typography.Paragraph copyable={{ text: shareUrl, tooltips: ['复制链接', '已复制'] }}>
            <Typography.Text code style={{ wordBreak: 'break-all' }}>
              {shareUrl}
            </Typography.Text>
          </Typography.Paragraph>
          {created.hasPassword && (
            <div style={{ fontSize: 12, color: 'var(--warning)', marginBottom: 8 }}>
              此分享需密码，请另行把访问密码告知对方。
            </div>
          )}
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
            {created.expiresAt ? `到期：${formatDateTime(created.expiresAt)}` : '永久有效'}
            {created.hasPassword ? ' · 需密码' : ''}
            {created.maxDownloads != null ? ` · 限 ${created.maxDownloads} 次下载` : ''}
          </div>
        </div>
      ) : (
        <Form form={form} layout="vertical" initialValues={{ expiry: 7 * 86400 }}>
          <Form.Item name="expiry" label="有效期">
            <Select options={EXPIRY_OPTIONS} />
          </Form.Item>
          <Form.Item
            name="password"
            label="访问密码（可选）"
            rules={[{ pattern: /^\d{4,6}$/, message: '请填 4-6 位数字' }]}
          >
            <Input placeholder="4-6 位数字，留空则无密码" maxLength={6} allowClear />
          </Form.Item>
          <Form.Item name="maxDownloads" label="最大下载次数（可选）">
            <InputNumber min={1} style={{ width: '100%' }} placeholder="留空则不限" />
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
}
