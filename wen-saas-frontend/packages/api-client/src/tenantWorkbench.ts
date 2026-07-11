import type { HttpClient } from './http';

/**
 * 租户后台「工作台首页」。对应 boot `/api/v1/base/workbench/**`。
 *
 * 自助首页，无权限门禁；套餐到期块仅主管理员可见（服务端按 tenantOwner 收敛，非主管理员为 null）。
 * 雪花 Long（消息 id）JSON 为字符串，类型用 string；计数为普通 long，用 number。
 */

/** 顶部欢迎语。 */
export type TenantWorkbenchGreeting = {
  userName: string;
  roleName: string;
};

/** 套餐到期提醒；severity: red(≤3 天) / yellow(≤7 天) / none。 */
export type TenantWorkbenchPlanExpiry = {
  planName: string;
  expireAt: string | null;
  daysLeft: number;
  severity: 'red' | 'yellow' | 'none';
};

/** 待办分项。 */
export type TenantWorkbenchTodoEntry = {
  label: string;
  count: number;
  actionUrl: string | null;
};

/** 我的待办卡片。 */
export type TenantWorkbenchTodoCard = {
  total: number;
  items: TenantWorkbenchTodoEntry[];
};

/** 消息摘要项。 */
export type TenantWorkbenchMessageBrief = {
  id: string | null;
  title: string | null;
  category: string | null;
  time: string | null;
};

/** 我的消息卡片。 */
export type TenantWorkbenchMessageCard = {
  unreadTotal: number;
  recent: TenantWorkbenchMessageBrief[];
};

/** 系统公告摘要。 */
export type TenantWorkbenchAnnouncementBrief = {
  title: string | null;
  from: string | null;
  time: string | null;
};

/** 系统公告卡片。 */
export type TenantWorkbenchAnnouncementCard = {
  total: number;
  latest: TenantWorkbenchAnnouncementBrief | null;
};

/** 工作台 overview：顶部信息条 + 固定三卡。 */
export type TenantWorkbenchOverview = {
  greeting: TenantWorkbenchGreeting;
  /** 仅主管理员可见，否则为 null。 */
  planExpiry: TenantWorkbenchPlanExpiry | null;
  todo: TenantWorkbenchTodoCard;
  message: TenantWorkbenchMessageCard;
  announcement: TenantWorkbenchAnnouncementCard;
};

/** 业务数据卡片展示形式。 */
export type TenantWorkbenchCardDisplay = 'NUMBER' | 'NUMBER_TREND' | 'LIST' | 'CHART';
/** 业务数据卡片占位宽度。 */
export type TenantWorkbenchCardSpan = 'THIRD' | 'HALF' | 'FULL';

/** 业务数据卡片分项（LIST / 分布用）。 */
export type TenantWorkbenchCardItem = {
  label: string;
  value: string;
  tone: string | null;
};

/** 业务数据卡片运行时数据（字段按 display 形态取用，全空视为空态）。 */
export type TenantWorkbenchCardData = {
  value: string | null;
  unit: string | null;
  delta: string | null;
  deltaTone: string | null;
  trend: number[] | null;
  items: TenantWorkbenchCardItem[] | null;
};

/** 业务数据卡片（定义元信息 + 数据）。 */
export type TenantWorkbenchBusinessCard = {
  code: string;
  moduleCode: string | null;
  name: string;
  display: TenantWorkbenchCardDisplay;
  span: TenantWorkbenchCardSpan;
  actionUrl: string | null;
  sortWeight: number;
  showOnH5: boolean;
  /** provider 异常或无数据时为 null（空态）。 */
  data: TenantWorkbenchCardData | null;
};

export function tenantWorkbenchApi(http: HttpClient) {
  const base = '/api/v1/base/workbench';
  return {
    /** 顶部信息条 + 套餐到期 + 待办/消息/公告 固定三卡。 */
    async overview(): Promise<TenantWorkbenchOverview> {
      const res = await http.get<TenantWorkbenchOverview>(`${base}/overview`);
      return res.data;
    },
    /** 业务数据卡片区（各业务模块贡献，按可见角色过滤 + 排序 + 实时求值）。 */
    async cards(): Promise<TenantWorkbenchBusinessCard[]> {
      const res = await http.get<TenantWorkbenchBusinessCard[]>(`${base}/cards`);
      return res.data;
    },
  };
}
