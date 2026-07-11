import { useEffect, useMemo, useState } from 'react';
import { Button, Checkbox, Space, Tooltip } from 'antd';
import type {
  NotificationCategory,
  NotificationChannel,
  NotificationCategoryGroup,
  NotificationPreferenceItem,
  NotificationPreferenceView,
} from '@wen-saas/api-client';
import { SectionCard } from '../components/SectionCard';
import { CATEGORY_DESC, NOTIFY_UI_CHANNELS } from '../constants';
import { useNotifyPrefsQuery, useUpdateNotifyPrefs } from '../profileQueries';

type Matrix = Record<NotificationCategory, Partial<Record<NotificationChannel, boolean>>>;

function viewToMatrix(view: NotificationPreferenceView | undefined): Matrix {
  const m: Matrix = {} as Matrix;
  for (const cat of view?.categories ?? []) {
    if (!cat.category) continue;
    const row: Partial<Record<NotificationChannel, boolean>> = {};
    for (const ch of cat.channels ?? []) {
      if (ch.channel) row[ch.channel] = ch.enabled === true;
    }
    m[cat.category] = row;
  }
  return m;
}

function matrixToPreferences(m: Matrix): NotificationPreferenceItem[] {
  const out: NotificationPreferenceItem[] = [];
  for (const cat of Object.keys(m) as NotificationCategory[]) {
    const row = m[cat] ?? {};
    out.push({ category: cat, channel: 'STATION', enabled: true });
    out.push({ category: cat, channel: 'EMAIL', enabled: row.EMAIL === true });
    out.push({ category: cat, channel: 'SMS', enabled: row.SMS === true });
  }
  return out;
}

function sameMatrix(a: Matrix, b: Matrix): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

export function NotifySection({ onDirtyChange }: { onDirtyChange?: (dirty: boolean) => void }) {
  const { data, isLoading } = useNotifyPrefsQuery();
  const initial = useMemo(() => viewToMatrix(data), [data]);
  // 用 initial 序列化作为 key，让 server 数据变化（包含 mutation 后 invalidate
  // 拉到的新值）时整段 remount，避免在 effect 内 setState（被
  // react-hooks/set-state-in-effect 规则禁止）。
  const formKey = useMemo(() => JSON.stringify(initial), [initial]);
  return (
    <MatrixForm
      key={formKey}
      initial={initial}
      categories={data?.categories ?? []}
      isLoading={isLoading}
      {...(onDirtyChange ? { onDirtyChange } : {})}
    />
  );
}

function MatrixForm({
  initial,
  categories,
  isLoading,
  onDirtyChange,
}: {
  initial: Matrix;
  categories: NotificationCategoryGroup[];
  isLoading: boolean;
  onDirtyChange?: (dirty: boolean) => void;
}) {
  const [matrix, setMatrix] = useState<Matrix>(initial);
  const update = useUpdateNotifyPrefs();
  const dirty = !sameMatrix(matrix, initial);

  // 未保存改动上抛给 OpsProfilePage，用于切换左侧分区时 modal.confirm 拦截。
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);

  const toggle = (cat: NotificationCategory, ch: NotificationChannel) => {
    setMatrix((prev) => ({
      ...prev,
      [cat]: { ...prev[cat], [ch]: !(prev[cat]?.[ch] === true) },
    }));
  };

  const reset = () => setMatrix(initial);

  const save = () => {
    update.mutate({ preferences: matrixToPreferences(matrix) });
  };

  return (
    <SectionCard
      id="notify"
      title="通知偏好"
      hint="运营后台不接 H5 推送通道，仅四类通道。"
      extra={
        <Space>
          <Button size="small" onClick={reset} disabled={!dirty || isLoading}>
            重置
          </Button>
          <Button
            size="small"
            type="primary"
            onClick={save}
            loading={update.isPending}
            disabled={!dirty || isLoading}
          >
            保存
          </Button>
        </Space>
      }
    >
      <div style={{ overflowX: 'auto' }}>
        {/* eslint-disable-next-line no-restricted-syntax -- 通知偏好是「分类 × 通道」的 Checkbox 交互矩阵，列为动态通道、单元格是控件，非数据展示表，保留手写 table */}
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead>
            <tr style={{ background: 'var(--bg-muted)' }}>
              <th
                style={{
                  textAlign: 'left',
                  padding: '10px 12px',
                  minWidth: 220,
                  fontWeight: 500,
                  color: 'var(--text-secondary)',
                }}
              >
                分类
              </th>
              {NOTIFY_UI_CHANNELS.map((c) => (
                <th
                  key={c.key}
                  style={{
                    textAlign: 'center',
                    padding: '10px 12px',
                    minWidth: 110,
                    fontWeight: 500,
                    color: 'var(--text-secondary)',
                  }}
                >
                  <div>{c.label}</div>
                  {c.hint ? (
                    <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>{c.hint}</div>
                  ) : null}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {categories.map((cat) => {
              const code = cat.category;
              if (!code) return null;
              const row = matrix[code] ?? {};
              return (
                <tr key={code} style={{ borderTop: '1px solid var(--border)' }}>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ fontWeight: 500 }}>{cat.label ?? code}</div>
                    <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 2 }}>
                      {CATEGORY_DESC[code]}
                    </div>
                  </td>
                  {NOTIFY_UI_CHANNELS.map((c) => {
                    if (c.placeholder) {
                      return (
                        <td key={c.key} style={{ textAlign: 'center', padding: '10px 12px' }}>
                          <Tooltip title="通道占位，敬请期待">
                            <Checkbox disabled />
                          </Tooltip>
                        </td>
                      );
                    }
                    const ch = c.key as NotificationChannel;
                    const checked = ch === 'STATION' ? true : row[ch] === true;
                    return (
                      <td key={c.key} style={{ textAlign: 'center', padding: '10px 12px' }}>
                        <Checkbox
                          checked={checked}
                          disabled={c.locked}
                          onChange={() => {
                            if (!c.locked) toggle(code, ch);
                          }}
                        />
                      </td>
                    );
                  })}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </SectionCard>
  );
}
