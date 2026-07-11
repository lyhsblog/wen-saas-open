import type { FormInstance } from 'antd';
import { ApiError } from '@wen-saas/api-client';

/**
 * 把后端错误映射到 AntD Form 的字段级内联错误，命中返回 true（调用方据此跳过兜底 toast）。
 *
 * 两条路径：
 * 1. 422 `errors[]` 字段级校验：直接按 field 挂错误（如 @Pattern/@Email 失败）。
 * 2. 业务错误码（409/422 等只带 `code`+`detail`、无 `errors[]`）：经 `codeFieldMap`
 *    把 code 映射到表单字段（如 USERNAME_CONFLICT→username、WEAK_PASSWORD→password），
 *    用 `detail` 作为该字段的内联错误。不传 `codeFieldMap` 时此路径不生效（行为同旧版）。
 */
export function applyApiErrorToAntdForm(
  err: unknown,
  form: FormInstance,
  codeFieldMap?: Record<string, string>,
): boolean {
  if (!(err instanceof ApiError)) return false;

  // 1) 后端字段级校验 errors[]（优先）
  const entries = Object.entries(err.fieldErrors);
  if (entries.length > 0) {
    form.setFields(entries.map(([name, msg]) => ({ name, errors: [msg ?? '校验失败'] })));
    return true;
  }

  // 2) 业务错误码 → 字段映射（无 errors[] 时的兜底高亮）
  const code = err.problem.code;
  if (codeFieldMap && code && code in codeFieldMap) {
    form.setFields([{ name: codeFieldMap[code], errors: [err.problem.detail ?? '操作失败'] }]);
    return true;
  }

  return false;
}
