import type { CSSProperties } from 'react';
import { useDroppable } from '@dnd-kit/core';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
  CloseCircleOutlined,
  DownOutlined,
  FolderOutlined,
  RightOutlined,
} from '@ant-design/icons';
import type { FlatDeptNode } from './deptTreeDnd';

/** 每级缩进像素，与原 antd Tree indentSize 一致。 */
const INDENT = 16;
/** switcher 占位宽度，保证叶子节点与有子节点的行图标对齐。 */
const SWITCHER_W = 18;

/** 安全取消放置区的 droppable id：落点是它时不做任何变更。 */
export const DRAG_CANCEL_ID = '__dnd_cancel__';

/** 拖拽中出现的「安全取消区」：拖到此处松手放弃本次调整（排序/调整层级通用）。 */
export function DragCancelZone() {
  const { setNodeRef, isOver } = useDroppable({ id: DRAG_CANCEL_ID });
  return (
    <div ref={setNodeRef} className={`dept-drag-cancel${isOver ? ' dept-drag-cancel--over' : ''}`}>
      <CloseCircleOutlined />
      <span>{isOver ? '松手取消本次调整' : '拖到这里取消'}</span>
    </div>
  );
}

type RowProps = {
  node: FlatDeptNode;
  selected: boolean;
  /** 浮起卡片态（DragOverlay 复用）：加阴影/边框，不渲染 switcher。 */
  overlay?: boolean;
  /** move 模式下当前 over 行：高亮"将放入此部门"。 */
  moveOver?: boolean;
  onToggleExpand?: (id: string) => void;
};

/** 纯展示行：复刻原部门树视觉（folder 图标 + 名称 + 停用标 + 人数）。 */
export function DeptRow({ node, selected, overlay, moveOver, onToggleExpand }: RowProps) {
  const { dept, depth, hasChildren, collapsed } = node;
  const expanded = hasChildren && !collapsed;

  const style: CSSProperties = {
    paddingInlineStart: overlay ? 8 : depth * INDENT + 8,
    ...(overlay
      ? {
          background: 'var(--bg-elev)',
          borderRadius: 8,
          border: '1px solid var(--border)',
          boxShadow: '0 6px 20px rgba(0,0,0,0.14)',
          transform: 'scale(1.02)',
        }
      : {}),
    ...(moveOver
      ? { outline: '2px solid var(--brand)', outlineOffset: -2, background: 'var(--brand-bg)' }
      : {}),
  };

  return (
    <div
      className={`dept-row${selected ? ' dept-row--selected' : ''}`}
      data-overlay={overlay ? '1' : undefined}
      style={style}
    >
      {!overlay &&
        (hasChildren ? (
          <span
            className="dept-row__switcher"
            // 切展开不触发选中、不启动拖拽
            onClick={(e) => {
              e.stopPropagation();
              onToggleExpand?.(node.id);
            }}
            onPointerDown={(e) => e.stopPropagation()}
          >
            {expanded ? <DownOutlined /> : <RightOutlined />}
          </span>
        ) : (
          <span style={{ width: SWITCHER_W, flexShrink: 0 }} />
        ))}
      <FolderOutlined className="dept-row__icon" />
      <span className="dept-row__name">{dept.name}</span>
      {dept.status === 0 && <span className="dept-row__badge">停用</span>}
      {(dept.memberCount ?? 0) > 0 && <span className="dept-row__count">{dept.memberCount}</span>}
    </div>
  );
}

type ItemProps = {
  node: FlatDeptNode;
  selected: boolean;
  /** 当前拖拽模式：仅 sort 做相邻项让位位移；move 只高亮目标、不挤动；off 禁用拖拽。 */
  mode: 'off' | 'sort' | 'move';
  /**
   * 排序让位作用域：undefined=当前无排序拖拽（不让位）；null=整棵树（拖顶级节点时各子树整体移动）；
   * Set=被拖节点父级 P 子树内的全部 id（同级节点连同其后代作为整块一起让位，P 之外不动）。
   */
  shiftScope: Set<string> | null | undefined;
  /** move 模式下当前 over 行高亮。 */
  moveOver: boolean;
  onSelect: (id: string) => void;
  onToggleExpand: (id: string) => void;
};

/** 接入 dnd-kit 的可排序行：sort 模式下 transform/transition 驱动相邻项让位动画。 */
export function SortableTreeItem({
  node,
  selected,
  mode,
  shiftScope,
  moveOver,
  onSelect,
  onToggleExpand,
}: ItemProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: node.id,
    disabled: mode === 'off',
  });

  // dnd-kit 默认会给 active↔over 之间的所有行加 transform，会把非同级节点也挤动。
  // 这里只放行「被拖节点父级子树范围内」的行：同级及其后代整块移动，跨父级节点不动。
  const applyShift = shiftScope !== undefined && (shiftScope === null || shiftScope.has(node.id));

  const style: CSSProperties = {
    transform: applyShift ? CSS.Translate.toString(transform) : undefined,
    transition: applyShift ? transition : undefined,
    // 被拖原行 ghost 化，留出空槽让相邻项往其方向让位
    opacity: isDragging ? 0.4 : undefined,
  };

  // off 模式：纯导航行，不下发 dnd-kit 的 role=button / aria-disabled / tabIndex / 拖拽监听，
  // 否则行会被标成"禁用按钮"，语义错误且展开箭头不可点。
  const dragProps = mode === 'off' ? {} : { ...attributes, ...listeners };

  return (
    <div ref={setNodeRef} style={style} {...dragProps} onClick={() => onSelect(node.id)}>
      <DeptRow
        node={node}
        selected={selected}
        moveOver={moveOver}
        onToggleExpand={onToggleExpand}
      />
    </div>
  );
}
