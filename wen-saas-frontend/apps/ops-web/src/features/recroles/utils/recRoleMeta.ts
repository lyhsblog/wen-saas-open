import {
  RECOMMENDED_DATA_SCOPE_LABEL,
  type RecRoleDataScope,
  type RecRoleStatus,
} from '@wen-saas/api-client';
import type { StatusBadge } from '@wen-saas/shared-ui';

/** 推荐角色 3 态 → 状态徽章（复用租户管理的 StatusPill 配色语义）。 */
export const REC_ROLE_STATUS_BADGE: Record<RecRoleStatus, StatusBadge> = {
  DRAFT: { kind: 'neutral', text: '草稿', color: 'default' },
  PUBLISHED: { kind: 'success', text: '已发布', color: 'green' },
  INACTIVE: { kind: 'warning', text: '已下架', color: 'orange' },
};

const FALLBACK_BADGE: StatusBadge = { kind: 'neutral', text: '未知', color: 'default' };

/** 取状态徽章，未知状态兜底为「未知」neutral，避免渲染崩溃。 */
export function recRoleStatusBadge(status: string): StatusBadge {
  return REC_ROLE_STATUS_BADGE[status as RecRoleStatus] ?? FALLBACK_BADGE;
}

/** 列表排序权重：已发布 > 草稿 > 已下架（PRD §八 默认排序）。 */
export const REC_ROLE_STATUS_ORDER: Record<RecRoleStatus, number> = {
  PUBLISHED: 0,
  DRAFT: 1,
  INACTIVE: 2,
};

/** 状态筛选下拉项。 */
export const REC_ROLE_STATUS_FILTER_OPTIONS: { label: string; value: RecRoleStatus }[] = [
  { label: '已发布', value: 'PUBLISHED' },
  { label: '草稿', value: 'DRAFT' },
  { label: '已下架', value: 'INACTIVE' },
];

/** 默认数据范围（租户角色语义 1/2/3）→ 中文标签。 */
// 文案单源自 @wen-saas/api-client（数字版，与 tenant 推荐角色弹窗共用）。
export const DATA_SCOPE_LABEL: Record<RecRoleDataScope, string> = RECOMMENDED_DATA_SCOPE_LABEL;

/** 数据范围单选项（新建/编辑表单用）。 */
export const DATA_SCOPE_OPTIONS: { label: string; value: RecRoleDataScope }[] = (
  [1, 2, 3] as RecRoleDataScope[]
).map((value) => ({ value, label: RECOMMENDED_DATA_SCOPE_LABEL[value] }));

export function dataScopeLabel(scope: number): string {
  return DATA_SCOPE_LABEL[scope as RecRoleDataScope] ?? '—';
}
