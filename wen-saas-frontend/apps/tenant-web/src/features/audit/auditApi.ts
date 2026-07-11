import { http } from '../../lib/http';

/** 一条操作审计记录（雪花 id 序列化为字符串，前端按 string 处理）。 */
export type OperationLogItem = {
  id: string;
  requestId: string | null;
  operatorId: string;
  operatorName: string | null;
  module: string | null;
  action: string | null;
  objectType: string | null;
  objectId: string | null;
  objectDesc: string | null;
  method: string | null;
  path: string | null;
  statusCode: number | null;
  durationMs: number | null;
  ip: string | null;
  createdAt: string;
};

export type OperationLogPage = { records: OperationLogItem[]; total: number };

/** ③ 单字段变更（已成句）。 */
export type FieldChange = {
  column: string;
  label: string;
  before: string | null;
  after: string | null;
};
/** ③ 一行数据的逐字段改前/改后。 */
export type DataChange = { tableName: string; op: string; rowPk: string; fields: FieldChange[] };

export type OperationLogDetail = OperationLogItem & {
  errorMessage: string | null;
  userAgent: string | null;
  dataChanges: DataChange[];
};

export type LogQuery = {
  page?: number;
  size?: number;
  /** ISO-8601 起始时间（含）。 */
  startAt?: string | undefined;
  /** ISO-8601 截止时间（不含）。 */
  endAt?: string | undefined;
  /** 操作人 id（雪花字符串）。 */
  operatorId?: string | undefined;
  action?: string | undefined;
  /** 对象类型（实体 code，如 sys.user；精确匹配 object_type 列）。 */
  objectType?: string | undefined;
  /** 对象 id（精确，命中 object_id / object_refs）。 */
  objectId?: string | undefined;
};

/** 审计来源：全租户审计日志（管理员，audit.view）/ 我的操作记录（成员看自己）。 */
export type AuditScope = 'tenant' | 'mine';

function basePath(scope: AuditScope): string {
  return scope === 'tenant' ? '/api/v1/base/audit-logs' : '/api/v1/me/operation-logs';
}

export const auditApi = {
  async list(scope: AuditScope, q: LogQuery): Promise<OperationLogPage> {
    const res = await http.get<OperationLogPage>(basePath(scope), {
      params: {
        page: q.page ?? 1,
        size: q.size ?? 20,
        startAt: q.startAt,
        endAt: q.endAt,
        operatorId: q.operatorId,
        action: q.action,
        objectType: q.objectType,
        objectId: q.objectId,
      },
    });
    return res.data;
  },
  async detail(scope: AuditScope, id: string, createdAt: string): Promise<OperationLogDetail> {
    const res = await http.get<OperationLogDetail>(`${basePath(scope)}/${id}`, {
      params: { createdAt },
    });
    return res.data;
  },
};
