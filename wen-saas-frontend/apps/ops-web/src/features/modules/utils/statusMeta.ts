import { formatDateTime } from '@wen-saas/shared-ui';

// 套餐状态元的单一真源在 packages/utils/statusMeta.ts（packageStatusBadge，配 StatusPill）；
// 此处不再另立一份，避免同一枚举两处漂移。

export function fmtDateTime(iso: string | null): string {
  return formatDateTime(iso);
}
