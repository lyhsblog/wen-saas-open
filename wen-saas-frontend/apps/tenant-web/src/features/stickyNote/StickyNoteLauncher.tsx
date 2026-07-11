import { useEffect, useState } from 'react';
import { App, Button, Drawer, Empty, FloatButton, Input, Skeleton } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { ColorDots, StickyNoteCard } from './StickyNoteCard';
import { DEFAULT_STICKY_COLOR } from './stickyColors';
import {
  useCreateStickyNote,
  useDeleteStickyNote,
  useStickyNotes,
  useUpdateStickyNote,
} from './stickyNoteQueries';
import { closeStickyDrawer, toggleStickyDrawer, useStickyDrawerOpen } from './stickyDrawerStore';

/** 便利贴图标：卷角便签 + 两条文字线（比 FormOutlined 更像便利贴）。 */
function StickyNoteIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="1em"
      height="1em"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M4 5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v8l-7 7H6a2 2 0 0 1-2-2V5z" />
      <path d="M20 13h-5a2 2 0 0 0-2 2v5" />
      <path d="M8 8.5h8M8 12h5" />
    </svg>
  );
}

/**
 * 备忘便利贴全局入口：右下角悬浮按钮 + 右侧抽屉。
 *
 * 挂在租户外壳（AppShell）内，登录后每个页面可用。抽屉开关走全局 store，
 * 与工作台卡片共享同一个抽屉。
 */
export function StickyNoteLauncher() {
  const open = useStickyDrawerOpen();
  const { message } = App.useApp();

  const notesQuery = useStickyNotes();
  const notes = notesQuery.data ?? [];
  const { refetch } = notesQuery;
  // 打开抽屉即视作「重新进入」：refetch 按 updatedAt 重排（会话内的原位编辑到此归位）
  useEffect(() => {
    if (open) void refetch();
  }, [open, refetch]);
  const create = useCreateStickyNote();
  const update = useUpdateStickyNote();
  const remove = useDeleteStickyNote();

  const [draft, setDraft] = useState('');
  const [draftColor, setDraftColor] = useState(DEFAULT_STICKY_COLOR);

  const add = () => {
    const content = draft.trim();
    if (!content) return;
    create.mutate(
      { content, color: draftColor },
      {
        onSuccess: () => setDraft(''),
        onError: () => {
          message.error('新增失败');
        },
      },
    );
  };

  return (
    <>
      <FloatButton
        icon={<StickyNoteIcon />}
        tooltip={{ title: '备忘便利贴', placement: 'bottom' }}
        onClick={toggleStickyDrawer}
        {...(notes.length > 0
          ? { badge: { count: notes.length, color: 'var(--brand)' } }
          : {})}
      />
      <Drawer
        title="备忘便利贴"
        placement="right"
        width={360}
        open={open}
        onClose={closeStickyDrawer}
        styles={{ body: { display: 'flex', flexDirection: 'column', gap: 12 } }}
      >
        {/* 新增区 */}
        <div
          style={{
            border: '1px solid var(--divider)',
            borderRadius: 'var(--r-md, 8px)',
            padding: 10,
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
          }}
        >
          <Input.TextArea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="记点什么…"
            autoSize={{ minRows: 2, maxRows: 6 }}
            maxLength={2000}
          />
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <ColorDots value={draftColor} onChange={setDraftColor} />
            <span style={{ flex: 1 }} />
            <Button
              type="primary"
              size="small"
              icon={<PlusOutlined />}
              loading={create.isPending}
              disabled={!draft.trim()}
              onClick={add}
            >
              添加
            </Button>
          </div>
        </div>

        {/* 列表 */}
        {notesQuery.isLoading ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : notes.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有便签" />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {notes.map((n) => (
              <StickyNoteCard
                key={n.id}
                note={n}
                onSave={(content, color) => update.mutate({ id: n.id, content, color })}
                onDelete={() => remove.mutate(n.id)}
              />
            ))}
          </div>
        )}
      </Drawer>
    </>
  );
}
