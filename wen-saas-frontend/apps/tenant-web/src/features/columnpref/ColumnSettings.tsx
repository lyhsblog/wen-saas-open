import { useState } from 'react';
import { App, Button, Modal, Space, Typography } from 'antd';
import { CountChip } from '@wen-saas/shared-ui';
import {
  EyeInvisibleOutlined,
  EyeOutlined,
  HolderOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import {
  closestCenter,
  DndContext,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import type { ColumnPanel, ColumnPanelItem, ColumnPref } from '@wen-saas/shared-ui';

interface Props {
  panel: ColumnPanel;
  isCustomized: boolean;
  saving?: boolean;
  onApply: (next: ColumnPref) => Promise<void> | void;
  onReset: () => Promise<void> | void;
}

/**
 * 列设置入口 + 弹层。两栏：左=固定列(只读)+隐藏列，右=显示列(拖拽排序)。
 * 显示↔隐藏用按钮移动；显示列内 dnd-kit 拖拽排序。字段多时左右分栏免来回滚动。
 */
export function ColumnSettingsButton({ panel, isCustomized, saving, onApply, onReset }: Props) {
  const { message } = App.useApp();
  const [open, setOpen] = useState(false);
  const [visible, setVisible] = useState<ColumnPanelItem[]>([]);
  const [hidden, setHidden] = useState<ColumnPanelItem[]>([]);
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }));

  const start = () => {
    setVisible(panel.visible);
    setHidden(panel.hidden);
    setOpen(true);
  };

  const onDragEnd = (e: DragEndEvent) => {
    const { active, over } = e;
    if (!over || active.id === over.id) return;
    setVisible((list) => {
      const from = list.findIndex((i) => i.key === active.id);
      const to = list.findIndex((i) => i.key === over.id);
      if (from < 0 || to < 0) return list;
      return arrayMove(list, from, to);
    });
  };
  const toHidden = (it: ColumnPanelItem) => {
    setVisible((v) => v.filter((x) => x.key !== it.key));
    setHidden((h) => [...h, it]);
  };
  const toVisible = (it: ColumnPanelItem) => {
    setHidden((h) => h.filter((x) => x.key !== it.key));
    setVisible((v) => [...v, it]);
  };

  const save = async () => {
    await onApply({ customKeys: visible.map((i) => i.key), hiddenKeys: hidden.map((i) => i.key) });
    message.success('已保存列设置');
    setOpen(false);
  };
  const reset = async () => {
    await onReset();
    message.success('已重置为默认');
    setOpen(false);
  };

  return (
    <>
      <Button icon={<SettingOutlined />} onClick={start}>
        列设置
      </Button>
      <Modal
        title="列设置"
        open={open}
        onCancel={() => setOpen(false)}
        width={760}
        footer={
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <Button danger disabled={!isCustomized || !!saving} onClick={() => void reset()}>
              重置为默认
            </Button>
            <div style={{ flex: 1 }} />
            <Space>
              <Button onClick={() => setOpen(false)}>取消</Button>
              <Button type="primary" loading={!!saving} onClick={() => void save()}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
          {/* 左半区：固定列 + 隐藏列 */}
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 12, minWidth: 0 }}>
            <Pane title="固定列（不可调整）">
              {panel.fixed.length === 0 ? (
                <EmptyHint />
              ) : (
                panel.fixed.map((it) => (
                  <Row key={it.key} muted>
                    <span style={{ flex: 1 }}>{it.label}</span>
                    <CountChip>{it.pin === 'right' ? '尾部固定' : '头部固定'}</CountChip>
                  </Row>
                ))
              )}
            </Pane>
            <Pane title={`隐藏列（${hidden.length}）`}>
              {hidden.length === 0 ? (
                <EmptyHint />
              ) : (
                hidden.map((it) => (
                  <Row key={it.key} muted>
                    <span style={{ flex: 1 }}>{it.label}</span>
                    <Button
                      size="small"
                      type="primary"
                      ghost
                      icon={<EyeOutlined />}
                      onClick={() => toVisible(it)}
                    >
                      显示
                    </Button>
                  </Row>
                ))
              )}
            </Pane>
          </div>

          {/* 右半区：显示列（拖拽排序） */}
          <div style={{ flex: 1, minWidth: 0 }}>
            <Pane title={`显示列（${visible.length}）· 拖拽排序`}>
              {visible.length === 0 ? (
                <EmptyHint />
              ) : (
                <DndContext
                  sensors={sensors}
                  collisionDetection={closestCenter}
                  onDragEnd={onDragEnd}
                >
                  <SortableContext
                    items={visible.map((i) => i.key)}
                    strategy={verticalListSortingStrategy}
                  >
                    {visible.map((it) => (
                      <SortableRow key={it.key} item={it} onHide={() => toHidden(it)} />
                    ))}
                  </SortableContext>
                </DndContext>
              )}
            </Pane>
          </div>
        </div>
      </Modal>
    </>
  );
}

function Pane({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div
      style={{
        border: '1px solid var(--divider)',
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          fontSize: 12,
          color: 'var(--text-secondary)',
          padding: '8px 12px',
          background: 'var(--bg-elev)',
          borderBottom: '1px solid var(--divider)',
        }}
      >
        {title}
      </div>
      <div style={{ padding: 8, minHeight: 48 }}>{children}</div>
    </div>
  );
}

function Row({ children, muted }: { children: React.ReactNode; muted?: boolean }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '6px 8px',
        borderRadius: 6,
        color: muted ? 'var(--text-secondary)' : 'var(--text)',
      }}
    >
      {children}
    </div>
  );
}

function SortableRow({ item, onHide }: { item: ColumnPanelItem; onHide: () => void }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.key,
  });
  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '6px 8px',
    borderRadius: 6,
    background: isDragging ? 'var(--bg-elev)' : 'transparent',
  };
  return (
    <div ref={setNodeRef} style={style}>
      <span
        {...attributes}
        {...listeners}
        style={{ cursor: 'grab', color: 'var(--text-secondary)' }}
      >
        <HolderOutlined />
      </span>
      <span style={{ flex: 1 }}>{item.label}</span>
      <Button size="small" icon={<EyeInvisibleOutlined />} onClick={onHide}>
        隐藏
      </Button>
    </div>
  );
}

function EmptyHint() {
  return (
    <Typography.Text type="secondary" style={{ fontSize: 12, paddingLeft: 4 }}>
      无
    </Typography.Text>
  );
}
