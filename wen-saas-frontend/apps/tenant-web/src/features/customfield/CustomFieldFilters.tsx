import { useMemo } from 'react';
import { DatePicker, Input, InputNumber, Select } from 'antd';
import dayjs from 'dayjs';
import type { FilterBarField } from '@wen-saas/shared-ui';
import { useCfFields } from './customFieldQueries';
import { EntityRefPicker } from './EntityRefPicker';
import { DictEnumSelect } from './DictEnumSelect';

/**
 * 索引化自定义字段 → FilterBar 筛选字段项。仅索引字段进筛选区。
 * 控件统一 width:100%，实际宽度交给 FilterBar 栅格；label 由 FilterBar 渲染在控件上方。
 * 受控 draft：只更新 draft，由页面「搜索」按钮提交。
 */
export function useCustomFieldFilterFields(
  objectCode: string,
  value: Record<string, string>,
  onChange: (next: Record<string, string>) => void,
): FilterBarField[] {
  const { data } = useCfFields(objectCode);
  const indexed = useMemo(() => (data ?? []).filter((d) => d.indexed), [data]);

  return useMemo(() => {
    const set = (code: string, v: string | number | null | undefined) => {
      const next = { ...value };
      const s = v == null ? '' : String(v).trim();
      if (s === '') delete next[code];
      else next[code] = s;
      onChange(next);
    };

    return indexed.map((d): FilterBarField => {
      const type = (d.type || '').toLowerCase();
      const current = value[d.fieldCode];
      let control: React.ReactNode;
      if (type === 'bool') {
        control = (
          <Select
            allowClear
            placeholder="请选择"
            style={{ width: '100%' }}
            {...(current ? { value: current } : {})}
            onChange={(v) => set(d.fieldCode, v)}
            options={[
              { value: 'true', label: '是' },
              { value: 'false', label: '否' },
            ]}
          />
        );
      } else if (type === 'number') {
        control = (
          <InputNumber
            placeholder="请输入"
            style={{ width: '100%' }}
            {...(current ? { value: Number(current) } : {})}
            onChange={(v) => set(d.fieldCode, v)}
          />
        );
      } else if (type === 'ref' && d.refObjectCode) {
        control = (
          <EntityRefPicker
            objectCode={d.refObjectCode}
            placeholder="输入名称搜索"
            style={{ width: '100%' }}
            {...(current ? { value: current } : {})}
            onChange={(v) => set(d.fieldCode, v)}
          />
        );
      } else if (type === 'date') {
        // 筛选值与表单一致：对外保持 "YYYY-MM-DD" 字符串，仅在 DatePicker 边界换算 dayjs
        control = (
          <DatePicker
            style={{ width: '100%' }}
            format="YYYY-MM-DD"
            value={current ? dayjs(current) : null}
            onChange={(v) => set(d.fieldCode, v ? v.format('YYYY-MM-DD') : null)}
          />
        );
      } else if (type === 'enum' && d.refDictCode) {
        control = (
          <DictEnumSelect
            dictCode={d.refDictCode}
            placeholder="请选择"
            style={{ width: '100%' }}
            allowClear
            {...(current ? { value: current } : {})}
            onChange={(v) => set(d.fieldCode, v)}
          />
        );
      } else {
        control = (
          <Input
            allowClear
            placeholder={type === 'ref' ? '输入 ID' : '请输入'}
            style={{ width: '100%' }}
            value={current ?? ''}
            onChange={(e) => set(d.fieldCode, e.target.value)}
          />
        );
      }
      return { key: d.fieldCode, label: d.fieldName, control };
    });
  }, [indexed, value, onChange]);
}
