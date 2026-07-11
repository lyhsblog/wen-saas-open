import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { opsPackageApi, type OpsPackageCatalogItemView } from '@wen-saas/api-client';
import { http } from '../../../lib/http';

/**
 * 套餐目录（OPS-T-011）。统一从后端 `GET /api/v1/ops/packages` 拉取，
 * 替代历史硬编码 `PACKAGE_META`。catalog 即原始目录，byCode 供按 code 查名/有效期。
 * status 为大写枚举，仅 'ACTIVE' 可勾选。
 */
export function useOpsPackages() {
  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'packages'],
    queryFn: () => opsPackageApi(http).listPackages(),
    staleTime: 5 * 60 * 1000,
  });

  const catalog = useMemo<OpsPackageCatalogItemView[]>(() => data ?? [], [data]);
  const byCode = useMemo(() => {
    const map = new Map<string, OpsPackageCatalogItemView>();
    for (const p of catalog) map.set(p.code, p);
    return map;
  }, [catalog]);

  return { catalog, byCode, isLoading };
}
