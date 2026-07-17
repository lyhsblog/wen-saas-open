import { type ReactNode, useMemo } from 'react';
import { Button, Empty, Table } from 'antd';
import type { ColumnsType, TablePaginationConfig, TableProps } from 'antd/lib/table';
import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import { shouldSkipRowNav } from './rowNav';

export type DataTableDensity = 'compact' | 'standard' | 'loose';

export type DataTableEmpty = {
  description?: ReactNode;
  /** 空态下的引导按钮（"新增第一个"）。 */
  action?: ReactNode;
};

/** 极简分页底栏的受控参数（配合 useListQuery 等外部分页状态）。 */
export type SimplePagination = {
  total: number;
  /** 1-based。 */
  page: number;
  pageSize: number;
  onChange: (page: number) => void;
};

export type DataTableProps<T> = {
  /** 标题区文本，可与 toolbar 共存。 */
  title?: ReactNode;
  /** 标题区右侧附加（搜索、筛选、按钮等）。 */
  toolbar?: ReactNode;
  columns: ColumnsType<T>;
  dataSource: readonly T[] | undefined;
  loading?: boolean;
  /** 行 key 取值。默认从 `record.id`、`record.code` 兜底；都没有时退到 index。 */
  rowKey?: TableProps<T>['rowKey'];
  pagination?: TablePaginationConfig | false;
  rowSelection?: TableProps<T>['rowSelection'];
  /** 行点击事件，配合 hover 高亮使用。 */
  onRowClick?: (record: T, index: number) => void;
  /** 密度。compact→small、standard→middle、loose→large；默认 standard。 */
  density?: DataTableDensity;
  /** 自定义空态文案 / 操作按钮。 */
  empty?: DataTableEmpty;
  /** 透传 scroll，开 fixed header 用。 */
  scroll?: TableProps<T>['scroll'];
  /** 透传 className，便于业务方覆盖样式。 */
  className?: string;
  /**
   * 主从列表：高亮「详情已打开」的那一行（无勾选框）。
   * 命中行套 AntD 自带的 .ant-table-row-selected 类，背景走 Table.rowSelectedBg 令牌，
   * 两端主题 / 品牌换肤自动跟随，不写死颜色。
   */
  selectedRowKey?: string | number;
  /** 极简分页底栏（「共 N 条 · 第 X-Y 条」+ ‹ ›）。传入时 AntD 内置分页强制关闭，与 pagination 二选一。 */
  simplePagination?: SimplePagination;
};

function densityToSize(d: DataTableDensity | undefined): TableProps<unknown>['size'] {
  switch (d) {
    case 'compact':
      return 'small';
    case 'loose':
      return 'large';
    default:
      return 'middle';
  }
}

function defaultRowKey<T>(record: T, index?: number): string | number {
  const r = record as Record<string, unknown>;
  if (typeof r.id === 'string' || typeof r.id === 'number') return r.id;
  if (typeof r.code === 'string') return r.code;
  return index ?? 0;
}

/** 按 rowKey 语义求某行的 key，与 AntD Table 的取值保持一致（供 selectedRowKey 匹配）。 */
function resolveKey<T>(record: T, index: number, rowKey: TableProps<T>['rowKey'] | undefined) {
  if (typeof rowKey === 'function') return rowKey(record, index);
  if (typeof rowKey === 'string') {
    const v = (record as Record<string, unknown>)[rowKey];
    if (typeof v === 'string' || typeof v === 'number') return v;
  }
  return defaultRowKey(record, index);
}

/** 极简分页底栏："共 N 条 · 第 X-Y 条" + 上一页/下一页。 */
function SimpleFooter({ total, page, pageSize, onChange }: SimplePagination) {
  const totalPages = total ? Math.max(1, Math.ceil(total / pageSize)) : 1;
  const from = total === 0 ? 0 : (page - 1) * pageSize + 1;
  const to = Math.min(page * pageSize, total);
  return (
    <div
      style={{
        padding: '10px 24px',
        borderTop: '1px solid var(--divider)',
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        fontSize: 12,
        color: 'var(--text-tertiary)',
        flexShrink: 0,
      }}
    >
      <span style={{ fontVariantNumeric: 'tabular-nums' }}>
        共 {total} 条 · 第 {from}-{to} 条
      </span>
      <div style={{ flex: 1 }} />
      <Button
        size="small"
        type="text"
        aria-label="上一页"
        icon={<LeftOutlined />}
        disabled={page <= 1}
        onClick={() => onChange(Math.max(1, page - 1))}
      />
      <span
        style={{
          fontVariantNumeric: 'tabular-nums',
          color: 'var(--text-secondary)',
          minWidth: 40,
          textAlign: 'center',
        }}
      >
        {page} / {totalPages}
      </span>
      <Button
        size="small"
        type="text"
        aria-label="下一页"
        icon={<RightOutlined />}
        disabled={page >= totalPages}
        onClick={() => onChange(Math.min(totalPages, page + 1))}
      />
    </div>
  );
}

/**
 * 列表场景统一壳：上方标题/工具栏，下方表格。
 * 不做"列设置 / 列固定 / 自定义筛选条"等高级能力——这些放未来 `<DataTablePro />` 再加。
 */
export function DataTable<T extends object>({
  title,
  toolbar,
  columns,
  dataSource,
  loading,
  rowKey,
  pagination,
  rowSelection,
  onRowClick,
  density = 'standard',
  empty,
  scroll,
  className,
  selectedRowKey,
  simplePagination,
}: DataTableProps<T>) {
  const showHeader = title != null || toolbar != null;

  const locale = useMemo(
    () => ({
      emptyText: (
        <Empty description={empty?.description ?? '暂无数据'} style={{ padding: '32px 0' }}>
          {empty?.action}
        </Empty>
      ),
    }),
    [empty?.description, empty?.action],
  );

  return (
    <div
      className={className}
      style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}
    >
      {showHeader && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '12px 24px',
            background: 'var(--bg-elev)',
            borderBottom: '1px solid var(--divider)',
          }}
        >
          {title != null && (
            <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text)' }}>{title}</div>
          )}
          <div style={{ flex: 1 }} />
          {toolbar}
        </div>
      )}
      <div style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: '12px 24px' }}>
        <Table<T>
          columns={columns}
          dataSource={dataSource}
          loading={loading ?? false}
          rowKey={rowKey ?? defaultRowKey}
          pagination={
            simplePagination
              ? false
              : (pagination ?? { showSizeChanger: true, showTotal: (n) => `共 ${n} 条` })
          }
          size={densityToSize(density)}
          locale={locale}
          {...(selectedRowKey != null
            ? {
                rowClassName: (record: T, index: number) =>
                  resolveKey(record, index, rowKey) === selectedRowKey
                    ? 'ant-table-row-selected'
                    : '',
              }
            : {})}
          {...(rowSelection ? { rowSelection } : {})}
          {...(scroll ? { scroll } : {})}
          {...(onRowClick
            ? {
                onRow: (record, index) => ({
                  onClick: (e) => {
                    // 正在划词复制时不跳转，让原生文本选择 + Ctrl/⌘+C 生效。
                    if (shouldSkipRowNav()) return;
                    // 点击落在行内按钮/链接（操作列、Dropdown 触发器等）时不触发行导航。
                    if ((e.target as HTMLElement).closest('button, a')) return;
                    onRowClick(record, index ?? 0);
                  },
                  style: { cursor: 'pointer' },
                }),
              }
            : {})}
        />
      </div>
      {simplePagination && <SimpleFooter {...simplePagination} />}
    </div>
  );
}
