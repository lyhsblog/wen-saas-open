import { App, Button, Card, Col, Descriptions, QRCode, Row, Statistic, Typography } from 'antd';
import { CopyOutlined, EyeOutlined, LoginOutlined, PlusOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router';
import type { CreatedTenantView } from '@wen-saas/api-client';
import { PageHeader, StatusPill, toneBadge } from '@wen-saas/shared-ui';
import { tenantWebUrl } from '../../lib/tenantWebUrl';

export function OnboardResultPage() {
  const { message } = App.useApp();
  const location = useLocation();
  const navigate = useNavigate();
  const view = location.state as CreatedTenantView | null;

  if (!view) {
    return (
      <div style={{ padding: 24 }}>
        <Typography.Text type="secondary">页面数据已失效，请返回租户列表。</Typography.Text>
        <br />
        <Button style={{ marginTop: 12 }} onClick={() => void navigate('/tenants')}>
          返回列表
        </Button>
      </div>
    );
  }

  const resetUrl = tenantWebUrl(view.tenantCode, `/reset-password/${view.resetToken}`);
  const tenantLoginUrl = tenantWebUrl(view.tenantCode, '/login');
  const expiresAt = new Date(view.resetExpiresAt).getTime();

  const copyToClipboard = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      void message.success(`${label}已复制到剪贴板`);
    } catch {
      void message.error('复制失败，请手动复制');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <PageHeader
        title="租户开通成功"
        breadcrumb={[
          { label: '租户管理', to: '/tenants' },
          { label: `${view.tenantCode} · 开通结果` },
        ]}
      />
      <div style={{ flex: 1, overflow: 'auto', padding: 24 }}>
        <Row gutter={24}>
          <Col xs={24} md={14}>
            <Card
              title="租户信息"
              style={{ marginBottom: 16 }}
              extra={<StatusPill badge={toneBadge('success', '已开通')} />}
            >
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label="租户 ID">{view.tenantId}</Descriptions.Item>
                <Descriptions.Item label="租户标识">
                  <code>{view.tenantCode}</code>
                </Descriptions.Item>
                <Descriptions.Item label="内部编号">{view.tenantNo}</Descriptions.Item>
                <Descriptions.Item label="主管理员账号">{view.adminUsername}</Descriptions.Item>
                <Descriptions.Item label="租户登录地址">
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>
                    {tenantLoginUrl}
                  </span>
                  <Button
                    type="link"
                    size="small"
                    icon={<CopyOutlined />}
                    aria-label="复制登录地址"
                    onClick={() => void copyToClipboard(tenantLoginUrl, '登录地址')}
                    style={{ padding: '0 4px' }}
                  />
                </Descriptions.Item>
              </Descriptions>
            </Card>

            <Card title="密码设置链接">
              <Typography.Paragraph type="secondary" style={{ fontSize: 13, marginBottom: 12 }}>
                该链接已自动发送至主管理员邮箱，用于设置初始密码；如未收到可复制以下链接线下转发。链接
                <Typography.Text strong> 48 小时内</Typography.Text>
                有效，仅可使用一次。
              </Typography.Paragraph>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '10px 12px',
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
                  onClick={() => void copyToClipboard(resetUrl, '密码设置链接')}
                  style={{ flexShrink: 0 }}
                >
                  复制
                </Button>
              </div>
              <div style={{ marginTop: 16 }}>
                <Statistic.Countdown
                  title="链接有效期剩余"
                  value={expiresAt}
                  format="D 天 H:mm:ss"
                />
              </div>
            </Card>
          </Col>

          <Col xs={24} md={10}>
            <Card title="二维码" style={{ textAlign: 'center' }}>
              <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 16 }}>
                主管理员扫码打开密码设置页面
              </Typography.Paragraph>
              <QRCode value={resetUrl} size={200} style={{ margin: '0 auto' }} />
            </Card>
          </Col>
        </Row>

        <div style={{ display: 'flex', gap: 12, marginTop: 8 }}>
          <Button
            type="primary"
            icon={<EyeOutlined />}
            onClick={() => void navigate(`/tenants/${view.tenantId}`)}
          >
            查看租户详情
          </Button>
          <Button icon={<PlusOutlined />} onClick={() => void navigate('/tenants')}>
            继续开通
          </Button>
          <Button icon={<LoginOutlined />} onClick={() => window.open(tenantLoginUrl, '_blank')}>
            打开租户登录页
          </Button>
        </div>
      </div>
    </div>
  );
}
