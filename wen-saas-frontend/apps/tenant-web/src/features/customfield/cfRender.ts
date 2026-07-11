const LABEL_SUFFIX = '__label';

/** 安全转展示串：仅对原始类型 stringify，对象返回空串（避免 [object Object]）。 */
function primStr(v: unknown): string {
  if (typeof v === 'string') return v;
  if (typeof v === 'number' || typeof v === 'boolean') return String(v);
  return '';
}

/** 自定义字段值渲染：enum/ref 优先 __label，bool 渲染是/否，空值 '-'。 */
export function renderCfValue(
  type: string,
  fieldCode: string,
  extra: Record<string, unknown>,
): string {
  const label = primStr(extra[`${fieldCode}${LABEL_SUFFIX}`]);
  if (label !== '') return label;
  const v = extra[fieldCode];
  if (v == null || v === '') return '-';
  if (type === 'bool') return v === true || v === 'true' ? '是' : '否';
  return primStr(v) || '-';
}
