import { Checkbox } from 'antd';
import type { CatalogTreeNode } from '@wen-saas/api-client';

/**
 * 角色 / 推荐角色共用的权限目录树（sys_menu 派生的 catalogTree）。
 * 中文分组标题来自 sys_menu 节点名；容器三态全选、可授码叶子单选；递归渲染。
 *
 * <p>`lockedCodes`：已锁定（如已发布推荐角色已含的码）叶子置灰、禁止单独取消；分组「取消全选」的
 * 移除过滤交由调用方 `onSet`（不把 locked 码从已选集移除）。
 */

/** 取子树全部可授码（容器自身无码，靠子叶聚合）。 */
export function collectCatalogCodes(node: CatalogTreeNode): string[] {
  if (node.code && !(node.children && node.children.length > 0)) return [node.code];
  return (node.children ?? []).flatMap(collectCatalogCodes);
}

/** 叶子码 → 展示信息（含顶层中文分组名），供「已选权限点」按组回显。 */
export type CatalogLeaf = { code: string; name: string; description: string | null; group: string };

/** 拍平目录树为叶子码列表，group = 其顶层容器中文名（顶层即叶子时 group=自身 label）。 */
export function flattenCatalogLeaves(tree: CatalogTreeNode[]): CatalogLeaf[] {
  const out: CatalogLeaf[] = [];
  const walk = (node: CatalogTreeNode, group: string) => {
    const isLeaf = !!node.code && !(node.children && node.children.length > 0);
    if (isLeaf) {
      out.push({
        code: node.code as string,
        name: node.label,
        description: node.description,
        group,
      });
      return;
    }
    (node.children ?? []).forEach((c) => walk(c, group));
  };
  tree.forEach((top) => walk(top, top.label));
  return out;
}

type TreeProps = {
  tree: CatalogTreeNode[];
  checked: Set<string>;
  onSet: (codes: string[], on: boolean) => void;
  lockedCodes?: Set<string>;
};

export function PermissionCatalogTree({ tree, checked, onSet, lockedCodes }: TreeProps) {
  const locked = lockedCodes ?? new Set<string>();
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, width: '100%' }}>
      {tree.map((node) => (
        <CatalogNodeBlock
          key={node.key}
          node={node}
          checked={checked}
          onSet={onSet}
          locked={locked}
        />
      ))}
    </div>
  );
}

type NodeProps = {
  node: CatalogTreeNode;
  checked: Set<string>;
  onSet: (codes: string[], on: boolean) => void;
  locked: Set<string>;
};

/** 顶层分组：中文分组标题 + 分组三态全选，内部递归。 */
function CatalogNodeBlock({ node, checked, onSet, locked }: NodeProps) {
  const codes = collectCatalogCodes(node);
  const onCount = codes.filter((c) => checked.has(c)).length;
  const allOn = codes.length > 0 && onCount === codes.length;
  const some = onCount > 0 && !allOn;
  const isLeaf = !!node.code && !(node.children && node.children.length > 0);

  if (isLeaf) {
    return (
      <div style={{ border: '1px solid var(--border)', borderRadius: 'var(--r-md)', padding: 12 }}>
        <LeafCheckbox node={node} checked={checked} onSet={onSet} locked={locked} />
      </div>
    );
  }

  return (
    <div
      style={{ border: '1px solid var(--border)', borderRadius: 'var(--r-md)', overflow: 'hidden' }}
    >
      <div
        style={{
          padding: '8px 12px',
          background: 'var(--bg-muted)',
          borderBottom: '1px solid var(--divider)',
        }}
      >
        <Checkbox
          checked={allOn}
          indeterminate={some}
          onChange={(e) => onSet(codes, e.target.checked)}
        >
          <span style={{ fontWeight: 600, fontSize: 13 }}>{node.label}</span>
          <span style={{ color: 'var(--text-tertiary)', fontSize: 12, marginLeft: 8 }}>
            {onCount}/{codes.length}
          </span>
        </Checkbox>
      </div>
      <div style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 6 }}>
        {(node.children ?? []).map((child) => (
          <CatalogChildRow
            key={child.key}
            node={child}
            depth={0}
            checked={checked}
            onSet={onSet}
            locked={locked}
          />
        ))}
      </div>
    </div>
  );
}

/** 分组内子行：容器（有子动作页）渲染子三态 + 递归；叶子渲染单复选框。 */
function CatalogChildRow({ node, depth, checked, onSet, locked }: NodeProps & { depth: number }) {
  const isLeaf = !!node.code && !(node.children && node.children.length > 0);
  if (isLeaf) {
    return (
      <div style={{ paddingLeft: depth * 18 }}>
        <LeafCheckbox node={node} checked={checked} onSet={onSet} locked={locked} />
      </div>
    );
  }
  const codes = collectCatalogCodes(node);
  const onCount = codes.filter((c) => checked.has(c)).length;
  const allOn = codes.length > 0 && onCount === codes.length;
  const some = onCount > 0 && !allOn;
  return (
    <div style={{ paddingLeft: depth * 18 }}>
      <Checkbox
        checked={allOn}
        indeterminate={some}
        onChange={(e) => onSet(codes, e.target.checked)}
      >
        <span style={{ fontWeight: 500 }}>{node.label}</span>
      </Checkbox>
      <div
        style={{
          paddingLeft: 18,
          display: 'flex',
          flexWrap: 'wrap',
          gap: '4px 16px',
          marginTop: 4,
        }}
      >
        {(node.children ?? []).map((child) => (
          <CatalogChildRow
            key={child.key}
            node={child}
            depth={0}
            checked={checked}
            onSet={onSet}
            locked={locked}
          />
        ))}
      </div>
    </div>
  );
}

function LeafCheckbox({ node, checked, onSet, locked }: NodeProps) {
  const code = node.code as string;
  return (
    <Checkbox
      checked={checked.has(code)}
      disabled={locked.has(code)}
      onChange={(e) => onSet([code], e.target.checked)}
    >
      <span title={node.description ?? undefined}>{node.label}</span>
      <span style={{ color: 'var(--text-tertiary)', fontSize: 12, marginLeft: 6 }}>{code}</span>
    </Checkbox>
  );
}
