import { useMemo, useState } from 'react';
import { CountChip, ErrorState, shouldSkipRowNav } from '@wen-saas/shared-ui';
import { Button, Empty, Input, Select, Spin } from 'antd';
import { NotificationOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { opsBusinessModuleApi, opsNotificationTemplateApi } from '@wen-saas/api-client';
import type {
  ListNotificationTemplatesQuery,
  NotificationTemplateListItem,
  NotificationTemplateStatus,
} from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { StatusPill } from '@wen-saas/shared-ui';
import { channelLabel } from './utils/meta';
import { OpsNotificationTemplateFormModal } from './OpsNotificationTemplateFormModal';
import { statusBadge, STATUS_FILTER_OPTIONS } from './utils/meta';

type Props = {
  selectedCode: string | null;
  onSelect: (code: string) => void;
  compact: boolean;
};

/** 平台内置模块 code → 展示名；业务模块 code 走业务模块目录兜底，映射不到回退原 code。 */
const MODULE_LABELS: Record<string, string> = {
  core: '系统通用',
  ops: '平台运营',
  compliance: '数据合规',
  system: '系统设置',
  member: '成员管理',
  audit: '操作日志',
};

type Group = { module: string; items: NotificationTemplateListItem[] };

function groupByModule(items: readonly NotificationTemplateListItem[]): Group[] {
  const map = new Map<string, NotificationTemplateListItem[]>();
  for (const it of items) {
    const key = it.moduleCode || '未分组';
    const arr = map.get(key) ?? [];
    arr.push(it);
    map.set(key, arr);
  }
  return [...map.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([module, list]) => ({
      module,
      items: [...list].sort((a, b) => a.templateCode.localeCompare(b.templateCode)),
    }));
}

export function OpsNotificationTemplateListPane({ selectedCode, onSelect, compact }: Props) {
  const [createOpen, setCreateOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<NotificationTemplateStatus | undefined>(undefined);

  const query: ListNotificationTemplatesQuery = {
    ...(keyword ? { keyword } : {}),
    ...(status != null ? { status } : {}),
  };

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['ops', 'notification-templates', query],
    queryFn: () => opsNotificationTemplateApi(http).listTemplates(query),
  });

  // 业务模块目录：仅用于把分组头的业务模块 code 翻译成展示名（内置模块走 MODULE_LABELS）。
  const { data: bizModules } = useQuery({
    queryKey: ['ops', 'modules'],
    queryFn: () => opsBusinessModuleApi(http).listModules(),
    staleTime: 5 * 60 * 1000,
  });
  const moduleNameOf = useMemo(() => {
    const byCode = new Map((bizModules ?? []).map((m) => [m.code, m.name]));
    return (code: string) => MODULE_LABELS[code] ?? byCode.get(code) ?? code;
  }, [bizModules]);

  const groups = useMemo(() => groupByModule(data ?? []), [data]);
  const total = data?.length ?? 0;

  const handleCreated = (code: string) => {
    setCreateOpen(false);
    onSelect(code);
  };

  const toolbar = (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <NotificationOutlined style={{ color: 'var(--text-tertiary)' }} />
      <span style={{ fontWeight: 600, fontSize: 15 }}>通知模板</span>
      {data && <CountChip>{total}</CountChip>}
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
        placeholder="搜索名称 / 编码"
        allowClear
        style={compact ? undefined : { width: 260 }}
        onSearch={(v) => setKeyword(v)}
      />
      <Select<NotificationTemplateStatus>
        size={compact ? 'small' : 'middle'}
        style={{ width: 120 }}
        placeholder="全部状态"
        allowClear
        value={status ?? null}
        onChange={(v) => setStatus(v ?? undefined)}
        options={STATUS_FILTER_OPTIONS}
      />
    </div>
  );

  const renderItem = (t: NotificationTemplateListItem) => {
    const isSelected = t.templateCode === selectedCode;
    return (
      <div
        key={t.templateCode}
        onClick={() => {
          if (shouldSkipRowNav()) return;
          onSelect(t.templateCode);
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
            {t.name}
          </span>
          {t.preset && <CountChip tone="brand">预置</CountChip>}
          <StatusPill badge={statusBadge(t.status)} />
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
          <code style={{ fontFamily: 'var(--font-mono)' }}>{t.templateCode}</code>
          {t.defaultChannels.map((c) => (
            <CountChip key={c}>{channelLabel(c)}</CountChip>
          ))}
        </div>
      </div>
    );
  };

  const list = isLoading ? (
    <div style={{ padding: 48, textAlign: 'center' }}>
      <Spin />
    </div>
  ) : isError ? (
    <ErrorState
      title="加载列表失败"
      primaryAction={{ label: '重试', onClick: () => void refetch() }}
    />
  ) : total === 0 ? (
    <Empty
      description="暂无模板，点击右上角新建"
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      style={{ padding: '48px 0' }}
    />
  ) : (
    groups.map((g) => (
      <div key={g.module}>
        <div
          style={{
            padding: '8px 16px',
            fontSize: 12,
            fontWeight: 600,
            color: 'var(--text-tertiary)',
            background: 'var(--bg-muted)',
            position: 'sticky',
            top: 0,
            zIndex: 1,
          }}
        >
          {moduleNameOf(g.module)}（{g.items.length}）
        </div>
        {g.items.map(renderItem)}
      </div>
    ))
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
      <OpsNotificationTemplateFormModal
        open={createOpen}
        editing={null}
        onClose={() => setCreateOpen(false)}
        onSaved={handleCreated}
      />
    </div>
  );
}
