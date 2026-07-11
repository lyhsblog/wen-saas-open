import type { HttpClient } from './http';

/**
 * 租户后台「备忘便利贴」。对应 boot `/api/v1/base/sticky-notes`。
 *
 * 个人自助，无权限门禁；主体取自登录态，每个用户只见/只改自己的便签。
 * 列表按 updatedAt 倒序：最近新建/编辑的排最前（会话内不即时冒顶由前端冻结顺序实现）。
 * 雪花 Long（id）JSON 为字符串，类型用 string（转 number 会丢精度）。
 */

/** 颜色 key，对应前端小色板。 */
export type StickyNoteColor = 'yellow' | 'green' | 'blue' | 'pink' | 'purple';

/** 单条便签。 */
export interface StickyNote {
  id: string;
  content: string;
  color: string;
  createdAt: string;
  updatedAt: string;
}

export function tenantStickyNoteApi(http: HttpClient) {
  const base = '/api/v1/base/sticky-notes';
  return {
    /** 当前用户全部便签，按 updatedAt 倒序。 */
    async list(): Promise<StickyNote[]> {
      const res = await http.get<StickyNote[]>(base);
      return res.data;
    },
    /** 新建便签（updatedAt=now，排最前），返回创建后的便签。 */
    async create(content: string, color: string): Promise<StickyNote> {
      const res = await http.post<StickyNote>(base, { content, color });
      return res.data;
    },
    /** 编辑内容 / 颜色，返回更新后的便签。 */
    async update(id: string, content: string, color: string): Promise<StickyNote> {
      const res = await http.put<StickyNote>(`${base}/${id}`, { content, color });
      return res.data;
    },
    /** 删除便签。 */
    async remove(id: string): Promise<void> {
      await http.delete(`${base}/${id}`);
    },
  };
}
