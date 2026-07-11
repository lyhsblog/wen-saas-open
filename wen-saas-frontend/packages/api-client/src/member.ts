import type { HttpClient } from './http';
import { toWirePageParams } from './pagination';

// 雪花 id 全程 string（后端 Long 序列化为带引号字符串，转 number 会丢精度）。

export interface DeptBinding {
  deptId: string;
  deptName: string | null;
}

export interface RoleBrief {
  roleId: string;
  code: string;
  name: string;
}

export interface MemberListItem {
  id: string;
  username: string;
  realName: string | null;
  mobile: string | null;
  email: string | null;
  mainDeptId: string;
  mainDeptName: string | null;
  secondaryDeptCount: number;
  status: number; // 0 停用 / 1 在职
  owner: boolean;
  roleIds: string[];
  roleNames: string[];
  /** 岗位字典项 code 快照（可空）。 */
  positionCode: string | null;
  /** 岗位展示名（字典 POSITION，删项后回退 code 文本快照）。 */
  positionName: string | null;
  /** 账号是否处于登录失败临时锁定中。 */
  loginLocked: boolean;
  lastLoginAt: string | null;
  createdAt: string | null;
  /** 该成员当前担任负责人的部门数（停用后仍保留，>0 时前端高亮提醒） */
  leaderDeptCount: number;
}

export interface MemberPage {
  items: MemberListItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface MemberDetail {
  id: string;
  username: string;
  realName: string | null;
  mobile: string | null;
  email: string | null;
  gender: number | null;
  birthday: string | null;
  empNo: string | null;
  /** 岗位字典项 code 快照（可空）。 */
  positionCode: string | null;
  /** 岗位展示名（字典 POSITION，删项后回退 code 文本快照）。 */
  positionName: string | null;
  status: number;
  owner: boolean;
  /** 账号是否处于登录失败临时锁定中。 */
  loginLocked: boolean;
  /** 锁定剩余秒数（未锁定为 0）。 */
  lockRemainingSeconds: number;
  mainDept: DeptBinding | null;
  secondaryDepts: DeptBinding[];
  roleIds: string[];
  roles: RoleBrief[];
  lastLoginAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  /** 该成员当前担任负责人的部门（停用后保留，UI 高亮提醒主管理员手动改派） */
  leaderDepts: DeptBinding[];
}

export interface MemberSearchItem {
  id: string;
  username: string;
  realName: string | null;
  mainDeptName: string | null;
}

/** 解密后的完整联系方式（明文）。列表/详情默认脱敏，明文仅经此审计端点获取。 */
export interface ContactReveal {
  mobile: string | null;
  email: string | null;
}

export interface CreatedMember {
  id: string;
  username: string;
  activationToken: string;
  activationExpiresAt: string;
}

export interface IssuedActivation {
  activationToken: string;
  activationExpiresAt: string;
  username: string;
}

export interface MemberListQuery {
  deptId?: string;
  includeSubDepts?: boolean;
  keyword?: string;
  status?: number;
  roleId?: string;
  page?: number;
  pageSize?: number;
}

export interface MemberCreateRequest {
  username: string;
  realName: string;
  mobile?: string;
  email?: string;
  mainDeptId: string;
  secondaryDeptIds?: string[];
  roleIds?: string[];
  /** 岗位字典项 code（可选）。 */
  positionCode?: string | null;
  remark?: string;
}

export interface MemberUpdateRequest {
  realName?: string;
  mobile?: string;
  email?: string;
  gender?: number;
  birthday?: string;
  empNo?: string;
  /** 岗位字典项 code；空串清空岗位。 */
  positionCode?: string | null;
  roleIds?: string[];
}

/** 岗位选项（字典 POSITION）。 */
export interface PositionOption {
  code: string;
  name: string;
}

export interface MemberDeptsRequest {
  mainDeptId: string;
  secondaryDeptIds?: string[];
}

/** 成员的第三方账号绑定（详情「登录信息」Tab）。 */
export interface MemberOAuthBinding {
  /** dingtalk / wechat / wecom */
  provider: string;
  nickname: string | null;
  avatarUrl: string | null;
  boundAt: string;
}

/** 一条登录记录（sys_login_log 精简投影；详情「登录信息」Tab）。 */
export interface MemberLoginLog {
  /** password / sms / wechat / wecom / dingtalk */
  loginMethod: string | null;
  /** login / logout / session_expired */
  action: string | null;
  /** success / fail */
  result: string | null;
  /** bad_credentials / disabled / locked / oauth_failed / other（成功为 null） */
  failReason: string | null;
  ip: string | null;
  userAgent: string | null;
  createdAt: string;
}

export function memberAdminApi(http: HttpClient) {
  const base = '/api/v1/base/members';
  return {
    async list(query: MemberListQuery = {}): Promise<MemberPage> {
      const res = await http.get<MemberPage>(base, { params: toWirePageParams(query) });
      return res.data;
    },
    async search(keyword: string, limit = 20): Promise<MemberSearchItem[]> {
      const res = await http.get<MemberSearchItem[]>(`${base}/search`, {
        params: { keyword, limit },
      });
      return res.data;
    },
    async detail(userId: string): Promise<MemberDetail> {
      const res = await http.get<MemberDetail>(`${base}/${encodeURIComponent(userId)}`);
      return res.data;
    },
    /** 解密查看完整联系方式（需 org.member.contact.reveal 权限，后端记审计）。 */
    async revealContact(userId: string): Promise<ContactReveal> {
      const res = await http.get<ContactReveal>(`${base}/${encodeURIComponent(userId)}/contact`);
      return res.data;
    },
    async create(body: MemberCreateRequest): Promise<CreatedMember> {
      const res = await http.post<CreatedMember>(base, body);
      return res.data;
    },
    async update(userId: string, body: MemberUpdateRequest): Promise<MemberDetail> {
      const res = await http.put<MemberDetail>(`${base}/${encodeURIComponent(userId)}`, body);
      return res.data;
    },
    async assignRoles(userId: string, roleIds: string[]): Promise<MemberDetail> {
      const res = await http.put<MemberDetail>(`${base}/${encodeURIComponent(userId)}/roles`, {
        roleIds,
      });
      return res.data;
    },
    async manageDepts(userId: string, body: MemberDeptsRequest): Promise<MemberDetail> {
      const res = await http.put<MemberDetail>(`${base}/${encodeURIComponent(userId)}/depts`, body);
      return res.data;
    },
    async setStatus(userId: string, enabled: boolean): Promise<void> {
      await http.put(`${base}/${encodeURIComponent(userId)}/status`, null, { params: { enabled } });
    },
    async reissueActivation(userId: string): Promise<IssuedActivation> {
      const res = await http.post<IssuedActivation>(
        `${base}/${encodeURIComponent(userId)}/reset-link`,
        null,
      );
      return res.data;
    },
    async unlockLogin(userId: string): Promise<void> {
      await http.delete(`${base}/${encodeURIComponent(userId)}/login-lock`);
    },
    /** 成员第三方账号绑定列表（org.member.list 鉴权）。 */
    async oauthBindings(userId: string): Promise<MemberOAuthBinding[]> {
      const res = await http.get<MemberOAuthBinding[]>(
        `${base}/${encodeURIComponent(userId)}/oauth-bindings`,
      );
      return res.data;
    },
    /** 成员最近登录记录（近 180 天倒序，limit≤100；org.member.list 鉴权）。 */
    async loginLogs(userId: string, limit = 20): Promise<MemberLoginLog[]> {
      const res = await http.get<MemberLoginLog[]>(
        `${base}/${encodeURIComponent(userId)}/login-logs`,
        { params: { limit } },
      );
      return res.data;
    },
    /** 岗位下拉项（字典 POSITION，org.member.list 鉴权）。 */
    async positions(): Promise<PositionOption[]> {
      const res = await http.get<PositionOption[]>(`${base}/positions`);
      return res.data;
    },
  };
}
