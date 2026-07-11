import { useMemo, useState } from 'react';
import { CountChip, ErrorState, shouldSkipRowNav } from '@wen-saas/shared-ui';
import { Button, Empty, Input, Select, Spin } from 'antd';
import { PlusOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { opsRecommendedRoleAdminApi } from '@wen-saas/api-client';
import type { ListRecRolesQuery, RecRoleListItem, RecRoleStatus } from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { StatusPill } from '@wen-saas/shared-ui';
import { OpsRecRoleFormModal } from './OpsRecRoleFormModal';
import {
  recRoleStatusBadge,
  REC_ROLE_STATUS_FILTER_OPTIONS,
  REC_ROLE_STATUS_ORDER,
} from './utils/recRoleMeta';

type Props = {
  selectedId: string | null;
  onSelect: (id: string) => void;
  compact: boolean;
};

function sortTemplates(items: readonly RecRoleListItem[]): RecRoleListItem[] {
  return [...items].sort((a, b) => {
    const so = (REC_ROLE_STATUS_ORDER[a.status] ?? 99) - (REC_ROLE_STATUS_ORDER[b.status] ?? 99);
    if (so !== 0) return so;
    return b.createdAt.localeCompare(a.createdAt); // 同状态按创建时间倒序
  });
}

export function OpsRecRoleListPane({ selectedId, onSelect, compact }: Props) {
  const [createOpen, setCreateOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<RecRoleStatus | undefined>(undefined);

  const query: ListRecRolesQuery = {
    ...(keyword ? { keyword } : {}),
    ...(status != null ? { status } : {}),
  };

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['ops', 'rec-roles', query],
    queryFn: () => opsRecommendedRoleAdminApi(http).listTemplates(query),
  });

  const items = useMemo(() => sortTemplates(data ?? []), [data]);

  const handleCreated = (id: string) => {
    setCreateOpen(false);
    onSelect(id);
  };

  const toolbar = (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <SafetyCertificateOutlined style={{ color: 'var(--text-tertiary)' }} />
      <span style={{ fontWeight: 600, fontSize: 15 }}>推荐角色模板</span>
      {data && <CountChip>{items.length}</CountChip>}
      <div style={{ flex: 1 }} />
      <Button
        type="primary"
        size="small"
        icon={<PlusOutlined />}
        onClick={() => setCreateOpen(true)}
      >
        新建模板
      </Button>
    </div>
  );

  const filterBar = (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
      <Input.Search
        size={compact ? 'small' : 'middle'}
        placeholder="搜索模板名称"
        allowClear
        style={compact ? undefined : { width: 260 }}
        onSearch={(v) => setKeyword(v)}
      />
      <Select<RecRoleStatus>
        size={compact ? 'small' : 'middle'}
        style={{ width: 120 }}
        placeholder="全部状态"
        allowClear
        value={status ?? null}
        onChange={(v) => setStatus(v ?? undefined)}
        options={REC_ROLE_STATUS_FILTER_OPTIONS}
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
      description="暂无推荐角色模板，点击右上角新建"
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      style={{ padding: '48px 0' }}
    />
  ) : (
    items.map((r) => {
      const isSelected = r.id === selectedId;
      return (
        <div
          key={r.id}
          onClick={() => {
            if (shouldSkipRowNav()) return;
            onSelect(r.id);
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
              {r.name}
            </span>
            <StatusPill badge={recRoleStatusBadge(r.status)} />
          </div>
          {r.description && (
            <div
              style={{
                fontSize: 12,
                color: 'var(--text-tertiary)',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                marginBottom: 6,
              }}
            >
              {r.description}
            </div>
          )}
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
            <CountChip>{r.packageCodes.length} 套餐</CountChip>
            <CountChip>{r.permissionCount} 权限</CountChip>
            <CountChip>{r.activatedTenantCount} 复制</CountChip>
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
      <OpsRecRoleFormModal
        open={createOpen}
        editing={null}
        onClose={() => setCreateOpen(false)}
        onCreated={handleCreated}
      />
    </div>
  );
}
