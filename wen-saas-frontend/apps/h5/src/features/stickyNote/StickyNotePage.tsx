import { useState } from 'react';
import {
  Button,
  CenterPopup,
  Dialog,
  DotLoading,
  Empty,
  NavBar,
  PullToRefresh,
  SwipeAction,
  TextArea,
  Toast,
} from 'antd-mobile';
import { useNavigate } from 'react-router';
import type { StickyNote } from '@wen-saas/api-client';
import { formatRelative } from '../../lib/format';
import {
  useCreateStickyNote,
  useDeleteStickyNote,
  useStickyNotes,
  useUpdateStickyNote,
} from './stickyNoteQueries';

/** 便签色板：key → { 背景, 边框 }。用 hex（移动 webview 对 oklch 兼容性差）。 */
type Tone = { bg: string; border: string };
const DEFAULT_COLOR = 'yellow';
const DEFAULT_TONE: Tone = { bg: '#fef9c3', border: '#fde047' };
const COLORS: Record<string, Tone> = {
  yellow: DEFAULT_TONE,
  green: { bg: '#dcfce7', border: '#86efac' },
  blue: { bg: '#dbeafe', border: '#93c5fd' },
  pink: { bg: '#fce7f3', border: '#f9a8d4' },
  purple: { bg: '#f3e8ff', border: '#d8b4fe' },
};

function tone(color: string): Tone {
  return COLORS[color] ?? DEFAULT_TONE;
}

type Editing = { id?: string; content: string; color: string };

export function StickyNotePage() {
  const navigate = useNavigate();

  const notesQ = useStickyNotes();
  const notes = notesQ.data ?? [];
  const create = useCreateStickyNote();
  const update = useUpdateStickyNote();
  const del = useDeleteStickyNote();

  const [editing, setEditing] = useState<Editing | null>(null);

  const confirmDelete = (id: string) => {
    void Dialog.confirm({
      content: '删除这条便签？',
      confirmText: '删除',
      cancelText: '取消',
      onConfirm: async () => {
        try {
          await del.mutateAsync(id);
        } catch {
          Toast.show({ content: '删除失败' });
        }
      },
    });
  };

  const save = () => {
    if (!editing) return;
    const content = editing.content.trim();
    if (!content) {
      Toast.show({ content: '便签内容不能为空' });
      return;
    }
    const onDone = { onSuccess: () => setEditing(null), onError: () => Toast.show({ content: '保存失败' }) };
    if (editing.id) {
      update.mutate({ id: editing.id, content, color: editing.color }, onDone);
    } else {
      create.mutate({ content, color: editing.color }, onDone);
    }
  };

  return (
    <div
      style={{ minHeight: '100dvh', background: 'var(--bg)', display: 'flex', flexDirection: 'column' }}
    >
      <NavBar
        onBack={() => void navigate(`/workspace`)}
        style={{ background: 'var(--bg-elev)' }}
        right={
          <span
            style={{ fontSize: 14, color: 'var(--brand)' }}
            onClick={() => setEditing({ content: '', color: DEFAULT_COLOR })}
          >
            ＋ 新增
          </span>
        }
      >
        备忘便利贴
      </NavBar>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        <PullToRefresh onRefresh={async () => void (await notesQ.refetch())}>
          {notesQ.isLoading ? (
            <div style={{ padding: '48px 0', textAlign: 'center', color: 'var(--brand)' }}>
              <DotLoading color="primary" />
            </div>
          ) : notes.length === 0 ? (
            <Empty description="还没有便签，点右上角新增" style={{ padding: '56px 0' }} />
          ) : (
            <div style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 10 }}>
              {notes.map((n) => (
                <NoteRow
                  key={n.id}
                  note={n}
                  onEdit={() => setEditing({ id: n.id, content: n.content, color: n.color })}
                  onDelete={() => confirmDelete(n.id)}
                />
              ))}
            </div>
          )}
        </PullToRefresh>
      </div>

      <CenterPopup
        visible={!!editing}
        onClose={() => setEditing(null)}
        onMaskClick={() => setEditing(null)}
        style={{ '--max-width': '340px' }}
      >
        {editing && (
          <div style={{ padding: 20 }}>
            <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 12 }}>
              {editing.id ? '编辑便签' : '新增便签'}
            </div>
            <TextArea
              placeholder="记点什么…"
              value={editing.content}
              onChange={(v) => setEditing((e) => (e ? { ...e, content: v } : e))}
              maxLength={2000}
              rows={4}
              autoFocus
              style={{
                background: tone(editing.color).bg,
                borderRadius: 8,
                padding: 10,
                '--font-size': '14px',
              }}
            />
            <div style={{ display: 'flex', gap: 10, margin: '14px 0 18px' }}>
              {Object.entries(COLORS).map(([c, t]) => (
                <button
                  key={c}
                  type="button"
                  aria-label={c}
                  onClick={() => setEditing((e) => (e ? { ...e, color: c } : e))}
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: '50%',
                    background: t.bg,
                    border:
                      editing.color === c
                        ? '2px solid var(--text-secondary, #666)'
                        : `1px solid ${t.border}`,
                    padding: 0,
                  }}
                />
              ))}
            </div>
            <div style={{ display: 'flex', gap: 10 }}>
              <Button block onClick={() => setEditing(null)}>
                取消
              </Button>
              <Button
                block
                color="primary"
                loading={create.isPending || update.isPending}
                onClick={save}
              >
                保存
              </Button>
            </div>
          </div>
        )}
      </CenterPopup>
    </div>
  );
}

function NoteRow({
  note,
  onEdit,
  onDelete,
}: {
  note: StickyNote;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const t = tone(note.color);
  return (
    <SwipeAction
      rightActions={[{ key: 'delete', text: '删除', color: 'danger', onClick: onDelete }]}
    >
      <div
        onClick={onEdit}
        style={{
          background: t.bg,
          border: `1px solid ${t.border}`,
          borderRadius: 10,
          padding: '12px 14px',
        }}
      >
        <div style={{ fontSize: 11, color: 'var(--text-tertiary, #999)', marginBottom: 6 }}>
          更新于 {formatRelative(note.updatedAt)}
        </div>
        <div
          style={{
            fontSize: 14,
            lineHeight: 1.5,
            color: 'var(--text, #333)',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            display: '-webkit-box',
            WebkitLineClamp: 5,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
          }}
        >
          {note.content}
        </div>
      </div>
    </SwipeAction>
  );
}
