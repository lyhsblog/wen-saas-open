import type { ColumnType } from 'antd/lib/table';

/**
 * 用户级列偏好核心逻辑。纯函数，无副作用、无数据请求——
 * 数据拉取 / 持久化由消费层的 react-query hook 负责（保持 shared-ui 不依赖数据层）。
 */

export type ColumnPin = 'left' | 'right';

/** 业务方声明的列：在 antd ColumnType 基础上加偏好元信息。 */
export interface PrefColumn<T> extends ColumnType<T> {
  /** 稳定唯一 key，持久化用。标准列硬编码；自定义字段列用 fieldCode（cf_xxx）。 */
  key: string;
  /** 固定列：left=头部、right=尾部（操作列）。固定列不参与排序/显隐。 */
  pin?: ColumnPin;
  /** 列设置弹层里的展示名（title 可能是 ReactNode，这里要纯文本）。 */
  settingLabel?: string;
}

/** 持久化的增量偏好。 */
export interface ColumnPref {
  /** 用户显式摆放过的可见非固定列，有序。 */
  customKeys: string[];
  /** 用户显式隐藏的列，集合语义（顺序无意义）。 */
  hiddenKeys: string[];
}

export interface ColumnPanelItem {
  key: string;
  label: string;
  pin?: ColumnPin;
}

/** 列设置弹层的三块分组。 */
export interface ColumnPanel {
  /** 固定列（只读，不可拖/隐）。 */
  fixed: ColumnPanelItem[];
  /** 可见可调列（有序）。 */
  visible: ColumnPanelItem[];
  /** 隐藏列。 */
  hidden: ColumnPanelItem[];
}

export interface ResolveResult<T> {
  /** 最终渲染列，直接喂给 <DataTable columns={} />；pin 映射为 antd fixed。 */
  columns: ColumnType<T>[];
  /** 列设置弹层用的三块分组。 */
  panel: ColumnPanel;
}

function panelItem<T>(c: PrefColumn<T>): ColumnPanelItem {
  const label = c.settingLabel ?? (typeof c.title === 'string' ? c.title : c.key);
  return c.pin ? { key: c.key, label, pin: c.pin } : { key: c.key, label };
}

/** 渲染前剥掉偏好元字段，并把 pin 映射为 antd 的 fixed（粘性列）。 */
function toAntdColumn<T>(c: PrefColumn<T>): ColumnType<T> {
  const rest: PrefColumn<T> = { ...c };
  const pin = rest.pin;
  delete rest.pin;
  delete rest.settingLabel;
  return pin ? { ...rest, fixed: pin } : rest;
}

/**
 * 三态合并。代码是「列是否存在 / 是否固定」的唯一真相源；
 * pref 仅表达排序与显隐偏好。
 *
 * @param allColumns 列全集（标准列 ∪ 运行时拼入的自定义字段列），默认顺序
 * @param pref       用户增量偏好；null = 默认态（全部可见、代码顺序）
 */
export function resolveColumns<T>(
  allColumns: PrefColumn<T>[],
  pref: ColumnPref | null,
): ResolveResult<T> {
  const left = allColumns.filter((c) => c.pin === 'left');
  const right = allColumns.filter((c) => c.pin === 'right');
  const movable = allColumns.filter((c) => c.pin == null);

  let visible: PrefColumn<T>[];
  let hidden: PrefColumn<T>[];

  if (!pref) {
    visible = movable;
    hidden = [];
  } else {
    const movableByKey = new Map(movable.map((c) => [c.key, c]));
    const known = new Set(movableByKey.keys());
    // 忽略未知 key（列已删/改名）；固定列即使误入 hiddenKeys 也不在 movable，自然排除
    const hiddenSet = new Set(pref.hiddenKeys.filter((k) => known.has(k)));

    const seen = new Set<string>();
    const orderedVisible: PrefColumn<T>[] = [];
    for (const k of pref.customKeys) {
      // 隐藏优先：同时出现在两数组时从可见区剔除；去重
      if (!known.has(k) || hiddenSet.has(k) || seen.has(k)) continue;
      seen.add(k);
      orderedVisible.push(movableByKey.get(k)!);
    }

    // 第三态：既不在 customKeys 也不在 hiddenKeys 的列（代码新增 / 新建自定义字段）→ 默认显示，按代码顺序追加
    const leftover = movable.filter((c) => !seen.has(c.key) && !hiddenSet.has(c.key));
    visible = [...orderedVisible, ...leftover];
    hidden = movable.filter((c) => hiddenSet.has(c.key));
  }

  return {
    columns: [...left, ...visible, ...right].map(toAntdColumn),
    panel: {
      fixed: [...left, ...right].map(panelItem),
      visible: visible.map(panelItem),
      hidden: hidden.map(panelItem),
    },
  };
}
