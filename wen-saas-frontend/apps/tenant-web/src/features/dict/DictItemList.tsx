import { useMemo, useState } from 'react';
import { Button, Empty, Input, Popconfirm, Space } from 'antd';
import { StatusPill, toneBadge } from '@wen-saas/shared-ui';
import { PermissionGate, ReadonlyButton } from '@wen-saas/permissions';
import {
  DownOutlined,
  RightOutlined,
  SearchOutlined,
  PlusOutlined,
  ExpandAltOutlined,
  ShrinkOutlined,
} from '@ant-design/icons';
import type { DictGroup, DictItem } from '@wen-saas/domain';
import { ImportEntryButton } from '../taskCenter/ImportEntryButton';
import { ExportEntryButton } from '../taskCenter/ExportEntryButton';

type Props = {
  group: DictGroup;
  items: DictItem[];
  onCreateItem: () => void;
  onEditItem: (item: DictItem) => void;
  onToggleStatus: (item: DictItem) => void;
  onDeleteItem: (item: DictItem) => void;
  /** 数据导入完成后刷新字典项。 */
  onImported: () => void;
};

function matchesSearch(item: DictItem, q: string): boolean {
  if (!q) return true;
  const name = (item.name ?? '').toLowerCase();
  const code = (item.code ?? '').toLowerCase();
  return name.includes(q) || code.includes(q);
}

function filterTree(items: DictItem[], q: string): DictItem[] {
  if (!q) return items;
  const out: DictItem[] = [];
  for (const it of items) {
    const kidsFiltered = filterTree(it.children ?? [], q);
    if (matchesSearch(it, q) || kidsFiltered.length > 0) {
      out.push({ ...it, children: kidsFiltered });
    }
  }
  return out;
}

function collectAllIds(items: DictItem[], acc: number[]): number[] {
  for (const it of items) {
    if (it.id != null) acc.push(it.id);
    if (it.children && it.children.length > 0) collectAllIds(it.children, acc);
  }
  return acc;
}

export function DictItemList({
  group,
  items,
  onCreateItem,
  onEditItem,
  onToggleStatus,
  onDeleteItem,
  onImported,
}: Props) {
  const [search, setSearch] = useState('');
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  const q = search.trim().toLowerCase();
  const visible = useMemo(() => filterTree(items, q), [items, q]);
  const allIds = useMemo(() => collectAllIds(items, []), [items]);

  const renderItem = (it: DictItem, depth: number): React.ReactNode => {
    const id = it.id;
    if (id == null) return null;
    const kids = it.children ?? [];
    const open = expanded.has(id);
    const paused = it.status === 0;
    return (
      <div key={id}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            padding: '10px 12px',
            borderBottom: '1px solid var(--divider)',
            background: paused ? 'var(--bg-muted)' : 'var(--bg-elev)',
          }}
        >
          <div style={{ width: depth * 20 + 4 }} />
          {kids.length > 0 ? (
            <span
              style={{ width: 20, cursor: 'pointer' }}
              onClick={() => {
                setExpanded((prev) => {
                  const next = new Set(prev);
                  if (next.has(id)) next.delete(id);
                  else next.add(id);
                  return next;
                });
              }}
            >
              {open ? (
                <DownOutlined style={{ fontSize: 10, color: 'var(--text-tertiary)' }} />
              ) : (
                <RightOutlined style={{ fontSize: 10, color: 'var(--text-tertiary)' }} />
              )}
            </span>
          ) : (
            <span style={{ width: 20 }} />
          )}
          <span
            style={{
              fontWeight: 500,
              fontSize: 13,
              color: paused ? 'var(--text-tertiary)' : 'var(--text)',
              minWidth: 140,
              marginLeft: 8,
            }}
          >
            {it.name}
          </span>
          <code
            style={{
              marginLeft: 8,
              fontSize: 11,
              color: 'var(--text-tertiary)',
              padding: '1px 6px',
              background: 'var(--bg-muted)',
              borderRadius: 'var(--r-sm)',
            }}
          >
            {it.code}
          </code>
          {it.remark && (
            <span
              title={it.remark}
              style={{
                marginLeft: 8,
                fontSize: 11,
                color: 'var(--text-tertiary)',
                maxWidth: 240,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {it.remark}
            </span>
          )}
          {paused && (
            <span style={{ marginLeft: 8 }}>
              <StatusPill badge={toneBadge('neutral', '已停用')} />
            </span>
          )}
          <div style={{ flex: 1 }} />
          <span style={{ fontSize: 11, color: 'var(--text-tertiary)', marginRight: 12 }}>
            排序 {it.sortOrder ?? 0}
          </span>
          <Space size={2}>
            <PermissionGate permission="dict.item.update">
              <ReadonlyButton type="text" size="small" onClick={() => onEditItem(it)}>
                编辑
              </ReadonlyButton>
              <ReadonlyButton type="text" size="small" onClick={() => onToggleStatus(it)}>
                {paused ? '启用' : '停用'}
              </ReadonlyButton>
            </PermissionGate>
            {it.source !== 'SYSTEM' && (
              <PermissionGate permission="dict.item.delete">
                <Popconfirm
                  title={`确认删除字典项「${it.name}」？`}
                  onConfirm={() => onDeleteItem(it)}
                  okText="删除"
                  okButtonProps={{ danger: true }}
                  cancelText="取消"
                >
                  <ReadonlyButton type="text" size="small" danger>
                    删除
                  </ReadonlyButton>
                </Popconfirm>
              </PermissionGate>
            )}
          </Space>
        </div>
        {open && kids.map((k) => renderItem(k, depth + 1))}
      </div>
    );
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <div
        style={{
          padding: '12px 24px',
          background: 'var(--bg-elev)',
          borderBottom: '1px solid var(--divider)',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <Input
          allowClear
          placeholder="搜索字典项（名称 / 编码）"
          prefix={<SearchOutlined style={{ color: 'var(--text-tertiary)' }} />}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ width: 260 }}
        />
        {group.supportHierarchy && (
          <>
            <Button
              size="middle"
              icon={<ExpandAltOutlined />}
              onClick={() => setExpanded(new Set(allIds))}
            >
              展开全部
            </Button>
            <Button size="middle" icon={<ShrinkOutlined />} onClick={() => setExpanded(new Set())}>
              收起全部
            </Button>
          </>
        )}
        <div style={{ flex: 1 }} />
        <PermissionGate permission="dict.item.list">
          <ExportEntryButton
            objectCode="dict"
            size="middle"
            label="导出字典项"
            filters={{ groupCode: group.code ?? '', includeDisabled: 'true' }}
            filterSummary={`分组=${group.name ?? group.code ?? ''}`}
          />
        </PermissionGate>
        <PermissionGate permission="dict.item.create">
          <ImportEntryButton
            entityCode="sys.dict"
            label="导入字典项"
            size="middle"
            onImported={onImported}
          />
        </PermissionGate>
        <PermissionGate permission="dict.item.create">
          <ReadonlyButton type="primary" icon={<PlusOutlined />} onClick={onCreateItem}>
            新增字典项
          </ReadonlyButton>
        </PermissionGate>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: 24, background: 'var(--bg)' }}>
        {visible.length > 0 ? (
          <div
            style={{
              background: 'var(--bg-elev)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--r-md)',
              overflow: 'hidden',
            }}
          >
            {visible.map((it) => renderItem(it, 0))}
          </div>
        ) : (
          <Empty
            description={q ? '没有匹配的字典项' : '该分组暂无字典项'}
            style={{ marginTop: 60 }}
          >
            {!q && (
              <PermissionGate permission="dict.item.create">
                <ReadonlyButton type="primary" icon={<PlusOutlined />} onClick={onCreateItem}>
                  新增字典项
                </ReadonlyButton>
              </PermissionGate>
            )}
          </Empty>
        )}
      </div>
    </div>
  );
}
