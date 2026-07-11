import { useRef } from 'react';
import { App, Button, Popconfirm, Spin, Tooltip, Typography } from 'antd';
import { DeleteOutlined, PaperClipOutlined, UploadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { BizFileMetadata } from '@wen-saas/api-client';
import { PermissionGate } from '@wen-saas/permissions';
import { bizApi, customerAttachmentOwner } from './crmApi';

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * 客户附件区（详情抽屉内）：零自建表——直接走通用 /api/v1/files 端点，
 * 上传带 owner 三元组落 sys_file，下载经 CrmDemoFilePermissionChecker 鉴权。
 */
export function CustomerAttachments({ customerId }: { customerId: string }) {
  const { message } = App.useApp();
  const qc = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const owner = customerAttachmentOwner(customerId);
  const queryKey = ['crm-customer', 'attachments', customerId] as const;

  const { data, isLoading } = useQuery({
    queryKey,
    queryFn: () => bizApi.list(owner),
  });

  const upload = useMutation({
    mutationFn: (file: File) => bizApi.upload(file, owner),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey });
      void message.success('附件已上传');
    },
    onError: () => void message.error('上传失败'),
  });

  const remove = useMutation({
    mutationFn: (fileId: string) => bizApi.remove(fileId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey });
      void message.success('附件已删除');
    },
    onError: () => void message.error('删除失败'),
  });

  const open = async (f: BizFileMetadata) => {
    try {
      const url = await bizApi.downloadUrl(f.fileId);
      window.open(url, '_blank', 'noopener');
    } catch {
      void message.error('获取下载地址失败');
    }
  };

  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography.Text strong>附件</Typography.Text>
        <PermissionGate permission="crm_demo.customer.edit">
          <input
            ref={fileRef}
            type="file"
            style={{ display: 'none' }}
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) upload.mutate(f);
              e.target.value = '';
            }}
          />
          <Button
            size="small"
            icon={<UploadOutlined />}
            loading={upload.isPending}
            onClick={() => fileRef.current?.click()}
          >
            上传附件
          </Button>
        </PermissionGate>
      </div>
      {isLoading ? (
        <Spin size="small" style={{ marginTop: 8 }} />
      ) : !data || data.length === 0 ? (
        <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
          暂无附件
        </Typography.Text>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 8 }}>
          {data.map((f) => (
            <div
              key={f.fileId}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                padding: '4px 8px',
                border: '1px solid var(--divider, #f0f0f0)',
                borderRadius: 6,
              }}
            >
              <PaperClipOutlined style={{ color: 'rgba(0,0,0,0.45)' }} />
              <Tooltip title="点击下载">
                <Typography.Link
                  onClick={() => void open(f)}
                  style={{
                    flex: 1,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {f.fileName}
                </Typography.Link>
              </Tooltip>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {formatSize(f.sizeBytes)}
              </Typography.Text>
              <PermissionGate permission="crm_demo.customer.edit">
                <Popconfirm title="删除该附件？" onConfirm={() => remove.mutate(f.fileId)}>
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    aria-label="删除附件"
                  />
                </Popconfirm>
              </PermissionGate>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
