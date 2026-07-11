import { useState } from 'react';
import { App, Input, Popconfirm, Popover } from 'antd';
import { BgColorsOutlined, DeleteOutlined } from '@ant-design/icons';
import type { StickyNote } from '@wen-saas/api-client';
import { formatDateTimeCompact } from '@wen-saas/shared-ui';
import { STICKY_COLORS, stickyTone } from './stickyColors';

/** 5 个颜色圆点选择器。 */
export function ColorDots({
  value,
  onChange,
}: {
  value: string;
  onChange: (color: string) => void;
}) {
  return (
    <div style={{ display: 'flex', gap: 6 }}>
      {Object.entries(STICKY_COLORS).map(([c, t]) => (
        <button
          key={c}
          type="button"
          aria-label={c}
          onClick={() => onChange(c)}
          style={{
            width: 18,
            height: 18,
            borderRadius: '50%',
            background: t.bg,
            border: value === c ? `2px solid var(--text-secondary)` : `1px solid ${t.border}`,
            cursor: 'pointer',
            padding: 0,
          }}
        />
      ))}
    </div>
  );
}

/**
 * 单条彩色便签：头部「更新于」时间 + 内联编辑内容（失焦保存）+ 改色 + 删除。
 * 内容非受控（key=note.content，服务端内容变化时重挂重置）；失焦为空则回退并提示。
 * 编辑态（聚焦）头部提示「失焦自动保存」；列表按 updatedAt 倒序，编辑当下不冒顶（会话内冻结顺序），重新打开抽屉/刷新才重排。
 */
export function StickyNoteCard({
  note,
  onSave,
  onDelete,
}: {
  note: StickyNote;
  onSave: (content: string, color: string) => void;
  onDelete: () => void;
}) {
  const { message } = App.useApp();
  const [editing, setEditing] = useState(false);

  const t = stickyTone(note.color);

  const commit = (e: React.FocusEvent<HTMLTextAreaElement>) => {
    setEditing(false);
    const next = e.target.value.trim();
    if (!next) {
      e.target.value = note.content;
      message.warning('便签内容不能为空');
      return;
    }
    if (next !== note.content) onSave(next, note.color);
  };

  const style: React.CSSProperties = {
    background: t.bg,
    border: `1px solid ${t.border}`,
    borderRadius: 'var(--r-md, 8px)',
    padding: 10,
    display: 'flex',
    flexDirection: 'column',
    gap: 6,
  };

  return (
    <div style={style}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        {editing ? (
          <span style={{ fontSize: 11, color: 'var(--brand)' }}>失焦自动保存</span>
        ) : (
          <span style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
            更新于 {formatDateTimeCompact(note.updatedAt)}
          </span>
        )}
        <span style={{ flex: 1 }} />
        <Popover
          trigger="click"
          content={<ColorDots value={note.color} onChange={(c) => onSave(note.content, c)} />}
        >
          <BgColorsOutlined style={{ cursor: 'pointer', color: 'var(--text-tertiary)' }} />
        </Popover>
        <Popconfirm title="删除这条便签？" okText="删除" cancelText="取消" onConfirm={onDelete}>
          <DeleteOutlined style={{ cursor: 'pointer', color: 'var(--text-tertiary)' }} />
        </Popconfirm>
      </div>
      <Input.TextArea
        key={note.content}
        defaultValue={note.content}
        onFocus={() => setEditing(true)}
        onBlur={commit}
        autoSize={{ minRows: 2, maxRows: 8 }}
        variant="borderless"
        style={{ background: 'transparent', padding: 0, color: 'var(--text)', resize: 'none' }}
        maxLength={2000}
      />
    </div>
  );
}
