import { useState } from 'react';
import { Button, Card, Input, Typography } from 'antd';

/**
 * 裸域（无租户子域）落地占位：租户后台以子域名区分，形如 {tenantKey}.{基础域}。
 * 正式部署时租户开通流程会派发完整子域地址；此页仅供本地调试跳转到目标子域。
 */
export function DevEntryPage() {
  const [tenantKey, setTenantKey] = useState('dev');

  const go = (v: string) => {
    const key = v.trim();
    if (!key) return;
    const base = import.meta.env.VITE_TENANT_BASE_DOMAIN as string | undefined;
    if (!base) return;
    const port = window.location.port ? `:${window.location.port}` : '';
    window.location.assign(`${window.location.protocol}//${key}.${base}${port}/login`);
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
        background: 'var(--bg)',
      }}
    >
      <Card
        bordered={false}
        style={{ width: 460, boxShadow: 'var(--shadow-md)', borderRadius: 'var(--r-xl)' }}
        styles={{ body: { padding: '32px 32px 28px' } }}
      >
        <Typography.Title level={4} style={{ marginTop: 0 }}>
          请通过完整租户地址进入
        </Typography.Title>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 20 }}>
          租户后台以子域名区分，形如 <code>{'{tenantKey}'}.域名</code>，由租户开通流程派发完整地址。
          下方仅供本地调试使用（跳转到 <code>{'{tenantKey}'}.localhost</code>）。
        </Typography.Paragraph>
        <Input.Search
          value={tenantKey}
          onChange={(e) => setTenantKey(e.target.value.trim())}
          placeholder="tenantKey（例如 dev）"
          enterButton={<Button type="primary">前往</Button>}
          size="large"
          onSearch={go}
        />
      </Card>
    </div>
  );
}
