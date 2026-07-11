import { useState } from 'react';
import { Button, Input, NavBar } from 'antd-mobile';

/**
 * 裸域（无租户子域）落地占位：H5 以子域名区分租户，形如 {tenantKey}.{基础域}。
 * 正式部署由租户开通流程派发完整子域地址；此页仅供本地调试跳转，默认测试租户 uitest。
 */
export function DevEntryPage() {
  const [tenantKey, setTenantKey] = useState('uitest');

  const go = () => {
    const key = tenantKey.trim();
    if (!key) return;
    const base = import.meta.env.VITE_TENANT_BASE_DOMAIN as string | undefined;
    if (!base) return;
    const port = window.location.port ? `:${window.location.port}` : '';
    window.location.assign(`${window.location.protocol}//${key}.${base}${port}/login`);
  };

  return (
    <div style={{ minHeight: '100dvh', background: 'var(--bg)' }}>
      <NavBar backArrow={false} style={{ background: 'var(--bg-elev)' }}>
        Wen SaaS Boot · 移动端
      </NavBar>
      <div style={{ padding: 24 }}>
        <p style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.7, marginTop: 8 }}>
          H5 以子域名区分租户，形如 <code>{'{tenantKey}'}.域名</code>
          ，由租户开通流程派发完整地址。下方仅供本地调试（跳转到 <code>{'{tenantKey}'}.localhost</code>）。
        </p>
        <Input
          value={tenantKey}
          onChange={(v) => setTenantKey(v.trim())}
          placeholder="tenantKey（例如 uitest）"
          style={{
            '--font-size': '15px',
            background: 'var(--bg-elev)',
            padding: '12px 14px',
            borderRadius: 10,
            margin: '16px 0',
          }}
        />
        <Button block color="primary" size="large" onClick={go}>
          前往登录
        </Button>
      </div>
    </div>
  );
}
