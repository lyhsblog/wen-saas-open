import type { HttpClient } from './http';

/**
 * 运营端「通知模板管理」admin CRUD。对应 boot `/api/v1/ops/notification-templates/**`
 * （8 endpoint）。
 *
 * 管理对象为**平台默认模板**（boot 侧 tenant_id=0），即业务模块声明 / 运营维护的默认版本。
 * 租户侧主管理员定制（tenant-web）不在此范围。
 *
 * sys.* 预置模板只读：不可改 / 停用 / 删除（boot 返回 422 SYS_TEMPLATE_PROTECTED）。
 */

/** 模板状态。boot 响应/查询参数统一大写（落库为小写 active/inactive）。 */
export type NotificationTemplateStatus = 'ACTIVE' | 'INACTIVE';

/** 优先级。 */
export type NotificationTemplatePriority = 'NORMAL' | 'IMPORTANT';

export type ListNotificationTemplatesQuery = {
  /** 状态过滤；不传 = 全部。 */
  status?: NotificationTemplateStatus;
  /** 业务模块编码过滤。 */
  moduleCode?: string;
  keyword?: string;
};

/** 单渠道文案。title 对邮件即主题；短信 / IM 可仅有 body。 */
export type ChannelContent = {
  title: string | null;
  body: string | null;
};

/** 可用变量声明（业务方声明，含示例值，供预览）。 */
export type NotificationVarDef = {
  name: string;
  sampleValue: string | null;
  required: boolean;
};

/** 渠道元数据。fallback=true 表示保底通道（站内信，不可禁用）。 */
export type ChannelMeta = {
  /** Channel 枚举名：STATION | EMAIL | SMS | WECHAT | WECOM | DINGTALK。 */
  channel: string;
  label: string;
  fallback: boolean;
};

/** 列表项（精简视图）。 */
export type NotificationTemplateListItem = {
  templateCode: string;
  moduleCode: string | null;
  name: string;
  description: string | null;
  status: NotificationTemplateStatus;
  priority: NotificationTemplatePriority;
  notificationCategory: string | null;
  defaultChannels: string[];
  /** true = sys.* 底座预置模板（只读）。 */
  preset: boolean;
  createdAt: string;
  updatedAt: string;
};

/** 详情。 */
export type NotificationTemplateDetail = {
  templateCode: string;
  moduleCode: string | null;
  name: string;
  description: string | null;
  availableVars: NotificationVarDef[];
  defaultChannels: string[];
  /** 各渠道文案，key 为 Channel 枚举名。 */
  contents: Record<string, ChannelContent>;
  notificationCategory: string | null;
  priority: NotificationTemplatePriority;
  status: NotificationTemplateStatus;
  preset: boolean;
  createdAt: string;
  updatedAt: string;
};

/**
 * 新建请求。templateCode `{module}.{name}`（小写，^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$），创建后不可改。
 * defaultChannels 服务端确保含 STATION 保底；启用渠道文案不能为空。
 */
export type NotificationTemplateCreateRequest = {
  templateCode: string;
  moduleCode?: string | null;
  name: string;
  description?: string | null;
  availableVars?: NotificationVarDef[] | null;
  defaultChannels: string[];
  contents: Record<string, ChannelContent>;
  notificationCategory?: string | null;
  /** null 视作 NORMAL。 */
  priority?: NotificationTemplatePriority | null;
  /** null 视作 ACTIVE。 */
  status?: NotificationTemplateStatus | null;
};

/** 编辑：各字段 null/省略 = 不改；defaultChannels/contents/availableVars 非 null 时全量替换。 */
export type NotificationTemplateUpdateRequest = {
  name?: string | null;
  description?: string | null;
  availableVars?: NotificationVarDef[] | null;
  defaultChannels?: string[] | null;
  contents?: Record<string, ChannelContent> | null;
  notificationCategory?: string | null;
  priority?: NotificationTemplatePriority | null;
  status?: NotificationTemplateStatus | null;
};

export function opsNotificationTemplateApi(http: HttpClient) {
  const base = '/api/v1/ops/notification-templates';
  return {
    async listTemplates(
      query?: ListNotificationTemplatesQuery,
    ): Promise<NotificationTemplateListItem[]> {
      const params = {
        ...(query?.status ? { status: query.status } : {}),
        ...(query?.moduleCode ? { moduleCode: query.moduleCode } : {}),
        ...(query?.keyword ? { keyword: query.keyword } : {}),
      };
      const res = await http.get<NotificationTemplateListItem[]>(base, { params });
      return res.data;
    },
    /** 渠道元数据（含保底标记），供渲染渠道开关 / 文案 Tab。 */
    async listChannels(): Promise<ChannelMeta[]> {
      const res = await http.get<ChannelMeta[]>(`${base}/channels`);
      return res.data;
    },
    async getTemplate(code: string): Promise<NotificationTemplateDetail> {
      const res = await http.get<NotificationTemplateDetail>(`${base}/${encodeURIComponent(code)}`);
      return res.data;
    },
    async createTemplate(
      body: NotificationTemplateCreateRequest,
    ): Promise<NotificationTemplateDetail> {
      const res = await http.post<NotificationTemplateDetail>(base, body);
      return res.data;
    },
    async updateTemplate(
      code: string,
      body: NotificationTemplateUpdateRequest,
    ): Promise<NotificationTemplateDetail> {
      const res = await http.put<NotificationTemplateDetail>(
        `${base}/${encodeURIComponent(code)}`,
        body,
      );
      return res.data;
    },
    async enable(code: string): Promise<void> {
      await http.put(`${base}/${encodeURIComponent(code)}/enable`, null);
    },
    async disable(code: string): Promise<void> {
      await http.put(`${base}/${encodeURIComponent(code)}/disable`, null);
    },
    async deleteTemplate(code: string): Promise<void> {
      await http.delete(`${base}/${encodeURIComponent(code)}`);
    },
  };
}
