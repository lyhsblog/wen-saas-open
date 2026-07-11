import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { viewPrefApi } from '@wen-saas/api-client';
import {
  resolveColumns,
  type ColumnPref,
  type ColumnPanel,
  type PrefColumn,
} from '@wen-saas/shared-ui';
import type { ColumnType } from 'antd/lib/table';
import { http } from '../../lib/http';

const api = viewPrefApi(http);
const keyOf = (viewKey: string) => ['view-pref', viewKey] as const;

export interface UseColumnPreferenceResult<T> {
  columns: ColumnType<T>[];
  panel: ColumnPanel;
  isCustomized: boolean;
  isLoading: boolean;
  saving: boolean;
  apply: (next: ColumnPref) => Promise<void>;
  reset: () => Promise<void>;
}

/**
 * 用户级列偏好绑定 hook。拉 pref → resolveColumns → 暴露列 + 弹层 panel + 保存/重置。
 * allColumns 建议在调用方 useMemo 稳定，避免无谓重算。
 */
export function useColumnPreference<T>(
  viewKey: string,
  allColumns: PrefColumn<T>[],
): UseColumnPreferenceResult<T> {
  const qc = useQueryClient();
  const prefQuery = useQuery({ queryKey: keyOf(viewKey), queryFn: () => api.get(viewKey) });

  const data = prefQuery.data;
  const resolved = useMemo(() => {
    const pref: ColumnPref | null =
      data && data.customized ? { customKeys: data.customKeys, hiddenKeys: data.hiddenKeys } : null;
    return resolveColumns(allColumns, pref);
  }, [allColumns, data]);

  const saveMutation = useMutation({
    mutationFn: (next: ColumnPref) => api.save(viewKey, next.customKeys, next.hiddenKeys),
    onSuccess: () => qc.invalidateQueries({ queryKey: keyOf(viewKey) }),
  });
  const resetMutation = useMutation({
    mutationFn: () => api.reset(viewKey),
    onSuccess: () => qc.invalidateQueries({ queryKey: keyOf(viewKey) }),
  });

  return {
    columns: resolved.columns,
    panel: resolved.panel,
    isCustomized: !!data?.customized,
    isLoading: prefQuery.isLoading,
    saving: saveMutation.isPending || resetMutation.isPending,
    apply: (next) => saveMutation.mutateAsync(next),
    reset: () => resetMutation.mutateAsync(),
  };
}
