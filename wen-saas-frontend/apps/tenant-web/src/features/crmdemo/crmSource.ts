import { useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { dictApi } from '../dict/dictApi';

/** 「客户来源」字典分组 code（与后端 CrmDemoModule.DICT_CUSTOMER_SOURCE 一致）。 */
export const CRM_SOURCE_DICT = 'crm_demo.customer_source';

/**
 * 来源 code → 中文名翻译。与 DictEnumSelect 共用 ['dict-items', dictCode] 缓存，
 * 翻不到（项被删）按 code 兜底展示。返回函数按字典数据 memo，可安全进列定义依赖。
 */
export function useSourceName(): (code: string | null | undefined) => string {
  const { data } = useQuery({
    queryKey: ['dict-items', CRM_SOURCE_DICT],
    queryFn: () => dictApi.listItems(CRM_SOURCE_DICT, true),
  });
  return useCallback(
    (code: string | null | undefined) => {
      if (!code) return '-';
      return data?.find((it) => it.code === code)?.name ?? code;
    },
    [data],
  );
}
