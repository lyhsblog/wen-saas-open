import type { HttpClient } from './http';

// 雪花 id 用 string。

export interface RecommendedRoleItem {
  id: string;
  name: string;
  description: string | null;
  dataScope: number; // 1 全部 / 2 本部门+下属 / 3 仅本人
  packageCodes: string[];
  permissionCount: number;
  activated: boolean;
  activatedRoleId: string | null;
}

export interface PermissionLine {
  code: string;
  name: string;
  valid: boolean; // false = 当前未注册/未购模块权限点，启用时标灰
}

export interface ActivatePreview {
  templateId: string;
  name: string;
  dataScope: number;
  permissions: PermissionLine[];
}

export interface ActivateRequest {
  name?: string;
  dataScope?: number;
  permissionCodes?: string[];
}

export interface ActivateResult {
  roleId: string;
}

export function tenantRecommendedRoleApi(http: HttpClient) {
  const base = '/api/v1/base/recommended-roles';
  return {
    async listAvailable(): Promise<RecommendedRoleItem[]> {
      const res = await http.get<RecommendedRoleItem[]>(base);
      return res.data;
    },
    async preview(templateId: string): Promise<ActivatePreview> {
      const res = await http.get<ActivatePreview>(
        `${base}/${encodeURIComponent(templateId)}/preview`,
      );
      return res.data;
    },
    async activate(templateId: string, body: ActivateRequest = {}): Promise<ActivateResult> {
      const res = await http.post<ActivateResult>(
        `${base}/${encodeURIComponent(templateId)}/activate`,
        body,
      );
      return res.data;
    },
  };
}
