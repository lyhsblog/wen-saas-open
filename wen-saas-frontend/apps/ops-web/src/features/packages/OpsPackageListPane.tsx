import { useMemo, useState } from 'react';
import { CountChip, ErrorState, shouldSkipRowNav } from '@wen-saas/shared-ui';
import { Button, Empty, Input, Select, Spin } from 'antd';
import { BookOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { opsPackageAdminApi } from '@wen-saas/api-client';
import type { ListPackagesQuery, PackageListItem, PackageStatus } from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { StatusPill } from '@wen-saas/shared-ui';
import { OpsPackageFormModal } from './OpsPackageFormModal';
import {
  packageStatusBadge,
  PACKAGE_STATUS_FILTER_OPTIONS,
  PACKAGE_STATUS_ORDER,
  ttlLabel,
} from './utils/statusMeta';

type Props = {
  selectedCode: string | null;
  onSelect: (code: string) => void;
  compact: boolean;
};

function sortPackages(items: readonly PackageListItem[]): PackageListItem[] {
  return [...items].sort((a, b) => {
    if (a.isSystem !== b.isSystem) return a.isSystem ? -1 : 1; // 基础包置顶
    const so = (PACKAGE_STATUS_ORDER[a.status] ?? 99) - (PACKAGE_STATUS_ORDER[b.status] ?? 99);
    if (so !== 0) return so;
    return b.createdAt.localeCompare(a.createdAt); // 同状态按创建时间倒序
  });
}

export function OpsPackageListPane({ selectedCode, onSelect, compact }: Props) {
  const [createOpen, setCreateOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<PackageStatus | undefined>(undefined);

  const query: ListPackagesQuery = {
    ...(keyword ? { keyword } : {}),
    ...(status != null ? { status } : {}),
  };

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['ops', 'packages', query],
    queryFn: () => opsPackageAdminApi(http).listPackages(query),
  });

  const items = useMemo(() => sortPackages(data ?? []), [data]);

  const handleCreated = (code: string) => {
    setCreateOpen(false);
    onSelect(code);
  };

  const toolbar = (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <BookOutlined style={{ color: 'var(--text-tertiary)' }} />
      <span style={{ fontWeight: 600, fontSize: 15 }}>套餐包</span>
      {data && <CountChip>{items.length}</CountChip>}
      <div style={{ flex: 1 }} />
      <Button
        type="primary"
        size="small"
        icon={<PlusOutlined />}
        onClick={() => setCreateOpen(true)}
      >
        新建套餐
      </Button>
    </div>
  );

  const filterBar = (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
      <Input.Search
        size={compact ? 'small' : 'middle'}
        placeholder="搜索套餐名 / 编码"
        allowClear
        style={compact ? undefined : { width: 260 }}
        onSearch={(v) => setKeyword(v)}
      />
      <Select<PackageStatus>
        size={compact ? 'small' : 'middle'}
        style={{ width: 120 }}
        placeholder="全部状态"
        allowClear
        value={status ?? null}
        onChange={(v) => setStatus(v ?? undefined)}
        options={PACKAGE_STATUS_FILTER_OPTIONS}
      />
    </div>
  );

  const list = isLoading ? (
    <div style={{ padding: 48, textAlign: 'center' }}>
      <Spin />
    </div>
  ) : isError ? (
    <ErrorState
      title="加载列表失败"
      primaryAction={{ label: '重试', onClick: () => void refetch() }}
    />
  ) : items.length === 0 ? (
    <Empty
      description="暂无套餐，点击右上角新建"
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      style={{ padding: '48px 0' }}
    />
  ) : (
    items.map((p) => {
      const isSelected = p.code === selectedCode;
      return (
        <div
          key={p.code}
          onClick={() => {
            if (shouldSkipRowNav()) return;
            onSelect(p.code);
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
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
            <span
              style={{
                fontWeight: 600,
                fontSize: 14,
                flex: 1,
                minWidth: 0,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {p.name}
            </span>
            <StatusPill badge={packageStatusBadge(p.status)} />
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
            <code style={{ fontFamily: 'var(--font-mono)' }}>{p.code}</code>
            {p.isSystem && <CountChip tone="brand">系统</CountChip>}
            <CountChip>{p.moduleCount} 模块</CountChip>
            <CountChip>{ttlLabel(p.defaultTtlDays)}</CountChip>
          </div>
        </div>
      );
    })
  );

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
        {toolbar}
        {filterBar}
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>{list}</div>
      <OpsPackageFormModal
        open={createOpen}
        editing={null}
        onClose={() => setCreateOpen(false)}
        onCreated={handleCreated}
      />
    </div>
  );
}
