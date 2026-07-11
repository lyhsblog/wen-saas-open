/**
 * 跨端通用枚举展示件。
 *
 * 约定：**文案 + 语义 tone 共享**（单一真相源），**配色不共享**——各端把 {@link Tone} 自行映射到
 * 本端 UI 库的色值（ops/tenant 走 antd Tag 色、h5 走 antd-mobile）。
 *
 * 枚举家约定：跨端复用的枚举一律放在 `packages/api-client/src/enums/`（按域一文件、各自独立定义、
 * 互不引用）；单端私有枚举留在各 app 本地，不进此目录。
 */

/** 语义色调（与具体 UI 库无关）。与 ops StatusBadge['kind'] 取同一集合。 */
export type Tone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

/** 「文案 + 语义 tone」单元。 */
export interface ToneLabel {
  label: string;
  tone: Tone;
}

/** 通用下拉选项。 */
export interface SelectOption<T extends string = string> {
  value: T;
  label: string;
}

/** 把 `{值: {label}}` 映射转成下拉选项数组（保持键插入顺序）。 */
export function metaToOptions<T extends string>(
  meta: Record<T, { label: string }>,
): SelectOption<T>[] {
  return (Object.keys(meta) as T[]).map((value) => ({ value, label: meta[value].label }));
}

/** 把 `{值: 文案}` 映射转成下拉选项数组（保持键插入顺序）。 */
export function labelMapToOptions<T extends string>(map: Record<T, string>): SelectOption<T>[] {
  return (Object.keys(map) as T[]).map((value) => ({ value, label: map[value] }));
}
