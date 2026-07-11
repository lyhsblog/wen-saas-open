import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Select, Spin } from 'antd';
import { useTabStatus } from '@wen-saas/shared-ui';
import {
  AlertOutlined,
  CheckCircleOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import type { RoleDetail } from '@wen-saas/domain';
import {
  describeApiError,
  TENANT_DATA_SCOPE_LABEL,
  type TenantDataScope,
} from '@wen-saas/api-client';
import { PermissionGate, ReadonlyButton } from '@wen-saas/permissions';
import { useRoleDataScope, useSaveRoleDataScope, useDataPermissionActions } from './roleQueries';

// 文案单源自 @wen-saas/api-client；sub/risk/icon 为本端 UI 资产
const SCOPE_UI: Record<
  TenantDataScope,
  { sub: string; risk: 'high' | 'medium' | 'low'; icon: React.ReactNode }
> = {
  ALL: { sub: '可查看租户内所有数据，不受部门限制', risk: 'high', icon: <AlertOutlined /> },
  DEPT_AND_BELOW: {
    sub: '仅可查看所在部门及其下属部门的数据',
    risk: 'medium',
    icon: <ExclamationCircleOutlined />,
  },
  DEPT_ONLY: {
    sub: '仅可查看直接所属部门的数据，不含子级部门',
    risk: 'medium',
    icon: <TeamOutlined />,
  },
  SELF: { sub: '仅可查看本人操作创建的数据', risk: 'low', icon: <CheckCircleOutlined /> },
};

const SCOPE_ORDER: TenantDataScope[] = ['ALL', 'DEPT_AND_BELOW', 'DEPT_ONLY', 'SELF'];
const SCOPE_OPTIONS = SCOPE_ORDER.map((value) => ({
  value,
  label: TENANT_DATA_SCOPE_LABEL[value],
  ...SCOPE_UI[value],
}));

const RISK_COLOR: Record<string, string> = {
  high: 'var(--danger)',
  medium: 'var(--warning)',
  low: 'var(--success)',
};
const RISK_LABEL: Record<string, string> = { high: '高风险', medium: '中风险', low: '低风险' };

// 始终返回完整选项（SCOPE_UI / 标签覆盖全部 4 个范围，无 undefined）
const scopeOpt = (value: TenantDataScope) => ({
  value,
  label: TENANT_DATA_SCOPE_LABEL[value],
  ...SCOPE_UI[value],
});

const SCOPE_DESC =
  '此默认范围只对本角色下含数据权限的功能点生效。未单独配置数据范围的功能点继承此默认值；' +
  '某成员在同一功能点上的最终数据范围，由其拥有的、包含该功能点的所有角色配置合并后取最宽松。';

function RiskTag({ risk }: { risk: 'high' | 'medium' | 'low' }) {
  return (
    <span
      style={{
        fontSize: 11,
        padding: '1px 6px',
        borderRadius: 4,
        color: RISK_COLOR[risk],
        background: `${RISK_COLOR[risk]}18`,
      }}
    >
      {RISK_LABEL[risk]}
    </span>
  );
}

type LocalState = {
  defaultScope?: TenantDataScope;
  actionOverrides?: Record<string, TenantDataScope>;
};

type Props = {
  roleId: string;
  detail: RoleDetail;
  /** 编辑态有未保存改动时上抛 true，供父级拦截切角色/切 Tab。 */
  onDirtyChange: (dirty: boolean) => void;
};

export function RoleDataScopeTab({ roleId, detail, onDirtyChange }: Props) {
  const { message } = App.useApp();
  const isOwner = detail.code === 'SYSTEM_OWNER';

  const { data: serverConfig, isLoading } = useRoleDataScope(roleId);
  const { data: allDataPermActions = [] } = useDataPermissionActions();
  const saveDataScope = useSaveRoleDataScope(roleId);

  // 仅涉及此角色已勾选功能权限中含数据权限的功能点
  const grantedSet = useMemo(() => new Set(detail.permissions ?? []), [detail.permissions]);
  const availableActions = useMemo(
    () => allDataPermActions.filter((a) => grantedSet.has(a.code)),
    [allDataPermActions, grantedSet],
  );

  // 查看 / 编辑态：默认查看
  const [editing, setEditing] = useState(false);
  // 仅记录用户相对于服务端数据的本地变更；空对象 = 未改动
  const [local, setLocal] = useState<LocalState>({});

  // 有效值 = 本地覆盖 ?? 服务端数据 ?? 兜底
  const defaultScope: TenantDataScope = local.defaultScope ?? serverConfig?.defaultScope ?? 'SELF';
  const actionOverrides: Record<string, TenantDataScope> = local.actionOverrides ?? {
    ...(serverConfig?.actionOverrides ?? {}),
  };

  // 与服务端有效值做值比对（对齐 RolePermissionsTab）：改回原样即视为无改动
  const serverDefault = serverConfig?.defaultScope ?? 'SELF';
  const serverOverrides = serverConfig?.actionOverrides ?? {};
  const overridesEqual = (
    a: Record<string, TenantDataScope>,
    b: Record<string, TenantDataScope>,
  ) => {
    const ak = Object.keys(a);
    return ak.length === Object.keys(b).length && ak.every((k) => a[k] === b[k]);
  };
  const isDirty = defaultScope !== serverDefault || !overridesEqual(actionOverrides, serverOverrides);

  // 编辑中有未保存改动：上抛给父级拦截切角色/切 Tab，并标记页签「未保存」（误关/刷新有二次确认）
  const editingDirty = editing && isDirty;
  const { setDirty: setTabDirty } = useTabStatus();
  useEffect(() => {
    onDirtyChange(editingDirty);
    setTabDirty(editingDirty);
    return () => {
      onDirtyChange(false);
      setTabDirty(false);
    };
  }, [editingDirty, onDirtyChange, setTabDirty]);

  const handleSetDefaultScope = (val: TenantDataScope) => {
    setLocal((prev) => ({ ...prev, defaultScope: val }));
  };

  const handleSetActionOverride = (code: string, val: TenantDataScope | '') => {
    // 第一次改 action 时，从服务端数据完整拷贝再改，之后维护 local.actionOverrides
    const current = local.actionOverrides ?? { ...(serverConfig?.actionOverrides ?? {}) };
    const next = { ...current };
    if (val === '') delete next[code];
    else next[code] = val;
    setLocal((prev) => ({ ...prev, actionOverrides: next }));
  };

  const exitEdit = () => {
    setLocal({});
    setEditing(false);
  };

  const handleSave = async () => {
    try {
      // 仅持久化当前角色含数据权限功能点的覆盖，剔除已不再拥有的功能点残留
      const prunedOverrides: Record<string, TenantDataScope> = {};
      for (const a of availableActions) {
        const v = actionOverrides[a.code];
        if (v) prunedOverrides[a.code] = v;
      }
      await saveDataScope.mutateAsync({ defaultScope, actionOverrides: prunedOverrides });
      message.success('数据权限已更新');
      exitEdit();
    } catch (err) {
      message.error(describeApiError(err, '保存失败'));
    }
  };

  if (isLoading) {
    return <Spin style={{ margin: 24 }} />;
  }

  const defaultOpt = scopeOpt(defaultScope);

  // ───────────────────────── 主管理员：恒为全部，无可配置 ─────────────────────────
  if (isOwner) {
    return (
      <div style={{ padding: '20px 24px', maxWidth: 660 }}>
        <Alert type="info" showIcon message="主管理员始终拥有全部数据权限，无需配置" />
      </div>
    );
  }

  // ───────────────────────── 查看态 ─────────────────────────
  if (!editing) {
    return (
      <div style={{ padding: '20px 24px', maxWidth: 660 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 16,
          }}
        >
          <div style={{ fontWeight: 600, fontSize: 14 }}>数据权限范围</div>
          <PermissionGate permission="role.update">
            <ReadonlyButton size="small" icon={<EditOutlined />} onClick={() => setEditing(true)}>
              编辑
            </ReadonlyButton>
          </PermissionGate>
        </div>

        <div
          style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 16, lineHeight: 1.6 }}
        >
          {SCOPE_DESC}
        </div>

        {/* 角色默认范围 */}
        <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 6 }}>
          角色默认范围
        </div>
        <div
          style={{
            padding: '12px 14px',
            border: '1px solid var(--border)',
            borderRadius: 'var(--r-md)',
            background: 'var(--bg-elev)',
            marginBottom: 24,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 14, fontWeight: 500 }}>{defaultOpt.label}</span>
            <RiskTag risk={defaultOpt.risk} />
          </div>
          <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>
            {defaultOpt.sub}
          </div>
        </div>

        {/* 按功能点单独配置：列出本角色全部含数据权限的功能点，一目了然 */}
        <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 6 }}>
          按功能点单独配置（共 {availableActions.length} 个含数据权限的功能点）
        </div>
        {availableActions.length === 0 ? (
          <div
            style={{
              padding: '12px 14px',
              border: '1px dashed var(--border)',
              borderRadius: 'var(--r-md)',
              fontSize: 13,
              color: 'var(--text-tertiary)',
            }}
          >
            该角色暂无含数据权限的功能点，可在「功能权限」中勾选后再配置
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {availableActions.map((action) => {
              const override = actionOverrides[action.code];
              const opt = scopeOpt(override ?? defaultScope);
              return (
                <div
                  key={action.code}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '8px 12px',
                    border: '1px solid var(--border)',
                    borderRadius: 'var(--r-sm)',
                    background: 'var(--bg-elev)',
                  }}
                >
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 500, lineHeight: 1.4 }}>
                      {action.name}
                    </div>
                    <div
                      style={{
                        fontSize: 11,
                        color: 'var(--text-tertiary)',
                        fontFamily: 'monospace',
                      }}
                    >
                      {action.code}
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                    <span style={{ fontSize: 13 }}>{opt.label}</span>
                    <RiskTag risk={opt.risk} />
                    <span
                      style={{
                        fontSize: 11,
                        color: override ? 'var(--brand)' : 'var(--text-tertiary)',
                      }}
                    >
                      {override ? '已单独配置' : '继承默认'}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    );
  }

  // ───────────────────────── 编辑态 ─────────────────────────
  return (
    <div style={{ padding: '20px 24px', maxWidth: 660 }}>
      {/* ── 角色默认范围 ── */}
      <div style={{ marginBottom: 28 }}>
        <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 8 }}>角色默认范围</div>
        <Alert
          type="info"
          showIcon
          message="此默认范围只对本角色下含数据权限的功能点生效"
          description="未单独配置的功能点继承此默认值；某成员在同一功能点的最终数据范围，由其拥有的、包含该功能点的所有角色配置合并后取最宽松。"
          style={{ marginBottom: 12 }}
        />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {SCOPE_OPTIONS.map((opt) => {
            const isSel = defaultScope === opt.value;
            return (
              <div
                key={opt.value}
                onClick={() => handleSetDefaultScope(opt.value)}
                style={{
                  padding: '12px 14px',
                  border: `2px solid ${isSel ? 'var(--brand)' : 'var(--border)'}`,
                  borderRadius: 'var(--r-md)',
                  background: isSel ? 'var(--brand-bg)' : 'var(--bg-elev)',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                }}
              >
                <div
                  style={{
                    width: 18,
                    height: 18,
                    borderRadius: '50%',
                    border: `2px solid ${isSel ? 'var(--brand)' : 'var(--border)'}`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }}
                >
                  {isSel && (
                    <div
                      style={{
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        background: 'var(--brand)',
                      }}
                    />
                  )}
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: 13, fontWeight: 500 }}>{opt.label}</span>
                    <RiskTag risk={opt.risk} />
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>
                    {opt.sub}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* ── 按功能点单独配置 ── */}
      {availableActions.length > 0 ? (
        <div style={{ marginBottom: 24 }}>
          <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4 }}>按功能点单独配置</div>
          <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 12 }}>
            覆盖该功能点的数据范围；留空则继承角色默认值
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {availableActions.map((action) => (
              <div
                key={action.code}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: '8px 12px',
                  border: '1px solid var(--border)',
                  borderRadius: 'var(--r-sm)',
                  background: 'var(--bg-elev)',
                }}
              >
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 500, lineHeight: 1.4 }}>
                    {action.name}
                  </div>
                  <div
                    style={{ fontSize: 11, color: 'var(--text-tertiary)', fontFamily: 'monospace' }}
                  >
                    {action.code}
                  </div>
                </div>
                <Select<TenantDataScope | ''>
                  style={{ width: 200, flexShrink: 0 }}
                  value={actionOverrides[action.code] ?? ''}
                  onChange={(val) => handleSetActionOverride(action.code, val)}
                  options={[
                    { value: '', label: '继承默认' },
                    ...SCOPE_OPTIONS.map((o) => ({ value: o.value, label: o.label })),
                  ]}
                />
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div style={{ marginBottom: 24, fontSize: 13, color: 'var(--text-tertiary)' }}>
          该角色暂无含数据权限的功能点，可在「功能权限」中勾选后再单独配置。
        </div>
      )}

      {/* ── 操作按钮 ── */}
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
        <Button onClick={exitEdit}>取消</Button>
        <PermissionGate permission="role.update">
          <ReadonlyButton
            type="primary"
            loading={saveDataScope.isPending}
            disabled={!isDirty}
            onClick={() => {
              void handleSave();
            }}
          >
            保存
          </ReadonlyButton>
        </PermissionGate>
      </div>
    </div>
  );
}
