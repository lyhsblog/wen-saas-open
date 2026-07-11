import { useCallback, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { App, Button, Dropdown, Input, Space, Switch, Tooltip } from 'antd';
import {
  ColumnHeightOutlined,
  DownOutlined,
  PlusOutlined,
  TeamOutlined,
  VerticalAlignMiddleOutlined,
} from '@ant-design/icons';
import {
  closestCenter,
  DndContext,
  DragOverlay,
  KeyboardSensor,
  MeasuringStrategy,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import type { DragEndEvent, DragOverEvent, DragStartEvent } from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import type { DeptView } from '@wen-saas/domain';
import { PermissionGate, ReadonlyButton, useHasPermission } from '@wen-saas/permissions';
import { ImportEntryButton } from '../taskCenter/ImportEntryButton';
import { DeptRow, DragCancelZone, DRAG_CANCEL_ID, SortableTreeItem } from './SortableTreeItem';
import { ALL_MEMBERS_KEY } from './deptQueries';
import {
  applyLocalOrder,
  collectDescendantIds,
  flattenTree,
  getSiblings,
  getSortProjection,
  isDescendant,
  parentIdOf,
} from './deptTreeDnd';

type Props = {
  treeData: DeptView[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  onCreateDept: (parentId: string | null) => void;
  onDrop: (draggedId: string, targetParentId: string) => void;
  /** 同级拖拽排序：传入父级 id 与该父级下重排后的子部门 id 顺序。 */
  onReorder: (parentId: string, orderedSiblingIds: string[]) => void;
  /** 数据导入完成后刷新部门树。 */
  onImported: () => void;
  /** 窄屏放进 NavRailDrawer 时铺满容器：宽高 100%、去掉 inline 形态的 260 定宽与右分隔线。 */
  fluid?: boolean;
};

/** 递归收集所有节点 key */
function collectAllKeys(nodes: DeptView[]): string[] {
  const keys: string[] = [];
  function walk(list: DeptView[]) {
    for (const n of list) {
      if (n.id != null) keys.push(String(n.id));
      if (n.children?.length) walk(n.children);
    }
  }
  walk(nodes);
  return keys;
}

/** 收集深度 ≤ maxDepth 的节点 key（depth 从 1 计）。"展开到 N 级"=展开深度 ≤ N-1 的节点 */
function keysWithinDepth(nodes: DeptView[], maxDepth: number, depth = 1): string[] {
  if (depth > maxDepth) return [];
  const out: string[] = [];
  for (const n of nodes) {
    if (n.id != null) out.push(String(n.id));
    if (n.children?.length) out.push(...keysWithinDepth(n.children, maxDepth, depth + 1));
  }
  return out;
}

/** 在树中查名称 */
function findName(nodes: DeptView[], id: string): string {
  for (const n of nodes) {
    if (String(n.id) === id) return n.name ?? '';
    if (n.children?.length) {
      const found = findName(n.children, id);
      if (found) return found;
    }
  }
  return '';
}

/** 搜索匹配：返回需要展开的 key 集合 */
function matchKeys(nodes: DeptView[], query: string): Set<string> {
  const result = new Set<string>();
  const q = query.toLowerCase();
  function walk(list: DeptView[], ancestors: string[]): boolean {
    let any = false;
    for (const n of list) {
      const key = String(n.id ?? '');
      const nameMatch = (n.name ?? '').toLowerCase().includes(q);
      let childMatch = false;
      if (n.children?.length) {
        childMatch = walk(n.children, [...ancestors, key]);
      }
      if (nameMatch || childMatch) {
        result.add(key);
        for (const a of ancestors) result.add(a);
        any = true;
      }
    }
    return any;
  }
  walk(nodes, []);
  return result;
}

export function DeptTreePane({
  treeData,
  selectedId,
  onSelect,
  onCreateDept,
  onDrop,
  onReorder,
  onImported,
  fluid = false,
}: Props) {
  const { modal, message } = App.useApp();
  const [search, setSearch] = useState('');
  // 拖拽默认关闭；拆成「排序 / 调整层级」两个互斥模式，避免一种拖拽里混两种语义（用户反馈）
  const [dragMode, setDragMode] = useState<'off' | 'sort' | 'move'>('off');
  const canEdit = useHasPermission('org.dept.update');
  const [expandedKeys, setExpandedKeys] = useState<string[]>(() =>
    treeData.length > 0 ? [String(treeData[0]?.id ?? '')] : [],
  );

  // 拖放成功到 refetch 回来之间，用本地受控顺序覆盖兄弟序，避免 UI 回弹闪烁。
  // 连同其依据的 treeData 引用一起存：refetch 回来引用变化即视为已落库，覆盖自动失效（不用 effect）。
  const [localOrder, setLocalOrder] = useState<{
    base: DeptView[];
    order: Record<string, string[]>;
  } | null>(null);
  // 拖拽中的节点（驱动 DragOverlay）与当前 over（move 模式高亮目标父级）
  const [activeId, setActiveId] = useState<string | null>(null);
  const [overId, setOverId] = useState<string | null>(null);

  // 仅当本地覆盖仍基于当前 treeData 引用时生效；引用变化（服务端数据回来）则丢弃
  const effectiveOrder = localOrder && localOrder.base === treeData ? localOrder.order : null;

  const filteredKeys = useMemo(() => {
    if (!search.trim()) return null;
    return matchKeys(treeData, search.trim());
  }, [treeData, search]);

  // 搜索筛选时部分兄弟节点被隐藏，重排会算错顺序——筛选态下排序模式失效（开关也禁用）
  const isFiltering = filteredKeys != null;
  const effectiveDragMode = isFiltering && dragMode === 'sort' ? 'off' : dragMode;

  // 展开集合：搜索时强制展开匹配节点；否则用用户展开态
  const expandedSet = useMemo<Set<string>>(
    () => (filteredKeys ? filteredKeys : new Set(expandedKeys)),
    [filteredKeys, expandedKeys],
  );

  // 应用本地排序覆盖 → 按搜索筛选 → 扁平化为可见行
  const flatNodes = useMemo(() => {
    const ordered = applyLocalOrder(treeData, effectiveOrder);
    const flat = flattenTree(ordered, expandedSet);
    if (!filteredKeys) return flat;
    return flat.filter((n) => filteredKeys.has(n.id));
  }, [treeData, effectiveOrder, expandedSet, filteredKeys]);

  const flatIds = useMemo(() => flatNodes.map((n) => n.id), [flatNodes]);
  const activeNode = useMemo(
    () => (activeId ? flatNodes.find((n) => n.id === activeId) : undefined),
    [activeId, flatNodes],
  );
  // 排序让位作用域：限定在被拖节点父级 P 的子树内（同级及其后代整块移动），P 之外不挤动。
  // P=根（顶级节点）时返回 null=整棵树都可整块移动。非排序拖拽返回 undefined=不让位。
  const shiftScope = useMemo<Set<string> | null | undefined>(() => {
    if (effectiveDragMode !== 'sort' || activeNode == null) return undefined;
    const parentId = activeNode.parentId;
    if (parentId === '0') return null;
    return collectDescendantIds(treeData, parentId);
  }, [effectiveDragMode, activeNode, treeData]);

  const sensors = useSensors(
    // 移动 5px 才激活拖拽，保护单击选中不被吞
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const handleExpandAll = useCallback(() => {
    setExpandedKeys(collectAllKeys(treeData));
  }, [treeData]);

  const handleCollapseAll = useCallback(() => {
    setExpandedKeys([]);
  }, []);

  // 展开到指定层级（N 级可见 = 展开深度 ≤ N-1 的节点）。一级即全部收起。
  const expandToLevel = useCallback(
    (level: number) => {
      setExpandedKeys(keysWithinDepth(treeData, level - 1));
    },
    [treeData],
  );

  const toggleExpand = useCallback((id: string) => {
    setExpandedKeys((prev) => (prev.includes(id) ? prev.filter((k) => k !== id) : [...prev, id]));
  }, []);

  const resetDrag = useCallback(() => {
    setActiveId(null);
    setOverId(null);
  }, []);

  const handleDragStart = useCallback((e: DragStartEvent) => {
    setActiveId(String(e.active.id));
  }, []);

  const handleDragOver = useCallback((e: DragOverEvent) => {
    setOverId(e.over ? String(e.over.id) : null);
  }, []);

  const handleDragEnd = useCallback(
    (e: DragEndEvent) => {
      const { active, over } = e;
      const activeKey = String(active.id);

      if (!over) {
        resetDrag();
        return;
      }
      const overKey = String(over.id);

      // ── 安全取消区：拖到此处松手，放弃本次调整（排序/调整层级通用）──────
      if (overKey === DRAG_CANCEL_ID) {
        resetDrag();
        return;
      }

      // ── 排序模式：仅同级之间重排，落本地序 + 调 onReorder ──────────
      if (effectiveDragMode === 'sort') {
        resetDrag();
        if (activeKey === overKey) return;
        const proj = getSortProjection(flatNodes, activeKey, overKey);
        if (!proj) return;
        const siblings = getSiblings(flatNodes, proj.parentId).map((n) => n.id);
        const fromIdx = siblings.indexOf(activeKey);
        if (fromIdx < 0 || fromIdx === proj.targetIndex) return;
        const orderedIds = arrayMove(siblings, fromIdx, proj.targetIndex);
        setLocalOrder((prev) => {
          const baseOrder = prev && prev.base === treeData ? prev.order : {};
          return { base: treeData, order: { ...baseOrder, [proj.parentId]: orderedIds } };
        });
        onReorder(proj.parentId, orderedIds);
        return;
      }

      // ── 调整层级模式：拖到某行上成为其子部门，二次确认后调 onDrop ──────
      if (effectiveDragMode === 'move') {
        resetDrag();
        const targetParentId = overKey;
        if (isDescendant(treeData, activeKey, targetParentId)) {
          message.info('不能移动到自身或其子部门下');
          return;
        }
        const currentParent = parentIdOf(treeData, activeKey) ?? '0';
        if (targetParentId === currentParent) {
          message.info('已在该层级，如需调整顺序请切换到「拖拽排序」');
          return;
        }
        const draggedName = findName(treeData, activeKey);
        const targetName = findName(treeData, targetParentId) || '目标部门';
        modal.confirm({
          title: '确认移动部门',
          content: `将「${draggedName}」移动到「${targetName}」下？`,
          okText: '确认移动',
          cancelText: '取消',
          onOk: () => onDrop(activeKey, targetParentId),
        });
        return;
      }

      resetDrag();
    },
    [effectiveDragMode, flatNodes, treeData, onReorder, onDrop, modal, message, resetDrag],
  );

  return (
    <div
      style={{
        flexShrink: 0,
        background: 'var(--bg-elev)',
        display: 'flex',
        flexDirection: 'column',
        ...(fluid
          ? { width: '100%', height: '100%' }
          : { width: 260, borderRight: '1px solid var(--border)' }),
      }}
    >
      <div
        style={{
          padding: '14px 16px 10px',
          borderBottom: '1px solid var(--divider)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
          <span style={{ fontSize: 14, fontWeight: 600, flexShrink: 0, whiteSpace: 'nowrap' }}>
            组织架构
          </span>
          <div style={{ flex: 1 }} />
          <Space.Compact size="small">
            <Dropdown
              trigger={['hover']}
              menu={{
                items: [
                  { key: '2', label: '展开到二级' },
                  { key: '3', label: '展开到三级' },
                  { key: '4', label: '展开到四级' },
                ],
                onClick: ({ key }) => expandToLevel(Number(key)),
              }}
            >
              <Button
                size="small"
                icon={<ColumnHeightOutlined />}
                onClick={handleExpandAll}
                style={{ fontSize: 12 }}
              >
                展开全部
                <DownOutlined style={{ fontSize: 9, marginInlineStart: 2 }} />
              </Button>
            </Dropdown>
            <Tooltip title="收起到一级">
              <Button
                size="small"
                icon={<VerticalAlignMiddleOutlined />}
                onClick={handleCollapseAll}
                style={{ fontSize: 12 }}
              >
                折叠
              </Button>
            </Tooltip>
          </Space.Compact>
        </div>
        <Input.Search
          size="small"
          placeholder="搜索部门名称"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          allowClear
        />
        {canEdit && (
          <div
            style={{
              marginTop: 12,
              padding: '7px 10px',
              border: '1px solid var(--divider)',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 8,
              fontSize: 12,
              color: 'var(--text-secondary)',
            }}
          >
            <Tooltip
              title={
                isFiltering
                  ? '搜索筛选时部分同级被隐藏，排序会算错，已禁用'
                  : '开启后拖动部门到同级之间调整顺序（不会改变上下级）'
              }
            >
              <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <Switch
                  size="small"
                  disabled={isFiltering}
                  checked={effectiveDragMode === 'sort'}
                  onChange={(on) => setDragMode(on ? 'sort' : 'off')}
                />
                <span>拖拽排序</span>
              </span>
            </Tooltip>
            <div style={{ width: 1, alignSelf: 'stretch', background: 'var(--divider)' }} />
            <Tooltip title="开启后把部门拖到另一个部门上，成为其子部门（改层级会二次确认）">
              <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <Switch
                  size="small"
                  checked={dragMode === 'move'}
                  onChange={(on) => setDragMode(on ? 'move' : 'off')}
                />
                <span>拖拽调整层级</span>
              </span>
            </Tooltip>
          </div>
        )}
      </div>
      <div className="dept-tree-scope" style={{ flex: 1, overflowY: 'auto', padding: '8px 0' }}>
        {/* 固定入口：全部成员（DndContext 之外，不参与拖拽/搜索；含未归属部门的成员） */}
        <div
          className={`dept-row${selectedId === ALL_MEMBERS_KEY ? ' dept-row--selected' : ''}`}
          style={{ paddingInlineStart: 8 }}
          onClick={() => onSelect(ALL_MEMBERS_KEY)}
        >
          <span style={{ width: 18, flexShrink: 0 }} />
          <TeamOutlined className="dept-row__icon" />
          <span className="dept-row__name">全部成员</span>
        </div>
        <div style={{ height: 1, background: 'var(--divider)', margin: '6px 12px' }} />
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          measuring={{ droppable: { strategy: MeasuringStrategy.Always } }}
          onDragStart={handleDragStart}
          onDragOver={handleDragOver}
          onDragEnd={handleDragEnd}
          onDragCancel={resetDrag}
        >
          <SortableContext items={flatIds} strategy={verticalListSortingStrategy}>
            {flatNodes.map((n) => (
              <SortableTreeItem
                key={n.id}
                node={n}
                selected={selectedId === n.id}
                mode={effectiveDragMode}
                shiftScope={shiftScope}
                moveOver={effectiveDragMode === 'move' && overId === n.id && activeId !== n.id}
                onSelect={onSelect}
                onToggleExpand={toggleExpand}
              />
            ))}
          </SortableContext>
          {/* 拖拽中才出现的安全取消区：拖到此处松手放弃本次调整 */}
          {activeId != null && <DragCancelZone />}
          {createPortal(
            <DragOverlay>
              {activeNode ? <DeptRow node={activeNode} selected={false} overlay /> : null}
            </DragOverlay>,
            document.body,
          )}
        </DndContext>
        {flatNodes.length === 0 && (
          <div style={{ padding: '32px 16px', textAlign: 'center', color: 'var(--text-tertiary)' }}>
            {search ? (
              '无匹配部门'
            ) : (
              <>
                暂无部门
                <div style={{ fontSize: 12, marginTop: 4 }}>成员可在上方「全部成员」中查看管理</div>
              </>
            )}
          </div>
        )}
      </div>
      <div
        style={{
          padding: '10px 16px',
          borderTop: '1px solid var(--divider)',
        }}
      >
        <PermissionGate permission="org.dept.create">
          <ReadonlyButton
            type="primary"
            block
            size="small"
            icon={<PlusOutlined />}
            onClick={() => onCreateDept(null)}
          >
            新增部门
          </ReadonlyButton>
        </PermissionGate>
        <PermissionGate permission="org.dept.create">
          <div style={{ marginTop: 8 }}>
            <ImportEntryButton
              entityCode="sys.dept"
              label="导入部门"
              size="small"
              block
              onImported={onImported}
            />
          </div>
        </PermissionGate>
      </div>
    </div>
  );
}
