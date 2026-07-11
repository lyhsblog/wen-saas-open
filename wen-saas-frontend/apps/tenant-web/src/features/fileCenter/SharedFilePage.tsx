import { useState } from 'react';
import { useParams } from 'react-router';
import { App, Button, Card, Descriptions, Input, Result, Space, Spin } from 'antd';
import { DownloadOutlined, FileOutlined, LockOutlined } from '@ant-design/icons';
import { describeApiError } from '@wen-saas/api-client';
import { formatBytes, formatDateTime } from '@wen-saas/shared-ui';
import { useConsumeShare, useShareInfo } from './fileCenterQueries';

/**
 * 链接分享访问页（P0：仅限本租户成员登录后访问，路由已套 RequireAuth）。
 * 流程：拉分享元信息 → 需密码则输入 → 消费分享拿签名 URL → 新窗口下载。
 */
export function SharedFilePage() {
  const { message } = App.useApp();
  const params = useParams<{ shareToken: string }>();
  // 多页签保活下非激活面板的 useParams() 读到的是「当前 URL」的参数而非本页签自己的；
  // 挂载时用 useState 初始化器一次性固化，后续渲染/请求只用固化值，避免并存多个分享页签时串台。
  const [shareToken] = useState(() => params.shareToken);
  const infoQuery = useShareInfo(shareToken);
  const consume = useConsumeShare();
  const [password, setPassword] = useState('');

  const handleDownload = async () => {
    if (!shareToken) return;
    try {
      const res = await consume.mutateAsync({
        shareToken,
        ...(password ? { password } : {}),
      });
      window.open(res.signedDownloadUrl, '_blank', 'noopener');
      message.success('已开始下载');
      // 消费成功后剩余下载次数已变，刷新分享信息。
      void infoQuery.refetch();
    } catch (e) {
      message.error(describeApiError(e, '下载失败，请确认密码或链接是否有效'));
    }
  };

  if (infoQuery.isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
        <Spin />
      </div>
    );
  }

  if (infoQuery.isError || !infoQuery.data) {
    return <Result status="404" title="分享不可用" subTitle="链接可能已过期、被撤销或不存在。" />;
  }

  const info = infoQuery.data;
  const inactive = info.status !== 'active';
  const exhausted = info.remainingDownloads != null && info.remainingDownloads <= 0;

  return (
    <div style={{ height: '100%', overflowY: 'auto' }}>
      <div style={{ maxWidth: 560, margin: '40px auto' }}>
        <Card>
          <div style={{ textAlign: 'center', marginBottom: 24 }}>
            <FileOutlined style={{ fontSize: 48, color: 'var(--brand)' }} />
            <div style={{ fontSize: 18, fontWeight: 600, marginTop: 12 }}>{info.fileName}</div>
          </div>
          <Descriptions column={1} size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="大小">{formatBytes(info.sizeBytes)}</Descriptions.Item>
            <Descriptions.Item label="有效期">
              {info.expiresAt ? formatDateTime(info.expiresAt) : '永久有效'}
            </Descriptions.Item>
            {info.maxDownloads != null && (
              <Descriptions.Item label="剩余下载次数">
                {info.remainingDownloads ?? info.maxDownloads} / {info.maxDownloads}
              </Descriptions.Item>
            )}
          </Descriptions>

          {inactive || exhausted ? (
            <Result
              status="warning"
              title={exhausted ? '下载次数已用尽' : '分享已失效'}
              subTitle="请联系分享者重新生成链接。"
              style={{ padding: '12px 0' }}
            />
          ) : (
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              {info.hasPassword && (
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder="请输入访问密码"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onPressEnter={() => {
                    void handleDownload();
                  }}
                  maxLength={6}
                />
              )}
              <Button
                type="primary"
                block
                icon={<DownloadOutlined />}
                loading={consume.isPending}
                disabled={info.hasPassword && !password}
                onClick={() => {
                  void handleDownload();
                }}
              >
                下载文件
              </Button>
            </Space>
          )}
        </Card>
      </div>
    </div>
  );
}
