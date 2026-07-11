import type { HttpClient } from './http';

/**
 * 租户后台「系统管理 · 通知模板」。对应 boot `/api/v1/base/notification-templates`。
 *
 * 文案 / 触发场景 / 变量全部只读（恒用平台默认版本，不提供文案编辑）；主管理员只能定制三项：
 * 启停 / 渠道 / 优先级。停用后该模板对本租户完全不发（含站内信兜底）。
 */

export type NotifyTemplatePriority = 'NORMAL' | 'IMPORTANT';

export type NotifyChannelMeta = {
  channel: string;
  label: string;
  /** true=站内信保底通道，不可取消。 */
  fallback: boolean;
};

export type NotifyVarDef = {
  name: string;
  sampleValue: string | null;
  required: boolean;
};

export type NotifyChannelContent = {
  title: string | null;
  body: string | null;
};

export type NotifyTemplateListItem = {
  templateCode: string;
  moduleCode: string | null;
  name: string;
  description: string | null;
  /** sys.* 底座预置模板（不可停用）。 */
  preset: boolean;
  /** 平台默认是否启用（运营全局停用时为 false，租户无法启用）。 */
  defaultActive: boolean;
  /** 本租户生效：是否启用。 */
  enabled: boolean;
  /** 本租户生效通道。 */
  channels: string[];
  priority: NotifyTemplatePriority;
  /** 是否存在本租户定制。 */
  customized: boolean;
  updatedAt: string | null;
};

export type NotifyTemplateDetail = {
  templateCode: string;
  moduleCode: string | null;
  name: string;
  description: string | null;
  preset: boolean;
  availableVars: NotifyVarDef[];
  /** 只读平台默认文案（按渠道）。 */
  contents: Record<string, NotifyChannelContent>;
  /** 平台默认通道。 */
  defaultChannels: string[];
  defaultPriority: NotifyTemplatePriority;
  defaultActive: boolean;
  /** 本租户生效设定。 */
  enabled: boolean;
  channels: string[];
  priority: NotifyTemplatePriority;
  customized: boolean;
  updatedAt: string | null;
};

export type UpdateNotifyPrefRequest = {
  /** null/undefined 视作 true。 */
  enabled?: boolean | null;
  /** null=继承默认；非空=覆盖（自动含 STATION）。 */
  channels?: string[] | null;
  /** null=继承默认。 */
  priority?: NotifyTemplatePriority | null;
};

export function tenantNotificationTemplateApi(http: HttpClient) {
  const base = '/api/v1/base/notification-templates';
  return {
    async list(params?: {
      moduleCode?: string;
      keyword?: string;
    }): Promise<NotifyTemplateListItem[]> {
      const res = await http.get<NotifyTemplateListItem[]>(base, { params });
      return res.data;
    },
    async channels(): Promise<NotifyChannelMeta[]> {
      const res = await http.get<NotifyChannelMeta[]>(`${base}/channels`);
      return res.data;
    },
    async get(code: string): Promise<NotifyTemplateDetail> {
      const res = await http.get<NotifyTemplateDetail>(`${base}/${encodeURIComponent(code)}`);
      return res.data;
    },
    /** 更新偏好（目标态全量覆盖；与默认一致时后端自动清除定制）。sys.* 停用返回 422。 */
    async updatePref(code: string, body: UpdateNotifyPrefRequest): Promise<NotifyTemplateDetail> {
      const res = await http.put<NotifyTemplateDetail>(`${base}/${encodeURIComponent(code)}`, body);
      return res.data;
    },
    /** 恢复默认（清除本租户定制）。 */
    async reset(code: string): Promise<NotifyTemplateDetail> {
      const res = await http.post<NotifyTemplateDetail>(
        `${base}/${encodeURIComponent(code)}/reset`,
      );
      return res.data;
    },
  };
}
