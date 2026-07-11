import { useMemo, useState } from 'react';
import { CountChip, ErrorState, shouldSkipRowNav } from '@wen-saas/shared-ui';
import { Empty, Input, Spin } from 'antd';
import { AppstoreOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { opsBusinessModuleApi } from '@wen-saas/api-client';
import type { BusinessModuleListItem } from '@wen-saas/api-client';
import { http } from '../../lib/http';

type Props = {
  selectedCode: string | null;
  onSelect: (code: string) => void;
  compact: boolean;
};

export function OpsModuleListPane({ selectedCode, onSelect, compact }: Props) {
  const [keyword, setKeyword] = useState('');

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['ops', 'modules'],
    queryFn: () => opsBusinessModuleApi(http).listModules(),
  });

  const filtered = useMemo(() => {
    const items = data ?? [];
    if (!keyword.trim()) return items;
    const kw = keyword.trim().toLowerCase();
    return items.filter(
      (m) => m.name.toLowerCase().includes(kw) || m.code.toLowerCase().includes(kw),
    );
  }, [data, keyword]);

  const renderItem = (m: BusinessModuleListItem) => {
    const isSelected = m.code === selectedCode;
    return (
      <div
        key={m.code}
        onClick={() => {
          if (shouldSkipRowNav()) return;
          onSelect(m.code);
        }}
        style={{
          padding: '12px 16px',
          cursor: 'pointer',
          background: isSelected ? 'var(--brand-bg)' : 'transparent',
          borderLeft: `3px solid ${isSelected ? 'var(--brand)' : 'transparent'}`,
          borderBottom: '1px solid var(--divider)',
          transition: 'background .15s',
        }}
        onMouseEnter={(e) => {
          if (!isSelected) e.currentTarget.style.background = 'var(--bg-muted)';
        }}
        onMouseLeave={(e) => {
          if (!isSelected) e.currentTarget.style.background = 'transparent';
        }}
      >
        <div style={{ marginBottom: 4 }}>
          <span
            style={{
              fontWeight: 600,
              fontSize: 14,
              display: 'block',
              minWidth: 0,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {m.name}
          </span>
        </div>
        <div
          style={{
            fontSize: 12,
            color: 'var(--text-tertiary)',
            display: 'flex',
            gap: 6,
            flexWrap: 'wrap',
            alignItems: 'center',
          }}
        >
          <code style={{ fontFamily: 'var(--font-mono)' }}>{m.code}</code>
          <CountChip>
            <span style={{ fontFamily: 'var(--font-mono)' }}>v{m.version}</span>
          </CountChip>
          <CountChip>{m.contributionCount} 扩展点</CountChip>
          {m.dependencyCount > 0 && <CountChip>{m.dependencyCount} 依赖</CountChip>}
        </div>
      </div>
    );
  };

  const body = isLoading ? (
    <div style={{ padding: 48, textAlign: 'center' }}>
      <Spin />
    </div>
  ) : isError ? (
    <ErrorState
      title="加载列表失败"
      primaryAction={{ label: '重试', onClick: () => void refetch() }}
    />
  ) : filtered.length === 0 ? (
    <Empty
      description={
        keyword.trim() ? '无匹配的业务模块' : '暂无业务模块 · 模块由系统部署注册后自动出现'
      }
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      style={{ padding: '48px 0' }}
    />
  ) : (
    filtered.map(renderItem)
  );

  const total = filtered.length;

  return (
    <div
      style={{
        width: compact ? 380 : undefined,
        flex: compact ? undefined : 1,
        flexShrink: compact ? 0 : undefined,
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--bg-elev)',
        borderRight: compact ? '1px solid var(--border)' : undefined,
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          padding: compact ? '14px 16px' : '14px 24px',
          borderBottom: '1px solid var(--divider)',
          display: 'flex',
          flexDirection: 'column',
          gap: 10,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <AppstoreOutlined style={{ color: 'var(--text-tertiary)' }} />
          <span style={{ fontWeight: 600, fontSize: 15 }}>业务模块</span>
          {data && <CountChip>{total}</CountChip>}
        </div>
        <Input.Search
          size={compact ? 'small' : 'middle'}
          placeholder="搜索模块名 / 编码"
          allowClear
          style={compact ? undefined : { width: 260 }}
          onChange={(e) => setKeyword(e.target.value)}
          onSearch={(v) => setKeyword(v)}
        />
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>{body}</div>
    </div>
  );
}
