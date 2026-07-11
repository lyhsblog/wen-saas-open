import type { OpLogDataChange } from '@wen-saas/api-client';

/**
 * ③ 数据变更日志展示。把 {@link OpLogDataChange}[]（同 requestId 下各表逐字段改前/改后）
 * 渲染成「op · 表 #行 → 字段: 改前 → 改后」。
 *
 * 字段名/枚举/FK 已由后端按 sys_audit_table_config 列字典成句（label/before/after），
 * 前端直接展示；脱敏列后端写为 "<已变更>"。
 */
export function DataChangesView({
  items,
  title = '数据变更',
}: {
  items: OpLogDataChange[] | null | undefined;
  title?: string;
}) {
  if (!items || items.length === 0) {
    return (
      <div style={{ marginTop: 16 }}>
        <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>{title}</div>
        <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>无字段级变更记录</div>
      </div>
    );
  }
  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>{title}</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {items.map((c, i) => (
          <div key={i}>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 4 }}>
              {opLabel(c.op)} · {c.tableName ?? '—'}
              {c.rowPk ? ` #${c.rowPk}` : ''}
            </div>
            {c.fields.length === 0 ? (
              <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>—</div>
            ) : (
              <div
                style={{
                  background: 'var(--bg-muted)',
                  borderRadius: 'var(--r-md)',
                  padding: 12,
                  fontSize: 12,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 4,
                  maxHeight: 280,
                  overflow: 'auto',
                }}
              >
                {c.fields.map((f) => (
                  <div key={f.column} style={{ wordBreak: 'break-all' }}>
                    <span style={{ fontWeight: 600 }}>{f.label}</span>: {fmt(f.before)}
                    <span style={{ color: 'var(--text-secondary)' }}> → </span>
                    {fmt(f.after)}
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function opLabel(op: string | null): string {
  switch (op) {
    case 'INSERT':
      return '新增';
    case 'UPDATE':
      return '修改';
    case 'DELETE':
      return '删除';
    default:
      return op ?? '变更';
  }
}

function fmt(v: string | null): string {
  return v === null || v === undefined ? '∅' : v;
}
