import type { HttpClient } from './http';

/**
 * 运营端「系统管理 · 团队成员」admin CRUD。对应 boot `/api/v1/ops/team/members/**`（8 endpoint）。
 *
 * 约束（服务端兜底）：不可停用自己；重置密码为 hard-reset，会下线该用户全部会话。
 * 雪花 Long（id / roleIds）JSON 序列化为字符串，前端全程 string 以避免 JS 精度丢失。
 */

/** 成员状态：0=停用 / 1=启用。 */
export type OpsMemberStatus = 0 | 1;

export type ListOpsMembersQuery = {
  keyword?: string;
  /** 0=停用 / 1=启用；不传 = 全部。 */
  status?: OpsMemberStatus;
};

/** 列表项（无分页，全量返回）。 */
export type OpsMemberListItem = {
  /** 雪花 Long，JSON 为字符串。 */
  id: string;
  username: string;
  realName: string;
  email: string | null;
  mobile: string | null;
  status: OpsMemberStatus;
  roleCodes: string[];
  lastLoginAt: string | null;
  createdAt: string;
};

/** 详情：含 gender/birthday/empNo + roleIds（用于编辑表单回填角色）。 */
export type OpsMemberDetail = {
  /** 雪花 Long，JSON 为字符串。 */
  id: string;
  username: string;
  realName: string;
  email: string | null;
  mobile: string | null;
  /** 0=未知 / 1=男 / 2=女。 */
  gender: number | null;
  birthday: string | null;
  empNo: string | null;
  status: OpsMemberStatus;
  /** 雪花 Long 数组，JSON 为字符串。 */
  roleIds: string[];
  roleCodes: string[];
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type OpsMemberCreateRequest = {
  /** 4-16 位，字母开头。 */
  username: string;
  /** ≥8 位，3 类字符。 */
  password: string;
  realName: string;
  email: string;
  mobile: string;
  /** 雪花 Long 数组；null = 无角色。 */
  roleIds?: string[] | null;
};

/** 编辑：各字段 null/省略 = 不改；roleIds 非 null 时全量替换。username/密码不可改。 */
export type OpsMemberUpdateRequest = {
  realName?: string | null;
  email?: string | null;
  mobile?: string | null;
  gender?: number | null;
  birthday?: string | null;
  empNo?: string | null;
  roleIds?: string[] | null;
};

export type OpsMemberResetPasswordRequest = {
  /** ≥8 位，3 类字符。 */
  newPassword: string;
};

/** 解密后的完整联系方式（明文）。列表/详情默认脱敏，明文仅经此审计端点获取。 */
export type OpsMemberContactReveal = {
  mobile: string | null;
  email: string | null;
};

/** 成员的活跃第三方账号绑定（运营成员常态仅企业 SSO，多为空）。 */
export type OpsMemberOAuthBinding = {
  provider: string;
  nickname: string | null;
  avatarUrl: string | null;
  /** ISO-8601。 */
  boundAt: string;
};

export function opsTeamMemberApi(http: HttpClient) {
  const base = '/api/v1/ops/team/members';
  return {
    async listMembers(query?: ListOpsMembersQuery): Promise<OpsMemberListItem[]> {
      const params = {
        ...(query?.keyword ? { keyword: query.keyword } : {}),
        ...(query?.status != null ? { status: query.status } : {}),
      };
      const res = await http.get<OpsMemberListItem[]>(base, { params });
      return res.data;
    },
    async getMember(userId: string): Promise<OpsMemberDetail> {
      const res = await http.get<OpsMemberDetail>(`${base}/${userId}`);
      return res.data;
    },
    /** 解密查看完整联系方式（需 ops.team.member.contact.reveal 权限，后端记审计）。 */
    async revealContact(userId: string): Promise<OpsMemberContactReveal> {
      const res = await http.get<OpsMemberContactReveal>(`${base}/${userId}/contact`);
      return res.data;
    },
    async createMember(body: OpsMemberCreateRequest): Promise<OpsMemberDetail> {
      const res = await http.post<OpsMemberDetail>(base, body);
      return res.data;
    },
    async updateMember(userId: string, body: OpsMemberUpdateRequest): Promise<OpsMemberDetail> {
      const res = await http.put<OpsMemberDetail>(`${base}/${userId}`, body);
      return res.data;
    },
    /** 停用（不可停用自己，服务端兜底 4xx）。 */
    async disableMember(userId: string): Promise<void> {
      await http.post(`${base}/${userId}/disable`, null);
    },
    async enableMember(userId: string): Promise<void> {
      await http.post(`${base}/${userId}/enable`, null);
    },
    /** 重置密码（hard-reset，会下线该用户全部会话）。 */
    async resetPassword(userId: string, body: OpsMemberResetPasswordRequest): Promise<void> {
      await http.post(`${base}/${userId}/reset-password`, body);
    },
    /** 成员第三方账号绑定列表（解绑前查看；运营成员常态为空）。 */
    async listOauthBindings(userId: string): Promise<OpsMemberOAuthBinding[]> {
      const res = await http.get<OpsMemberOAuthBinding[]>(`${base}/${userId}/oauth-bindings`);
      return res.data;
    },
    /** 解绑 OAuth。 */
    async unbindOauth(userId: string, provider: string): Promise<void> {
      await http.delete(`${base}/${userId}/oauth-bindings/${provider}`);
    },
  };
}
