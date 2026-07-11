import { toneBadge, type StatusBadge } from '@wen-saas/shared-ui';
import type { OpsMemberStatus, OpsRoleDataScope, SettingCategory } from '@wen-saas/api-client';

/** 成员状态筛选选项。 */
export const MEMBER_STATUS_OPTIONS = [
  { label: '在职', value: 1 as OpsMemberStatus },
  { label: '停用', value: 0 as OpsMemberStatus },
];

/** 成员状态 → StatusPill badge（停用是可逆中性态，用 neutral）。 */
export function memberStatusBadge(status: OpsMemberStatus): StatusBadge {
  return status === 1 ? toneBadge('success', '在职') : toneBadge('neutral', '停用');
}

/** 运营域数据范围（只有 ALL / OWNED；CSM/SUPPORT 仅看自己负责的租户）。 */
export const DATA_SCOPE_OPTIONS: { label: string; value: OpsRoleDataScope }[] = [
  { label: '全部数据', value: 'ALL' },
  { label: '仅本人负责', value: 'OWNED' },
];

export function dataScopeLabel(scope: OpsRoleDataScope): string {
  return scope === 'ALL' ? '全部数据' : '仅本人负责';
}

/** 参数分组元信息（展示顺序 + 标题 + 说明）。 */
export const SETTING_CATEGORY_META: Record<SettingCategory, { title: string; hint: string }> = {
  platform_info: { title: '平台运营信息', hint: '展示在租户的「套餐与账号」页底部' },
  tenant_default: { title: '租户开通默认值', hint: '新开通租户的默认配额 / 时效' },
  security_policy: { title: '安全策略', hint: '修改后经广播即时对所有租户生效' },
};

/** 参数分组展示顺序。 */
export const SETTING_CATEGORY_ORDER: SettingCategory[] = [
  'platform_info',
  'tenant_default',
  'security_policy',
];

/**
 * 操作日志「状态」展示：HTTP 状态码 → 业务文案 badge。运营视角只关心成功/失败与失败大类，
 * 原始数字码收进详情抽屉。null（无记录）返回 null，由调用方渲染占位。
 */
export function opLogStatusBadge(code: number | null): StatusBadge | null {
  if (code == null) return null;
  if (code < 400) return toneBadge('success', '成功');
  if (code < 500) return toneBadge('warning', '失败(4xx)');
  return toneBadge('danger', '失败(5xx)');
}

/** 校验是否为雪花 id（纯数字字符串），用于 URL 参数兜底。 */
export function isSnowflakeId(raw: string | null): raw is string {
  return raw != null && /^\d+$/.test(raw);
}
