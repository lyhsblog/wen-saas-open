import { useMemo } from 'react';
import { Select } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { dictApi } from '../dict/dictApi';

interface Props {
  /** 枚举字段引用的字典分组 code（= CustomFieldDef.refDictCode）。 */
  dictCode: string;
  /** 当前选中的字典项 code（enum 存的是 item code；Form.Item 注入或筛选区显式传）。 */
  value?: string;
  onChange?: (v: string | undefined) => void;
  placeholder?: string;
  style?: React.CSSProperties;
  allowClear?: boolean;
}

/**
 * 枚举（enum）自定义字段的字典下拉。按 refDictCode 拉字典项，value=字典项 code、灰显 code（B 端交互规范 §七.6(e)）。
 * 拉全量（含停用）以便已选的停用项仍能回显其名称，但停用项标 disabled 不可新选。
 * 表单与筛选两处共用：表单内由父 antd Form.Item 注入 value/onChange，筛选区显式传入。
 */
export function DictEnumSelect({
  dictCode,
  value,
  onChange,
  placeholder,
  style,
  allowClear,
}: Props) {
  const { data } = useQuery({
    queryKey: ['dict-items', dictCode],
    queryFn: () => dictApi.listItems(dictCode, true),
    enabled: !!dictCode,
  });

  const options = useMemo(
    () =>
      (data ?? [])
        .filter((it) => it.code != null)
        .map((it) => ({
          value: it.code as string,
          label: it.name ?? (it.code as string),
          disabled: it.status === 0,
        })),
    [data],
  );

  return (
    <Select
      showSearch
      optionFilterProp="label"
      placeholder={placeholder ?? '请选择'}
      {...(allowClear ? { allowClear: true } : {})}
      {...(style ? { style } : {})}
      {...(value ? { value } : {})}
      onChange={(v) => onChange?.(v ?? undefined)}
      options={options}
      optionRender={(opt) => (
        <span>
          {opt.label}
          <span style={{ color: 'var(--text-tertiary)', marginLeft: 6, fontSize: 12 }}>
            {opt.value}
          </span>
        </span>
      )}
    />
  );
}
