import type { DictGroup, DictItem, DeptView, RoleDetail, UserSummary } from '@wen-saas/domain';
import type {
  NotificationCategory,
  NotificationChannel,
  OpsLoginLogItem,
  OpsProfileView,
  OpsSessionItem,
} from '../opsProfile';

/**
 * mock 后端的内存状态。每次 worker 启动重置（页面刷新即新副本），
 * 不持久化到 localStorage——避免和真后端混。
 */

export const demoUser: UserSummary = {
  id: '100001',
  username: 'alice',
  tenantId: '9701',
};

export const validCredentials = { username: 'alice', password: 'P@ssw0rd' };

function seedGroups(): DictGroup[] {
  return [
    {
      code: 'PROJECT_STAGE',
      name: '项目阶段',
      description: '示例：项目阶段字典',
      source: 'CUSTOM',
      supportHierarchy: false,
    },
    {
      code: 'REGION',
      name: '行政区划',
      description: '内置 · 树形',
      source: 'SYSTEM',
      supportHierarchy: true,
    },
  ];
}

function seedItems(): Record<string, DictItem[]> {
  return {
    PROJECT_STAGE: [
      { id: 1001, code: 'PLAN', name: '规划', sortOrder: 1, status: 1, source: 'CUSTOM' },
      { id: 1002, code: 'BUILD', name: '建设', sortOrder: 2, status: 1, source: 'CUSTOM' },
      { id: 1003, code: 'CLOSED', name: '已结项', sortOrder: 3, status: 0, source: 'CUSTOM' },
    ],
    REGION: [
      {
        id: 2001,
        code: 'CN-31',
        name: '上海市',
        sortOrder: 1,
        status: 1,
        source: 'SYSTEM',
        children: [
          {
            id: 2002,
            code: 'CN-31-001',
            name: '黄浦区',
            sortOrder: 1,
            status: 1,
            source: 'SYSTEM',
          },
          {
            id: 2003,
            code: 'CN-31-002',
            name: '徐汇区',
            sortOrder: 2,
            status: 1,
            source: 'SYSTEM',
          },
        ],
      },
    ],
  };
}

const ALL_PERMISSIONS = [
  'base.dict.view',
  'base.dict.write',
  'base.role.view',
  'base.role.write',
  'base.role.assign',
  'base.member.view',
  'base.member.assign',
  'base.dept.view',
  'base.dept.write',
];

function seedRoles(): RoleDetail[] {
  return [
    {
      id: 1001,
      code: 'SYSTEM_OWNER',
      name: '主管理员',
      description: '拥有全部权限，不可修改',
      dataScope: 'ALL',
      status: 1,
      memberCount: 1,
      systemPreset: true,
      permissions: ALL_PERMISSIONS,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
    {
      id: 1002,
      code: 'SYSTEM_ADMIN',
      name: '管理员',
      description: '系统预置管理员角色，可管理字典与角色',
      dataScope: 'DEPT_AND_BELOW',
      status: 1,
      memberCount: 0,
      systemPreset: true,
      permissions: ['base.dict.view', 'base.dict.write', 'base.role.view'],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
    {
      id: 1003,
      code: 'SYSTEM_MEMBER',
      name: '普通成员',
      description: '系统预置成员角色，仅查看权限',
      dataScope: 'SELF',
      status: 1,
      memberCount: 3,
      systemPreset: true,
      permissions: ['base.dict.view'],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
    {
      id: 1004,
      code: 'SALES_MANAGER',
      name: '销售经理',
      description: '自定义角色示例',
      dataScope: 'DEPT_AND_BELOW',
      status: 1,
      memberCount: 0,
      systemPreset: false,
      permissions: ['base.dict.view', 'base.role.view'],
      createdAt: '2026-01-15T08:00:00Z',
      updatedAt: '2026-01-15T08:00:00Z',
    },
  ];
}

/**
 * 部门种子数据（扁平存储）。
 * parentId=0 表示一级部门；ancestors 为 materialized path "/root/parent/self/"。
 */
function seedDepts(): DeptView[] {
  return [
    {
      id: 1,
      parentId: 0,
      name: '示例科技有限公司',
      ancestors: '/1/',
      sortOrder: 0,
      status: 1,
      memberCount: 12,
      children: [],
    },
    {
      id: 10,
      parentId: 1,
      name: '研发部',
      ancestors: '/1/10/',
      sortOrder: 1,
      status: 1,
      memberCount: 6,
      children: [],
    },
    {
      id: 11,
      parentId: 10,
      name: '前端组',
      ancestors: '/1/10/11/',
      sortOrder: 1,
      status: 1,
      memberCount: 3,
      children: [],
    },
    {
      id: 12,
      parentId: 10,
      name: '后端组',
      ancestors: '/1/10/12/',
      sortOrder: 2,
      status: 1,
      memberCount: 3,
      children: [],
    },
    {
      id: 20,
      parentId: 1,
      name: '销售部',
      ancestors: '/1/20/',
      sortOrder: 2,
      status: 1,
      memberCount: 4,
      children: [],
    },
    {
      id: 30,
      parentId: 1,
      name: '行政部',
      ancestors: '/1/30/',
      sortOrder: 3,
      status: 1,
      memberCount: 2,
      children: [],
    },
  ];
}

function seedRoleMembers(): Record<string, number[]> {
  return {
    '1001': [100001],
    '1003': [100002, 100003, 100004],
  };
}

// ===================== 运营个人中心 =====================

/** 当前模拟登录的运营会话 ID，固定值便于 handler 标记 current。 */
export const MOCK_CURRENT_SESSION_ID = 'sess-current-001';
/** mock 敏感操作（改密码/改手机等）step-up 验证通过后的票据。 */
export const MOCK_STEP_UP_TOKEN = 'mock-stepup-token';

function seedOpsProfile(): OpsProfileView {
  return {
    basic: {
      userId: '200001',
      username: 'admin@ops',
      empNo: 'EMP-0001',
      email: 'li.zixuan@platform.com',
      realName: '李梓萱',
      mobile: '138****1234',
      gender: 2,
      birthday: '1992-03-14',
    },
    teamsRoles: {
      roles: [
        { id: 'r-1', code: 'SUPER_ADMIN', name: '超级管理员' },
        { id: 'r-2', code: 'CSM', name: '客户经理' },
      ],
      dataScope: '全部数据',
      accountStatus: '在职',
    },
    security: {
      sessionTimeoutSeconds: 3600,
      loginLockMaxAttempts: 5,
      loginLockWindowSeconds: 900,
      mfaRequired: false,
      mfaEnabled: true,
      mfaMethod: 'TOTP',
    },
    sso: {
      provider: '阿里云 IDaaS（占位）',
      enabled: false,
    },
  };
}

function seedOpsSessions(): OpsSessionItem[] {
  return [
    {
      sessionId: MOCK_CURRENT_SESSION_ID,
      loginAt: '2026-05-28T09:12:00+08:00',
      lastAccessAt: '2026-05-28T14:38:00+08:00',
      current: true,
    },
    {
      sessionId: 'sess-mac-002',
      loginAt: '2026-05-27T18:42:00+08:00',
      lastAccessAt: '2026-05-28T08:05:00+08:00',
      current: false,
    },
    {
      sessionId: 'sess-iphone-003',
      loginAt: '2026-05-26T20:01:00+08:00',
      lastAccessAt: '2026-05-27T21:33:00+08:00',
      current: false,
    },
  ];
}

function seedOpsLoginLogs(): OpsLoginLogItem[] {
  const base: OpsLoginLogItem[] = [];
  for (let i = 0; i < 23; i++) {
    const ok = i % 4 !== 0;
    const failReason = ok ? undefined : i === 0 ? '连续失败超过阈值，账号已锁定' : '密码错误';
    base.push({
      id: String(900000 + i),
      account: 'admin@ops',
      loginMethod: i % 5 === 0 ? 'SSO' : 'PASSWORD',
      action: 'LOGIN',
      result: ok ? 'SUCCESS' : i === 0 ? 'LOCKED' : 'FAIL',
      ...(failReason ? { failReason } : {}),
      ip: i % 2 === 0 ? '203.0.113.42' : '198.51.100.7',
      userAgent:
        i % 2 === 0
          ? 'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605 Safari/605'
          : 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124 Safari/537',
      createdAt: new Date(Date.UTC(2026, 4, 28 - Math.floor(i / 3), 9 - (i % 6))).toISOString(),
    });
  }
  return base;
}

function seedOpsNotifyPrefs(): Record<
  NotificationCategory,
  Partial<Record<NotificationChannel, boolean>>
> {
  // STATION 始终 true（前端 + 后端均强制）；EMAIL/SMS 按 PRD §3.6 示意默认勾选。
  return {
    TODO_TASK: { STATION: true, EMAIL: true, SMS: false },
    TICKET_ALERT: { STATION: true, EMAIL: true, SMS: true },
    CUSTOMER_HEALTH: { STATION: true, EMAIL: false, SMS: false },
    PACKAGE_EXPIRY: { STATION: true, EMAIL: true, SMS: false },
    SYSTEM_ANNOUNCEMENT: { STATION: true, EMAIL: true, SMS: false },
    SECURITY_ALERT: { STATION: true, EMAIL: true, SMS: true },
  };
}

export type MockState = {
  groups: DictGroup[];
  items: Record<string, DictItem[]>;
  nextItemId: number;
  depts: DeptView[];
  nextDeptId: number;
  roles: RoleDetail[];
  roleMembers: Record<string, number[]>;
  nextRoleId: number;
  opsProfile: OpsProfileView;
  opsSessions: OpsSessionItem[];
  opsLoginLogs: OpsLoginLogItem[];
  opsNotifyPrefs: Record<NotificationCategory, Partial<Record<NotificationChannel, boolean>>>;
};

function freshState(): MockState {
  return {
    groups: seedGroups(),
    items: seedItems(),
    nextItemId: 9000,
    depts: seedDepts(),
    nextDeptId: 1000,
    roles: seedRoles(),
    roleMembers: seedRoleMembers(),
    nextRoleId: 2000,
    opsProfile: seedOpsProfile(),
    opsSessions: seedOpsSessions(),
    opsLoginLogs: seedOpsLoginLogs(),
    opsNotifyPrefs: seedOpsNotifyPrefs(),
  };
}

export const state: MockState = freshState();

/** 测试 / 重新种子需要时显式调用。 */
export function resetMockState(): void {
  const s = freshState();
  state.groups = s.groups;
  state.items = s.items;
  state.nextItemId = s.nextItemId;
  state.depts = s.depts;
  state.nextDeptId = s.nextDeptId;
  state.roles = s.roles;
  state.roleMembers = s.roleMembers;
  state.nextRoleId = s.nextRoleId;
  state.opsProfile = s.opsProfile;
  state.opsSessions = s.opsSessions;
  state.opsLoginLogs = s.opsLoginLogs;
  state.opsNotifyPrefs = s.opsNotifyPrefs;
}
