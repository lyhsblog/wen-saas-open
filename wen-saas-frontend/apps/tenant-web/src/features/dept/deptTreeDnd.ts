import type { DeptView } from '@wen-saas/domain';

/** 扁平化后的部门节点（dnd-kit 只渲染可见行，折叠子树不入列表）。 */
export type FlatDeptNode = {
  /** String(dept.id)，雪花 id 一律按字符串处理（勿转 number 丢精度）。 */
  id: string;
  /** 父级 id；'0' 表示一级。 */
  parentId: string;
  /** 深度，从 0 计（一级 depth=0）。 */
  depth: number;
  /** 在其兄弟中的位置（基于可见列表）。 */
  index: number;
  /** 原节点引用（取 name/status/memberCount）。 */
  dept: DeptView;
  hasChildren: boolean;
  /** 有子节点但未展开。 */
  collapsed: boolean;
};

/**
 * 把部门树深度优先扁平化为可见行列表。未展开的节点不下钻其 children，
 * 因此折叠态下整棵子树不进列表——dnd-kit 只对可见行做排序/挤动。
 */
export function flattenTree(treeData: DeptView[], expandedKeys: Set<string>): FlatDeptNode[] {
  const out: FlatDeptNode[] = [];
  function walk(nodes: DeptView[], parentId: string, depth: number) {
    nodes.forEach((n, index) => {
      const id = String(n.id ?? '');
      const hasChildren = !!n.children?.length;
      const expanded = expandedKeys.has(id);
      out.push({
        id,
        parentId,
        depth,
        index,
        dept: n,
        hasChildren,
        collapsed: hasChildren && !expanded,
      });
      if (hasChildren && expanded) walk(n.children!, id, depth + 1);
    });
  }
  walk(treeData, '0', 0);
  return out;
}

/** 取某父级下、当前可见的直接子项（保持原顺序）。 */
export function getSiblings(flat: FlatDeptNode[], parentId: string): FlatDeptNode[] {
  return flat.filter((n) => n.parentId === parentId);
}

/**
 * 把本地受控顺序（parentId → 有序 id 数组）应用到树：按 order 重排对应父级的 children，
 * 子树整体随之移动。用于拖放成功到 refetch 回来之间保持 UI 不回弹。
 */
export function applyLocalOrder(
  treeData: DeptView[],
  order: Record<string, string[]> | null,
): DeptView[] {
  if (!order) return treeData;
  function reorder(nodes: DeptView[], parentId: string): DeptView[] {
    let arr = nodes;
    const ord = order![parentId];
    if (ord) {
      const rank = (n: DeptView) => {
        const i = ord.indexOf(String(n.id));
        return i < 0 ? Number.MAX_SAFE_INTEGER : i;
      };
      arr = [...nodes].sort((a, b) => rank(a) - rank(b));
    }
    return arr.map((n) =>
      n.children?.length ? { ...n, children: reorder(n.children, String(n.id)) } : n,
    );
  }
  return reorder(treeData, '0');
}

/** 查父级 id（顶层返回 '0'，找不到返回 null）。 */
export function parentIdOf(nodes: DeptView[], id: string, parent = '0'): string | null {
  for (const n of nodes) {
    if (String(n.id) === id) return parent;
    if (n.children?.length) {
      const r = parentIdOf(n.children, id, String(n.id));
      if (r !== null) return r;
    }
  }
  return null;
}

/** 收集某节点下全部后代 id（不含自身）。用于排序时把「同级子树」当作整块一起让位。 */
export function collectDescendantIds(treeData: DeptView[], id: string): Set<string> {
  const out = new Set<string>();
  function findNode(nodes: DeptView[]): DeptView | null {
    for (const n of nodes) {
      if (String(n.id) === id) return n;
      if (n.children?.length) {
        const r = findNode(n.children);
        if (r) return r;
      }
    }
    return null;
  }
  function collect(nodes: DeptView[]) {
    for (const n of nodes) {
      out.add(String(n.id));
      if (n.children?.length) collect(n.children);
    }
  }
  const node = findNode(treeData);
  if (node?.children?.length) collect(node.children);
  return out;
}

/** ancestorId 是否为 nodeId 的祖先（含本身）——move 模式防止把节点拖进自己子树成环。 */
export function isDescendant(treeData: DeptView[], ancestorId: string, nodeId: string): boolean {
  if (ancestorId === nodeId) return true;
  function findNode(nodes: DeptView[]): DeptView | null {
    for (const n of nodes) {
      if (String(n.id) === ancestorId) return n;
      if (n.children?.length) {
        const r = findNode(n.children);
        if (r) return r;
      }
    }
    return null;
  }
  const anc = findNode(treeData);
  if (!anc?.children?.length) return false;
  function contains(nodes: DeptView[]): boolean {
    for (const n of nodes) {
      if (String(n.id) === nodeId) return true;
      if (n.children?.length && contains(n.children)) return true;
    }
    return false;
  }
  return contains(anc.children);
}

/**
 * 排序模式 projection（同级锁定）：
 * 把落点钳制在被拖节点所属父级的兄弟范围内，不改父级、不改 depth，
 * 从而让"挤动"动画只发生在同级兄弟之间。返回目标在该兄弟组内的插入下标。
 */
export function getSortProjection(
  flat: FlatDeptNode[],
  activeId: string,
  overId: string,
): { parentId: string; targetIndex: number } | null {
  const active = flat.find((n) => n.id === activeId);
  if (!active) return null;
  const siblings = getSiblings(flat, active.parentId);
  const fromIdx = siblings.findIndex((n) => n.id === activeId);
  if (fromIdx < 0) return null;

  // over 命中同组兄弟 → 用其下标；否则（拖出本组）钳到首/尾。
  const overSiblingIdx = siblings.findIndex((n) => n.id === overId);
  let targetIndex: number;
  if (overSiblingIdx >= 0) {
    targetIndex = overSiblingIdx;
  } else {
    // over 在本组之外：按其在整个扁平列表中的位置决定钳到头还是尾。
    const overFlatIdx = flat.findIndex((n) => n.id === overId);
    const firstFlatIdx = flat.findIndex((n) => n.parentId === active.parentId);
    targetIndex = overFlatIdx >= 0 && overFlatIdx < firstFlatIdx ? 0 : siblings.length - 1;
  }
  return { parentId: active.parentId, targetIndex };
}
