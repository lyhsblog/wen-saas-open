import { useMemo } from 'react';
import type { PrefColumn } from '@wen-saas/shared-ui';
import type { CustomFieldDef } from '@wen-saas/api-client';
import { useCfFields } from './customFieldQueries';
import { renderCfValue } from './cfRender';

type HasExtra = { extra?: Record<string, unknown> | null };

/** 把某对象的自定义字段定义翻成 antd 列（PrefColumn）。enum/ref 优先用 __label，bool 渲染是/否。 */
export function useCustomFieldColumns<T extends HasExtra>(objectCode: string): PrefColumn<T>[] {
  const { data } = useCfFields(objectCode);
  return useMemo(() => (data ?? []).map((d) => toColumn<T>(d)), [data]);
}

function toColumn<T extends HasExtra>(d: CustomFieldDef): PrefColumn<T> {
  const type = (d.type || '').toLowerCase();
  return {
    key: d.fieldCode,
    title: d.fieldName,
    settingLabel: d.fieldName,
    render: (_value: unknown, row: T) => renderCfValue(type, d.fieldCode, row.extra ?? {}),
  };
}
