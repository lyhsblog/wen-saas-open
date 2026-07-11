import { useEffect, useState } from 'react';
import { Alert, App, Button, Card, Empty, Input, InputNumber, Space, Spin, Switch } from 'antd';
import { useQuery } from '@tanstack/react-query';
import {
  opsSettingApi,
  type SettingCategory,
  type SettingCategoryView,
  type SettingItem,
} from '@wen-saas/api-client';
import { StatusPill, toneBadge } from '@wen-saas/shared-ui';
import { http } from '../../../lib/http';
import { SETTING_CATEGORY_META, SETTING_CATEGORY_ORDER } from '../utils/sysadminMeta';
import { useUpdateSettingCategory } from '../hooks/useSettingMutations';

/** 把动态参数值安全转成字符串（对象走 JSON，避免 [object Object]）。 */
function toStr(v: unknown): string {
  if (v == null) return '';
  if (typeof v === 'string') return v;
  if (typeof v === 'number' || typeof v === 'boolean') return String(v);
  return JSON.stringify(v);
}

export function ParamsTab({ onDirtyChange }: { onDirtyChange?: (dirty: boolean) => void }) {
  const { modal } = App.useApp();
  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'settings'],
    queryFn: () => opsSettingApi(http).listAll(),
  });

  const [editing, setEditing] = useState<SettingCategory | null>(null);
  const [draft, setDraft] = useState<Record<string, unknown>>({});
  // 进入编辑时的快照，用于 isDirty 比对（draft 改回原值即视为不脏）。
  const [snapshot, setSnapshot] = useState<Record<string, unknown>>({});

  const update = useUpdateSettingCategory(editing ?? 'platform_info');

  const dirty = editing != null && JSON.stringify(draft) !== JSON.stringify(snapshot);

  // 未保存改动上抛给 OpsSysAdminPage，用于切换顶部 Tab 时 modal.confirm 拦截。
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);

  if (isLoading) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }

  const byCategory = new Map<SettingCategory, SettingCategoryView>();
  for (const c of data ?? []) byCategory.set(c.category, c);
  const ordered = SETTING_CATEGORY_ORDER.filter((c) => byCategory.has(c));

  const startEdit = (view: SettingCategoryView) => {
    const init: Record<string, unknown> = {};
    for (const item of view.items) init[item.key] = item.value;
    setDraft(init);
    setSnapshot(init);
    setEditing(view.category);
  };

  /** 点另一张卡的「编辑」：当前卡有未保存改动时先确认再切换。 */
  const requestEdit = (view: SettingCategoryView) => {
    if (dirty) {
      modal.confirm({
        title: '放弃未保存的更改？',
        content: '当前分组的修改尚未保存，切换编辑其它分组后将丢失。',
        okText: '放弃修改',
        okButtonProps: { danger: true },
        cancelText: '继续编辑',
        onOk: () => startEdit(view),
      });
      return;
    }
    startEdit(view);
  };

  const save = async () => {
    if (editing == null) return;
    try {
      await update.mutateAsync({ values: draft });
    } catch {
      // 错误 toast 由 mutation onError 统一提示，保持编辑态供修正。
      return;
    }
    setEditing(null);
    setDraft({});
    setSnapshot({});
  };

  return (
    <div style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 16 }}>
      {ordered.length === 0 && (
        <Empty description="暂无可配置参数" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}
      {ordered.map((cat) => {
        const view = byCategory.get(cat)!;
        const meta = SETTING_CATEGORY_META[cat];
        const isEditing = editing === cat;
        return (
          <Card
            key={cat}
            title={
              <Space size={8}>
                <span>{meta.title}</span>
                {cat === 'security_policy' && (
                  <StatusPill badge={toneBadge('warning', '改后即时全局生效')} />
                )}
              </Space>
            }
            extra={
              isEditing ? (
                <Space>
                  <Button
                    size="small"
                    onClick={() => {
                      setEditing(null);
                      setDraft({});
                      setSnapshot({});
                    }}
                  >
                    取消
                  </Button>
                  <Button
                    size="small"
                    type="primary"
                    loading={update.isPending}
                    onClick={() => void save()}
                  >
                    保存
                  </Button>
                </Space>
              ) : (
                <Button size="small" onClick={() => requestEdit(view)}>
                  编辑
                </Button>
              )
            }
          >
            <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 12 }}>
              {meta.hint}
            </div>
            {view.items.length === 0 ? (
              <Empty description="该分组暂无参数" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                {view.items.map((item) => (
                  <ParamRow
                    key={item.key}
                    item={item}
                    editing={isEditing}
                    value={isEditing ? draft[item.key] : item.value}
                    onChange={(v) => {
                      setDraft((d) => ({ ...d, [item.key]: v }));
                    }}
                    masked={/secret|access_key|password/i.test(item.key)}
                  />
                ))}
              </div>
            )}
          </Card>
        );
      })}
      <Alert
        type="info"
        showIcon
        message="参数运行时即时生效（经广播失效缓存，无需重启）。系统保留参数（sys.* 前缀）不可在此修改。"
      />
    </div>
  );
}

function ParamRow({
  item,
  editing,
  value,
  onChange,
  masked,
}: {
  item: SettingItem;
  editing: boolean;
  value: unknown;
  onChange: (v: unknown) => void;
  masked?: boolean;
}) {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '220px 1fr',
        alignItems: 'center',
        padding: '10px 4px',
        borderBottom: '1px dashed var(--divider)',
      }}
    >
      <div>
        <div style={{ fontSize: 13, color: 'var(--text-secondary)', fontWeight: 500 }}>
          {item.description || item.key}
        </div>
        <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 2 }}>{item.key}</div>
      </div>
      <div>{renderControl(value, editing, onChange, masked)}</div>
    </div>
  );
}

function renderControl(
  value: unknown,
  editing: boolean,
  onChange: (v: unknown) => void,
  masked?: boolean,
) {
  if (typeof value === 'boolean') {
    return editing ? (
      <Switch checked={value} onChange={onChange} />
    ) : (
      <StatusPill badge={toneBadge(value ? 'success' : 'neutral', value ? '已启用' : '未启用')} />
    );
  }
  if (typeof value === 'number') {
    return editing ? (
      <InputNumber value={value} onChange={(v) => onChange(v)} style={{ width: 160 }} />
    ) : (
      <span style={{ fontWeight: 500 }}>{value}</span>
    );
  }
  // string / null / 其它
  const str = toStr(value);
  if (!editing) {
    if (masked && str) return <span style={{ color: 'var(--text-tertiary)' }}>••••••••</span>;
    return str ? (
      <span style={{ fontWeight: 500 }}>{str}</span>
    ) : (
      <span style={{ color: 'var(--text-tertiary)' }}>—</span>
    );
  }
  // 密钥类字段：后端回显掩码 ********；留空或保持掩码提交 = 不变更
  return masked ? (
    <div>
      <Input.Password
        value={str}
        onChange={(e) => onChange(e.target.value)}
        placeholder="留空则不变"
        style={{ maxWidth: 360 }}
      />
      <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 2 }}>
        回显为掩码；留空或保持 ******** 则不变，重填才更新
      </div>
    </div>
  ) : (
    <Input value={str} onChange={(e) => onChange(e.target.value)} style={{ maxWidth: 360 }} />
  );
}
