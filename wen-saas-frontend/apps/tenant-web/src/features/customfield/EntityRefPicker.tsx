import { useMemo, useRef, useState } from 'react';
import { Select, Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { entityLookupApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

const api = entityLookupApi(http);

interface Props {
  /** 被关联对象类型（= ref 字段的 refObjectCode / NameSearch entityType）。 */
  objectCode: string;
  /** 当前选中 id（Form.Item 注入）。 */
  value?: string;
  onChange?: (v: string | undefined) => void;
  /** 已知的展示名（编辑回显用，如详情里的 cf_xxx__label）。 */
  label?: string;
  placeholder?: string;
  style?: React.CSSProperties;
}

/**
 * 通用引用对象选择器。下拉 = 单关键字远程搜索（OR 命中该对象声明的可搜字段，≤20 条）。
 * 占位符按对象的可搜字段 label 拼出（如「输入单据编号/客户摘要搜索」），让用户知道能输入哪些；下拉主行 =
 * 主字段（{@code primary}）、淡色 = 次字段（{@code secondary}）——code 主显的实体（如单据）主行即编号。
 * 编辑回显：value 有、label 有时先种入一条 option；否则回退显示 id，重新搜索可选回。
 */
export function EntityRefPicker({ objectCode, value, onChange, label, placeholder, style }: Props) {
  const [kw, setKw] = useState('');
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  // 拉一次可搜类型清单（缓存），取本对象的可搜字段拼提示文案；非搜索态、对 ref 字段也只一次。
  const typesQuery = useQuery({
    queryKey: ['entity-lookup', 'types'],
    queryFn: () => api.types(),
    staleTime: 5 * 60 * 1000,
  });
  const fieldLabels = useMemo(
    () => typesQuery.data?.find((t) => t.code === objectCode)?.fields.map((f) => f.label) ?? [],
    [typesQuery.data, objectCode],
  );
  const resolvedPlaceholder =
    placeholder ?? (fieldLabels.length ? `输入${fieldLabels.join('/')}搜索` : '输入关键字搜索');

  const searchQuery = useQuery({
    queryKey: ['entity-lookup', objectCode, kw],
    queryFn: () => api.search(objectCode, kw, 0),
    enabled: kw.trim().length > 0,
  });
  const options = useMemo(() => {
    const hits = searchQuery.data ?? [];
    const opts: { value: string; label: React.ReactNode }[] = hits.map((h) => {
      const main = h.primary ?? h.display;
      return {
        value: h.id,
        label: h.secondary ? (
          <span>
            {main}
            <span style={{ color: 'var(--text-tertiary)', marginLeft: 6, fontSize: 12 }}>
              {h.secondary}
            </span>
          </span>
        ) : (
          main
        ),
      };
    });
    if (value && !opts.some((o) => o.value === value)) {
      opts.unshift({ value, label: label ?? value });
    }
    return opts;
  }, [searchQuery.data, value, label]);

  const onSearch = (v: string) => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => setKw(v), 300);
  };

  return (
    <Select
      showSearch
      allowClear
      filterOption={false}
      placeholder={resolvedPlaceholder}
      {...(style ? { style } : {})}
      {...(value ? { value } : {})}
      onSearch={onSearch}
      onChange={(v) => onChange?.(v ?? undefined)}
      notFoundContent={searchQuery.isFetching ? <Spin size="small" /> : null}
      options={options}
    />
  );
}
