import { useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { App, Button, Card, Descriptions, Input, Result, Space, Spin } from 'antd';
import { DownloadOutlined, FileOutlined, LockOutlined } from '@ant-design/icons';
import { useQuery, useMutation } from '@tanstack/react-query';
import { ApiError, publicShareApi } from '@wen-saas/api-client';
import { formatBytes, formatDateTime } from '@wen-saas/shared-ui';
import { http } from '../../lib/http';

const centerStyle: React.CSSProperties = {
  minHeight: '100vh',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: 24,
};

/**
 * 文件免登录分享落地页（FC-901）。对外公开路由 `/s/:shareToken`，无需登录态。
 *
 * <p>对应内部登录版 {@link SharedFilePage}（`/file-center/share/:shareToken`）。
 * 流程：拉公开分享元信息 → 需密码则输入 → 消费分享拿签名 URL → 新窗口下载。
 * 走 `/api/v1/public/shares/**`（无鉴权、跨租户，token 自携租户）。
 */
export function PublicSharePage() {
  const { message } = App.useApp();
  const { shareToken = '' } = useParams<{ shareToken: string }>();
  const [password, setPassword] = useState('');

  // 公开页豁免 tokens.css 的 body min-width:1280（分享链接常被手机打开）
  useEffect(() => {
    document.body.classList.add('public-page');
    return () => document.body.classList.remove('public-page');
  }, []);

  const api = publicShareApi(http);

  const infoQuery = useQuery({
    queryKey: ['public-share', shareToken],
    queryFn: () => api.getShare(shareToken),
    enabled: !!shareToken,
    retry: false,
  });

  const consume = useMutation({
    mutationFn: () => api.download(shareToken, password || undefined),
  });

  const handleDownload = async () => {
    if (!shareToken) return;
    try {
      const res = await consume.mutateAsync();
      window.open(res.signedDownloadUrl, '_blank', 'noopener');
      message.success('已开始下载');
      void infoQuery.refetch();
    } catch (e) {
      if (e instanceof ApiError) {
        const tip: Record<number, string> = {
          401: '该分享需要密码，请输入访问密码',
          403: '密码错误，请重试',
          404: '分享不存在',
          410: '分享已过期或被撤销',
          429: '下载次数已用尽',
        };
        message.error(tip[e.status] ?? e.problem.detail ?? '下载失败，请稍后重试');
      } else {
        message.error('网络错误，请稍后重试');
      }
    }
  };

  if (infoQuery.isLoading) {
    return (
      <div style={centerStyle}>
        <Spin size="large" />
      </div>
    );
  }

  if (infoQuery.isError || !infoQuery.data) {
    const status = infoQuery.error instanceof ApiError ? infoQuery.error.status : 0;
    const gone = status === 410;
    return (
      <div style={centerStyle}>
        <Result
          status={gone ? 'warning' : '404'}
          title={gone ? '分享已失效' : '分享不可用'}
          subTitle={
            gone
              ? '该分享链接已过期或被撤销，请联系分享者重新生成。'
              : '链接不存在或已被删除，请确认链接是否正确。'
          }
        />
      </div>
    );
  }

  const info = infoQuery.data;
  const exhausted = info.remainingDownloads != null && info.remainingDownloads <= 0;

  return (
    <div
      style={{
        minHeight: '100vh',
        background: 'linear-gradient(180deg, var(--brand-bg) 0%, var(--bg) 50%)',
        ...centerStyle,
      }}
    >
      <Card
        bordered={false}
        style={{
          width: 'min(480px, calc(100vw - 32px))',
          boxShadow: 'var(--shadow-lg)',
          borderRadius: 'var(--r-xl)',
        }}
        styles={{ body: { padding: '36px 36px 28px' } }}
      >
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <FileOutlined style={{ fontSize: 48, color: 'var(--brand)' }} />
          <div style={{ fontSize: 18, fontWeight: 600, marginTop: 12, wordBreak: 'break-all' }}>
            {info.fileName}
          </div>
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

        {exhausted ? (
          <Result
            status="warning"
            title="下载次数已用尽"
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
                size="large"
              />
            )}
            <Button
              type="primary"
              block
              size="large"
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
  );
}
