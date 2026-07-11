import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ImportPreviewFilter } from '@wen-saas/api-client';
import { importApi } from './taskApi';
import { taskKeys } from './taskQueries';

export const importKeys = {
  all: ['imports'] as const,
  entities: () => [...importKeys.all, 'entities'] as const,
};

/** 可导入实体列表（含字段定义），向导第 1 步选择对象用。 */
export function useImportEntities(enabled = true) {
  return useQuery({
    queryKey: importKeys.entities(),
    queryFn: () => importApi.listEntities(),
    enabled,
    staleTime: 5 * 60 * 1000,
  });
}

/** 上传 + 解析 + 全量预校验。 */
export function useParseImport() {
  return useMutation({
    mutationFn: ({ entityCode, file }: { entityCode: string; file: File }) =>
      importApi.parse(entityCode, file),
  });
}

/** 重取预览（切换筛选）。 */
export function useSessionPreview(sessionId: string | null, filter: ImportPreviewFilter) {
  return useQuery({
    queryKey: [...importKeys.all, 'session', sessionId ?? '__none__', filter] as const,
    queryFn: () => importApi.getSession(sessionId!, filter),
    enabled: !!sessionId,
  });
}

/** 确认导入 → 返回 taskId。 */
export function useConfirmImport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) => importApi.confirm(sessionId),
    // 提交即失效任务列表，新任务（运行中）立刻出现在「导入导出」列表，不等终态（T12-04）
    onSuccess: () => void qc.invalidateQueries({ queryKey: taskKeys.all }),
  });
}
