import { ApiError } from './http';

/**
 * 与 react-hook-form 的 `setError` 兼容的最小签名（不直接依赖 RHF，避免 api-client
 * 反向耦合上层）。`type` 用 RHF 同款 `type?: string` 形式（无 `| undefined`），
 * 这样在开启 exactOptionalPropertyTypes 的工程里也能直接转交。
 */
export type SetFieldError = (name: string, error: { type?: string; message: string }) => void;

export type ApplyApiErrorOptions = {
  /**
   * 字段名映射：后端字段 → 前端表单字段。当后端命名与表单不一致时（如
   * 后端 `support_hierarchy`、前端 `supportHierarchy`）按此 map 重写。
   */
  fieldMap?: Record<string, string>;
};

/**
 * 把 ApiError 中的字段级错误（422 ProblemDetail.errors[]）映射到 RHF 表单。
 *
 * 返回 true 表示"已消化字段错误"（调用方一般据此跳过通用 toast，避免重复提示）。
 * 非 422 或没有 fieldErrors 时返回 false，调用方按 describeApiError 走 toast 兜底。
 */
export function applyApiErrorToForm(
  err: unknown,
  setError: SetFieldError,
  opts: ApplyApiErrorOptions = {},
): boolean {
  if (!(err instanceof ApiError) || !err.isValidation()) return false;
  const fe = err.fieldErrors;
  const fields = Object.keys(fe);
  if (fields.length === 0) return false;
  for (const f of fields) {
    const target = opts.fieldMap?.[f] ?? f;
    setError(target, { type: 'server', message: fe[f]! });
  }
  return true;
}
