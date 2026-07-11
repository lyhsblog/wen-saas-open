import { Empty } from 'antd';
import { PlusOutlined, RightOutlined } from '@ant-design/icons';
import { WaCard } from '../../components/wa';
import { stickyTone } from './stickyColors';
import { useStickyNotes } from './stickyNoteQueries';
import { openStickyDrawer } from './stickyDrawerStore';

/** 工作台「备忘便利贴」概览卡片：最近几条摘要 + 新增/查看全部，点击唤起同一个抽屉。 */
export function WorkbenchStickyCard() {
  const notes = useStickyNotes().data ?? [];
  const preview = notes.slice(0, 4);

  return (
    <WaCard
      title="备忘便利贴"
      extra={
        <span
          style={{ fontSize: 12, color: 'var(--brand)', cursor: 'pointer' }}
          onClick={openStickyDrawer}
        >
          <PlusOutlined style={{ marginRight: 4 }} />
          新增
        </span>
      }
    >
      {preview.length === 0 ? (
        <div onClick={openStickyDrawer} style={{ cursor: 'pointer' }}>
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有便签，点此记一笔" />
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10 }}>
          {preview.map((n) => {
            const t = stickyTone(n.color);
            return (
              <div
                key={n.id}
                onClick={openStickyDrawer}
                title={n.content}
                style={{
                  background: t.bg,
                  border: `1px solid ${t.border}`,
                  borderRadius: 'var(--r-md, 8px)',
                  padding: 10,
                  minHeight: 72,
                  fontSize: 13,
                  color: 'var(--text)',
                  cursor: 'pointer',
                  overflow: 'hidden',
                  display: '-webkit-box',
                  WebkitLineClamp: 3,
                  WebkitBoxOrient: 'vertical',
                }}
              >
                {n.content}
              </div>
            );
          })}
        </div>
      )}
      {notes.length > preview.length && (
        <div
          style={{ fontSize: 12, color: 'var(--brand)', marginTop: 10, cursor: 'pointer' }}
          onClick={openStickyDrawer}
        >
          查看全部（{notes.length}） <RightOutlined style={{ fontSize: 10 }} />
        </div>
      )}
    </WaCard>
  );
}
