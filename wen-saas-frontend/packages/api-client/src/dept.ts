import type {
  DeptView,
  DeptCreateRequest,
  DeptUpdateRequest,
  DeptMoveRequest,
} from '@wen-saas/domain';
import type { HttpClient } from './http';

/** 部门负责人引用。 */
export interface DeptLeaderRef {
  userId: string;
  username: string;
  realName: string | null;
}

export function deptAdminApi(http: HttpClient) {
  const base = '/api/v1/base/depts';
  return {
    async listTree(): Promise<DeptView[]> {
      const res = await http.get<DeptView[]>(base);
      return res.data;
    },
    async getDept(deptId: string): Promise<DeptView> {
      const res = await http.get<DeptView>(`${base}/${encodeURIComponent(deptId)}`);
      return res.data;
    },
    async createDept(body: DeptCreateRequest): Promise<DeptView> {
      const res = await http.post<DeptView>(base, body);
      return res.data;
    },
    async updateDept(deptId: string, body: DeptUpdateRequest): Promise<DeptView> {
      const res = await http.put<DeptView>(`${base}/${encodeURIComponent(deptId)}`, body);
      return res.data;
    },
    async deleteDept(deptId: string): Promise<void> {
      await http.delete(`${base}/${encodeURIComponent(deptId)}`);
    },
    async moveDept(deptId: string, body: DeptMoveRequest): Promise<DeptView> {
      const res = await http.put<DeptView>(`${base}/${encodeURIComponent(deptId)}/parent`, body);
      return res.data;
    },
    /** 同级批量重排：orderedIds 为该父级下全部子部门按新顺序的 id（parentId 传 '0' 表示一级）。 */
    async reorderDepts(parentId: string, orderedIds: string[]): Promise<void> {
      await http.put(`${base}/reorder`, { parentId, orderedIds });
    },
    async setDeptStatus(deptId: string, enabled: boolean): Promise<DeptView> {
      const res = await http.put<DeptView>(`${base}/${encodeURIComponent(deptId)}/status`, null, {
        params: { enabled },
      });
      return res.data;
    },
    async listLeaders(deptId: string): Promise<DeptLeaderRef[]> {
      const res = await http.get<DeptLeaderRef[]>(`${base}/${encodeURIComponent(deptId)}/leaders`);
      return res.data;
    },
    async setLeaders(deptId: string, userIds: string[]): Promise<DeptLeaderRef[]> {
      const res = await http.put<DeptLeaderRef[]>(`${base}/${encodeURIComponent(deptId)}/leaders`, {
        userIds,
      });
      return res.data;
    },
  };
}
