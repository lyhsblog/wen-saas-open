/**
 * 前端跨端枚举家 barrel。
 *
 * 跨端复用的枚举展示元数据集中于此目录（按域一文件、各自独立定义、互不引用）；
 * 单端私有枚举留在各 app 本地。新增一类枚举：建 `enums/<域>.ts` 并在此 re-export。
 */
export {
  type Tone,
  type ToneLabel,
  type SelectOption,
  metaToOptions,
  labelMapToOptions,
} from './common';
export {
  TICKET_TYPE_LABEL,
  TICKET_PRIORITY_META,
  TICKET_STATUS_META,
  TICKET_SLA_META,
  type TicketPriorityMeta,
} from './ticket';
export {
  type TenantDataScope,
  TENANT_DATA_SCOPE_LABEL,
  type RecommendedDataScope,
  RECOMMENDED_DATA_SCOPE_LABEL,
} from './dataScope';
