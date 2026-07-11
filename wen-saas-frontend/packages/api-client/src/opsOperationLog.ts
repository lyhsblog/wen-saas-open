import type { HttpClient } from './http';

/**
 * 运营操作日志。对应 boot `/api/v1/ops/operation-logs/**`（2 endpoint）。
 *
 * 列表分页查询 + 单条详情。详情按 id+createdAt（分区键）取，列表项必带 createdAt 才能拉详情。
 * dataScope=OWNED 的角色仅看 operator_id=self 的日志（服务端过滤）。
 * 雪花 Long（id / operatorId / targetTenantId / total）JSON 为字符串。
 */

/** 列表查询条件（page 1-based，size≤200）。 */
export type ListOpLogsQuery = {
  page?: number | undefined;
  size?: number | undefined;
  /** ISO-8601，时间范围起。 */
  startAt?: string | undefined;
  /** ISO-8601，时间范围止。 */
  endAt?: string | undefined;
  /** 操作人 userId（雪花 Long → 字符串）。 */
  operatorId?: string | undefined;
  /** 操作类型，后端为中文自由串（如 新建/更新/删除/重置密码），按等值过滤。 */
  action?: string | undefined;
  /** 目标租户 id（雪花 Long → 字符串）。 */
  targetTenantId?: string | undefined;
  /** 请求链路追踪 id。 */
  requestId?: string | undefined;
};

/** 列表项（摘要，不含 changeBefore/After，需取详情）。 */
export type OpLogListItem = {
  id: string;
  requestId: string | null;
  operatorId: string | null;
  operatorName: string | null;
  module: string | null;
  action: string;
  objectType: string | null;
  objectTypeLabel: string | null;
  objectId: string | null;
  objectDesc: string | null;
  targetTenantId: string | null;
  targetTenantName: string | null;
  method: string | null;
  path: string | null;
  statusCode: number | null;
  durationMs: number | null;
  ip: string | null;
  createdAt: string;
};

/** 分页结果。total 后端为 long（序列化为字符串），api 层转回 number 供分页计算。 */
export type OpLogPage = {
  records: OpLogListItem[];
  total: number;
};

/** ③ 单字段变更（后端已按列字典成句）：label=中文字段名，before/after=渲染后的人话。 */
export type OpLogFieldChange = {
  column: string;
  /** 中文字段名（未配置则回退列名）。 */
  label: string;
  /** 改前（枚举/FK 已译；脱敏列为 "<已变更>"；无值为 null）。 */
  before: string | null;
  /** 改后。 */
  after: string | null;
};

/**
 * ③ 数据变更日志一条记录（同 requestId 下某表某行的字段级改前/改后）。
 * 由 PG 触发器记录、后端按 sys_audit_table_config 列字典成句。
 */
export type OpLogDataChange = {
  tableName: string | null;
  /** INSERT / UPDATE / DELETE（软删归一为 DELETE）。 */
  op: string | null;
  rowPk: string | null;
  fields: OpLogFieldChange[];
};

/**
 * 运营操作日志详情。对应 boot `GET /api/v1/ops/operation-logs/{id}`。
 * 字段级改前/改后已迁往 ③：列表项只给摘要，需看 diff 时按 id+createdAt 取明细，读 {@link dataChanges}。
 */
export type OpLogDetail = {
  id: string;
  requestId: string | null;
  operatorId: string | null;
  operatorName: string | null;
  module: string | null;
  action: string;
  objectType: string | null;
  objectTypeLabel: string | null;
  objectId: string | null;
  objectDesc: string | null;
  targetTenantId: string | null;
  targetTenantName: string | null;
  method: string | null;
  path: string | null;
  statusCode: number | null;
  durationMs: number | null;
  errorMessage: string | null;
  ip: string | null;
  userAgent: string | null;
  createdAt: string | null;
  /** ③ 数据变更日志：同 requestId 下各表逐字段改前/改后（PG 触发器记录）。 */
  dataChanges: OpLogDataChange[];
};

export function opsOperationLogApi(http: HttpClient) {
  const base = '/api/v1/ops/operation-logs';
  return {
    /** 分页查询（多维筛选）。 */
    async listLogs(query?: ListOpLogsQuery): Promise<OpLogPage> {
      const params = {
        ...(query?.page ? { page: query.page } : {}),
        ...(query?.size ? { size: query.size } : {}),
        ...(query?.startAt ? { startAt: query.startAt } : {}),
        ...(query?.endAt ? { endAt: query.endAt } : {}),
        ...(query?.operatorId ? { operatorId: query.operatorId } : {}),
        ...(query?.action ? { action: query.action } : {}),
        ...(query?.targetTenantId ? { targetTenantId: query.targetTenantId } : {}),
        ...(query?.requestId ? { requestId: query.requestId } : {}),
      };
      const res = await http.get<OpLogPage>(base, { params });
      return { ...res.data, total: Number(res.data.total) };
    },
    /** createdAt 为必填查询参数（分区键）。 */
    async getDetail(id: string, createdAt: string): Promise<OpLogDetail> {
      const res = await http.get<OpLogDetail>(`${base}/${encodeURIComponent(id)}`, {
        params: { createdAt },
      });
      return res.data;
    },
  };
}
