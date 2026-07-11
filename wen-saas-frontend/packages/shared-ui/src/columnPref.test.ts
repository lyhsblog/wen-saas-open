import { describe, expect, it } from 'vitest';
import { resolveColumns, type ColumnPref, type PrefColumn } from './columnPref';

type Row = Record<string, unknown>;

/** 构造一组标准列全集：固定头(name) + 可调(code/status/remark) + 固定尾(actions)。 */
function cols(extra: PrefColumn<Row>[] = []): PrefColumn<Row>[] {
  return [
    { key: 'name', title: '名称', pin: 'left' },
    { key: 'code', title: '编号' },
    { key: 'status', title: '状态' },
    { key: 'remark', title: '备注' },
    ...extra,
    { key: 'actions', title: '操作', pin: 'right' },
  ];
}

const keysOf = (items: { key: string }[]) => items.map((i) => i.key);

describe('resolveColumns', () => {
  it('pref=null：全部可见、代码顺序，固定列在两端', () => {
    const { columns, panel } = resolveColumns(cols(), null);
    expect(keysOf(columns as { key: string }[])).toEqual([
      'name',
      'code',
      'status',
      'remark',
      'actions',
    ]);
    expect(keysOf(panel.fixed)).toEqual(['name', 'actions']);
    expect(keysOf(panel.visible)).toEqual(['code', 'status', 'remark']);
    expect(panel.hidden).toEqual([]);
  });

  it('按 customKeys 排序，hiddenKeys 隐藏', () => {
    const pref: ColumnPref = { customKeys: ['status', 'code'], hiddenKeys: ['remark'] };
    const { columns, panel } = resolveColumns(cols(), pref);
    // 固定头 + 自定义顺序 + 固定尾
    expect(keysOf(columns as { key: string }[])).toEqual(['name', 'status', 'code', 'actions']);
    expect(keysOf(panel.visible)).toEqual(['status', 'code']);
    expect(keysOf(panel.hidden)).toEqual(['remark']);
  });

  it('未知 key（列已删/改名）被忽略', () => {
    const pref: ColumnPref = { customKeys: ['ghost', 'code'], hiddenKeys: ['phantom'] };
    const { panel } = resolveColumns(cols(), pref);
    expect(keysOf(panel.visible)).toContain('code');
    expect(keysOf(panel.visible)).not.toContain('ghost');
    expect(keysOf(panel.hidden)).not.toContain('phantom');
  });

  it('同一 key 同时在两数组 → 隐藏优先', () => {
    // 全列入 customKeys 以排除 leftover 干扰，单测「冲突时 code 进隐藏区」
    const pref: ColumnPref = { customKeys: ['code', 'status', 'remark'], hiddenKeys: ['code'] };
    const { panel } = resolveColumns(cols(), pref);
    expect(keysOf(panel.visible)).toEqual(['status', 'remark']);
    expect(keysOf(panel.hidden)).toEqual(['code']);
  });

  it('固定列即使误入 hiddenKeys 也不会被隐藏', () => {
    const pref: ColumnPref = { customKeys: [], hiddenKeys: ['name', 'actions'] };
    const { columns, panel } = resolveColumns(cols(), pref);
    expect(keysOf(columns as { key: string }[])).toContain('name');
    expect(keysOf(columns as { key: string }[])).toContain('actions');
    expect(keysOf(panel.fixed)).toEqual(['name', 'actions']);
    expect(keysOf(panel.hidden)).not.toContain('name');
  });

  it('代码新增列（不在 pref）→ 默认显示，追加到可见区末尾', () => {
    const pref: ColumnPref = { customKeys: ['status', 'code'], hiddenKeys: ['remark'] };
    // 新增一个自定义字段列 cf_source（pref 里没有）
    const all = cols([{ key: 'cf_source', title: '客户来源' }]);
    const { panel } = resolveColumns(all, pref);
    expect(keysOf(panel.visible)).toEqual(['status', 'code', 'cf_source']);
  });

  it('customKeys 去重', () => {
    const pref: ColumnPref = { customKeys: ['code', 'code', 'status'], hiddenKeys: [] };
    const { panel } = resolveColumns(cols(), pref);
    expect(keysOf(panel.visible)).toEqual(['code', 'status', 'remark']);
  });

  it('pin 映射为 antd fixed，且剥离偏好元字段', () => {
    const { columns } = resolveColumns(cols(), null);
    const name = (columns as Record<string, unknown>[]).find((c) => c.key === 'name')!;
    const code = (columns as Record<string, unknown>[]).find((c) => c.key === 'code')!;
    expect(name.fixed).toBe('left');
    expect(name.pin).toBeUndefined();
    expect(name.settingLabel).toBeUndefined();
    expect(code.fixed).toBeUndefined();
  });

  it('软删自定义字段：pref 残留 key 不报错且被忽略', () => {
    // 用户曾把 cf_old 摆进可见、cf_gone 隐藏，但当前列全集已无这两列
    const pref: ColumnPref = { customKeys: ['cf_old', 'code'], hiddenKeys: ['cf_gone'] };
    const { panel } = resolveColumns(cols(), pref);
    expect(keysOf(panel.visible)).toEqual(['code', 'status', 'remark']);
    expect(panel.hidden).toEqual([]);
  });

  it('settingLabel 优先于 title 作为弹层展示名', () => {
    const all: PrefColumn<Row>[] = [{ key: 'k1', title: '原始标题', settingLabel: '设置名' }];
    const { panel } = resolveColumns(all, null);
    expect(panel.visible[0]?.label).toBe('设置名');
  });
});
