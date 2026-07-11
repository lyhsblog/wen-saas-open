import { type ReactNode } from 'react';
import { Badge, Button, Empty } from 'antd';
import { StatusPill, type StatusBadge } from './StatusPill';

export type TaskStatus = 'running' | 'done' | 'failed' | 'pending' | 'canceled';
/** 任务类型；'import' / 'export' 是底座给的默认色，业务方可塞任意 string。 */
export type TaskKind = 'import' | 'export' | (string & {});

export type TaskItem = {
  id: string;
  kind: TaskKind;
  name: string;
  /** 发起人（可选展示在副标题）。 */
  who?: string;
  status: TaskStatus;
  /** 0-100；仅当 status='running' 时画进度条。 */
  progress?: number;
  /** "约 30 秒"等粗粒度文案；running 时副标题里展示。 */
  eta?: string;
  /** "今天 10:22" / "昨天 17:10"；非 running 时副标题里展示。 */
  finish?: string;
  /** 结果/失败原因，渲染在副标题下方一行（done 用次要色，failed 用 danger）。 */
  result?: string;
  /** kind 不在默认色里时，调用方可塞自定义图标。 */
  icon?: ReactNode;
};

export type TaskAction = {
  key: string;
  label: string;
  icon?: ReactNode;
  danger?: boolean;
  disabled?: boolean;
};

export type TaskFilter = TaskStatus | 'all';

export type TaskCenterProps = {
  tasks: TaskItem[];
  /** 标题区文本。默认"任务中心"。 */
  title?: ReactNode;
  /** 标题区副文（"任务保留 30 天"等）。 */
  subtitle?: ReactNode;
  /** 顶部右侧操作（如"新建导入 / 导出"按钮）。 */
  headerExtra?: ReactNode;
  /**
   * 顶部 filter chips 当前值；不传则不渲染 chips，全部展示。
   * 由调用方持有 state，避免组件内外状态双向同步。
   */
  filter?: TaskFilter;
  onFilterChange?: (next: TaskFilter) => void;
  /** chips 右侧的辅助文案。 */
  filterHint?: ReactNode;
  /** 每个任务行右侧的操作按钮列表。 */
  actionsForTask?: (task: TaskItem) => TaskAction[];
  /** 操作按钮触发回调；actionKey 即 TaskAction.key。 */
  onAction?: (task: TaskItem, actionKey: string) => void;
  emptyDescription?: ReactNode;
};

type StatusMeta = { label: string; status: 'success' | 'error' | 'processing' | 'default' };

const STATUS_META: Record<TaskStatus, StatusMeta> = {
  running: { label: '进行中', status: 'processing' },
  done: { label: '已完成', status: 'success' },
  failed: { label: '已失败', status: 'error' },
  pending: { label: '等待中', status: 'default' },
  canceled: { label: '已取消', status: 'default' },
};

/**
 * 状态 → 顶部 filter 桶。chips 仅展示 全部/进行中/已完成/已失败 四档，
 * 故把 pending(等待中) 并入「进行中」(活跃未终态)、canceled(已取消) 并入「已失败」(非成功终态)，
 * 保证「全部 = 各桶之和」；行内仍按各自 STATUS_META 显示真实标签（等待中 / 已取消）。
 */
function filterBucket(status: TaskStatus): TaskFilter {
  if (status === 'pending') return 'running';
  if (status === 'canceled') return 'failed';
  return status;
}

function countByStatus(tasks: TaskItem[]): Record<TaskFilter, number> {
  const out: Record<TaskFilter, number> = {
    all: tasks.length,
    running: 0,
    done: 0,
    failed: 0,
    pending: 0,
    canceled: 0,
  };
  for (const t of tasks) out[filterBucket(t.status)]++;
  return out;
}

function KindIcon({ kind, icon }: { kind: TaskKind; icon?: ReactNode }) {
  const isImport = kind === 'import';
  const isExport = kind === 'export';
  const bg = isImport
    ? 'oklch(0.94 0.05 145)'
    : isExport
      ? 'oklch(0.94 0.04 240)'
      : 'var(--bg-muted)';
  const color = isImport
    ? 'oklch(0.45 0.14 145)'
    : isExport
      ? 'oklch(0.45 0.16 240)'
      : 'var(--text-secondary)';
  return (
    <div
      style={{
        width: 38,
        height: 38,
        borderRadius: 'var(--r-md)',
        flexShrink: 0,
        background: bg,
        color,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 13,
        fontWeight: 700,
      }}
    >
      {icon ?? (isImport ? '↑' : isExport ? '↓' : '·')}
    </div>
  );
}

/**
 * 任务中心通用展示组件。
 *
 * 设计：纯展示；数据 / filter / 轮询 / 拉新一律由调用方注入，避免组件内藏 state。
 */
export function TaskCenter({
  tasks,
  title = '任务中心',
  subtitle,
  headerExtra,
  filter,
  onFilterChange,
  filterHint,
  actionsForTask,
  onAction,
  emptyDescription = '暂无任务',
}: TaskCenterProps) {
  const counts = countByStatus(tasks);
  const filtered =
    filter && filter !== 'all' ? tasks.filter((t) => filterBucket(t.status) === filter) : tasks;
  const showFilters = filter !== undefined && onFilterChange !== undefined;

  return (
    <div
      style={{
        padding: 24,
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        background: 'var(--bg)',
        height: '100%',
        overflowY: 'auto',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{ fontSize: 20, fontWeight: 600, color: 'var(--text)' }}>{title}</div>
        {subtitle != null && (
          <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>{subtitle}</span>
        )}
        {counts.running > 0 && (
          <Badge status="processing" text={`${counts.running} 进行中`} style={{ fontSize: 12 }} />
        )}
        <div style={{ flex: 1 }} />
        {headerExtra}
      </div>

      <div
        style={{
          background: 'var(--bg-elev)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--r-md)',
          overflow: 'hidden',
        }}
      >
        {showFilters && (
          <div
            style={{
              padding: '12px 16px',
              borderBottom: '1px solid var(--divider)',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
          >
            <FilterPill
              options={[
                { v: 'all', l: `全部 ${counts.all}` },
                { v: 'running', l: `进行中 ${counts.running}` },
                { v: 'done', l: `已完成 ${counts.done}` },
                { v: 'failed', l: `已失败 ${counts.failed}` },
              ]}
              value={filter ?? 'all'}
              onChange={onFilterChange}
            />
            <div style={{ flex: 1 }} />
            {filterHint != null && (
              <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>{filterHint}</span>
            )}
          </div>
        )}

        {filtered.length === 0 ? (
          <div style={{ padding: '40px 0' }}>
            <Empty description={emptyDescription} />
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {filtered.map((t, i) => (
              <TaskRow
                key={t.id}
                task={t}
                first={i === 0}
                actions={actionsForTask?.(t) ?? []}
                {...(onAction ? { onAction } : {})}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function FilterPill<V extends string>({
  options,
  value,
  onChange,
}: {
  options: { v: V; l: ReactNode }[];
  value: V;
  onChange: (v: V) => void;
}) {
  return (
    <div
      role="tablist"
      style={{
        display: 'flex',
        gap: 4,
        padding: 3,
        background: 'var(--bg-muted)',
        borderRadius: 'var(--r-pill)',
      }}
    >
      {options.map((o) => {
        const active = value === o.v;
        return (
          <button
            key={o.v}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(o.v)}
            style={{
              padding: '5px 12px',
              fontSize: 12,
              fontWeight: active ? 600 : 500,
              border: 'none',
              borderRadius: 'var(--r-pill)',
              background: active ? '#fff' : 'transparent',
              color: active ? 'var(--text)' : 'var(--text-secondary)',
              boxShadow: active ? '0 1px 2px rgba(0,0,0,0.08)' : 'none',
              cursor: 'pointer',
            }}
          >
            {o.l}
          </button>
        );
      })}
    </div>
  );
}

function TaskRow({
  task,
  first,
  actions,
  onAction,
}: {
  task: TaskItem;
  first: boolean;
  actions: TaskAction[];
  onAction?: (task: TaskItem, actionKey: string) => void;
}) {
  const meta = STATUS_META[task.status];
  const isRunning = task.status === 'running';
  const isFailed = task.status === 'failed';
  const progress = Math.max(0, Math.min(100, task.progress ?? 0));

  return (
    <div
      style={{
        padding: '14px 18px',
        borderTop: first ? 'none' : '1px solid var(--divider)',
        display: 'flex',
        alignItems: 'center',
        gap: 14,
      }}
    >
      <KindIcon kind={task.kind} icon={task.icon} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span
            style={{
              fontSize: 14,
              fontWeight: 600,
              color: 'var(--text)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
            title={task.name}
          >
            {task.name}
          </span>
          <Badge status={meta.status} text={meta.label} style={{ fontSize: 12 }} />
        </div>
        <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>
          {task.who != null && <>发起人 {task.who} · </>}
          {isRunning ? task.eta != null && <>预计还需 {task.eta}</> : task.finish}
        </div>
        {isRunning && (
          <div style={{ marginTop: 6, display: 'flex', alignItems: 'center', gap: 8 }}>
            <div
              role="progressbar"
              aria-valuenow={progress}
              aria-valuemin={0}
              aria-valuemax={100}
              style={{ flex: 1, height: 4, background: 'var(--bg-muted)', borderRadius: 2 }}
            >
              <div
                style={{
                  width: `${progress}%`,
                  height: '100%',
                  background: 'var(--brand)',
                  borderRadius: 2,
                }}
              />
            </div>
            <span
              style={{
                fontSize: 11,
                color: 'var(--text-tertiary)',
                fontFamily: 'var(--font-mono, monospace)',
              }}
            >
              {progress}%
            </span>
          </div>
        )}
        {!isRunning && task.result != null && (
          <div
            style={{
              fontSize: 12,
              color: isFailed ? 'var(--danger)' : 'var(--text-secondary)',
              marginTop: 4,
            }}
          >
            {task.result}
          </div>
        )}
      </div>
      {actions.length > 0 && (
        <div style={{ display: 'flex', gap: 6 }}>
          {actions.map((a) => (
            <Button
              key={a.key}
              size="small"
              danger={a.danger ?? false}
              disabled={a.disabled ?? false}
              icon={a.icon}
              onClick={() => onAction?.(task, a.key)}
            >
              {a.label}
            </Button>
          ))}
        </div>
      )}
    </div>
  );
}

/** 把 status 渲染为 StatusPill（业务方需要列表头部统计时方便些）。 */
export function TaskStatusTag({ status }: { status: TaskStatus }) {
  const meta = STATUS_META[status];
  const kindMap: Record<StatusMeta['status'], StatusBadge['kind']> = {
    success: 'success',
    error: 'danger',
    processing: 'info',
    default: 'neutral',
  };
  return <StatusPill badge={{ kind: kindMap[meta.status], text: meta.label, color: '' }} />;
}
