/**
 * 分页查询 wire 适配。
 *
 * boot 列表端点已统一为 PageQuery 规范：wire 参数名固定 `page` / `size`（1-based，默认 20，上限 200）。
 * TS 侧查询对象沿用 `pageSize` 字段命名（与分页响应体的 pageSize 字段对称），
 * 发请求前经此函数统一改写为 wire 名 `size`。
 */
export function toWirePageParams<T extends { pageSize?: number }>(
  query?: T,
): (Omit<T, 'pageSize'> & { size?: number }) | undefined {
  if (!query) return undefined;
  const { pageSize, ...rest } = query;
  return pageSize != null ? { ...rest, size: pageSize } : rest;
}
