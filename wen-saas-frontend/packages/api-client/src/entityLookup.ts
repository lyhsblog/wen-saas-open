import type { HttpClient } from './http';

/** 可搜字段匹配模式。 */
export type MatchMode = 'PREFIX' | 'EXACT';

/** 一个可搜字段的元数据：中文 label + 匹配模式（驱动前端提示文案 / 列）。 */
export interface FieldMeta {
  label: string;
  match: MatchMode;
}

/** 可搜索实体类型元数据。fields 有序，第一个为主显字段。 */
export interface EntityTypeMeta {
  code: string;
  label: string;
  fields: FieldMeta[];
}

/** 一条候选实例。primary=主显、secondary=淡色辅助（无次字段时为 null）。 */
export interface NameHit {
  id: string;
  display: string;
  primary: string | null;
  secondary: string | null;
}

/**
 * 通用实体反向搜索。单关键字 OR 命中各可搜字段（名称类前缀、编码类精确）、≤20 条、
 * offset 滚动、无总数。登录即可调用，不挂业务权限门控（端点只回 id+展示名，不暴露业务字段）——供 ref
 * 选择器、审计「对象」筛选、业务页面任意「选人/选部门/选关联实例」类筛选或表单字段复用（如固定
 * type='sys.user' 搜成员，不要再调有权限门控的 `/api/v1/base/members/search`）。
 */
export function entityLookupApi(http: HttpClient) {
  const base = '/api/v1/base/entity-lookup';
  return {
    /** 可搜索类型清单（含每个类型的可搜字段，用于拼提示文案 / 列）。 */
    async types(): Promise<EntityTypeMeta[]> {
      const res = await http.get<EntityTypeMeta[]>(`${base}/types`);
      return res.data;
    },
    async search(type: string, keyword: string, offset = 0): Promise<NameHit[]> {
      const res = await http.get<NameHit[]>(`${base}/${encodeURIComponent(type)}/search`, {
        params: { keyword, offset },
      });
      return res.data;
    },
  };
}
