import { useQuery } from '@tanstack/react-query';
import { http } from '../../lib/http';

/** 可搜字段匹配模式。 */
export type MatchMode = 'PREFIX' | 'EXACT';

/** 一个可搜字段元数据：中文 label + 匹配模式（驱动提示文案 / 列）。 */
export type FieldMeta = {
  label: string;
  match: MatchMode;
};

/** 可搜索实体类型元数据（喂「对象类型」下拉 + 对象选择弹窗左侧类型列表）。fields 有序，首个为主显。 */
export type EntityTypeMeta = {
  code: string;
  label: string;
  fields: FieldMeta[];
};

/** 一条候选实例（雪花 id 为字符串）。primary=主显、secondary=淡色辅助。 */
export type EntityHit = {
  id: string;
  display: string;
  primary: string | null;
  secondary: string | null;
};

const BASE = '/api/v1/base/entity-lookup';

export const entityLookupApi = {
  async types(): Promise<EntityTypeMeta[]> {
    const res = await http.get<EntityTypeMeta[]>(`${BASE}/types`);
    return res.data;
  },
  async search(type: string, keyword?: string): Promise<EntityHit[]> {
    const res = await http.get<EntityHit[]>(`${BASE}/${encodeURIComponent(type)}/search`, {
      params: { keyword: keyword || undefined },
    });
    return res.data;
  },
};

/** 某类型的可搜字段标签拼成提示文案，如「输入名称/编码搜索」。无 fields 回退「输入关键字搜索」。 */
export function searchPlaceholder(fields: FieldMeta[] | undefined): string {
  if (!fields || fields.length === 0) return '输入关键字搜索';
  return `输入${fields.map((f) => f.label).join('/')}搜索`;
}

/** 可搜索类型列表；审计筛选区与对象选择弹窗共用。 */
export function useEntityTypes(enabled = true) {
  return useQuery({
    queryKey: ['audit', 'entity-types'],
    queryFn: () => entityLookupApi.types(),
    enabled,
    staleTime: 5 * 60 * 1000,
  });
}
