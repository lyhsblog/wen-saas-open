import { useMemo, useState } from 'react';
import { App, Alert, Checkbox, Input, Modal, Select, Spin, Tooltip } from 'antd';
import { CountChip } from '@wen-saas/shared-ui';
import { useQuery } from '@tanstack/react-query';
import {
  describeApiError,
  RECOMMENDED_DATA_SCOPE_LABEL,
  type RecommendedDataScope,
} from '@wen-saas/api-client';
import { recommendedRoleApi } from './permissionCatalogApi';

// 文案单源自 @wen-saas/api-client（数字版）。
const SCOPE_OPTIONS = ([1, 2, 3] as RecommendedDataScope[]).map((value) => ({
  value,
  label: RECOMMENDED_DATA_SCOPE_LABEL[value],
}));

type Props = {
  open: boolean;
  templateId: string | null;
  onCancel: () => void;
  onActivated: (roleId: string) => void;
};

/**
 * 推荐角色「复制预览 / 调权」弹窗。
 *
 * 复制前先拉 `/preview`：未购模块 / 未注册的权限点 `valid=false` 标灰过滤、不可勾选；
 * 可在此调角色名 / 数据范围 / 勾选要复制的权限点，确认后按所选子集生成一份独立角色副本。
 */
export function RoleActivatePreviewModal({ open, templateId, onCancel, onActivated }: Props) {
  const { message } = App.useApp();

  const previewQuery = useQuery({
    queryKey: ['recommended-role-preview', templateId],
    queryFn: () => recommendedRoleApi.preview(templateId!),
    enabled: open && !!templateId,
    staleTime: 0,
  });
  const preview = previewQuery.data;

  // 不用 effect 同步表单（lint 禁止 set-state-in-effect）：以 preview 为默认值，
  // 仅记录用户「覆盖项」叠加其上。父级以 key={templateId} 挂载，切模板自动重置。
  const [nameOverride, setNameOverride] = useState<string | null>(null);
  const [scopeOverride, setScopeOverride] = useState<number | null>(null);
  const [unchecked, setUnchecked] = useState<Set<string>>(new Set());
  const [submitting, setSubmitting] = useState(false);

  const validLines = useMemo(() => preview?.permissions.filter((p) => p.valid) ?? [], [preview]);
  const invalidLines = useMemo(() => preview?.permissions.filter((p) => !p.valid) ?? [], [preview]);

  const name = nameOverride ?? preview?.name ?? '';
  const dataScope = scopeOverride ?? preview?.dataScope ?? 1;
  const isChecked = (code: string) => !unchecked.has(code);
  const checkedCount = validLines.filter((p) => isChecked(p.code)).length;

  const toggle = (code: string, on: boolean) => {
    setUnchecked((prev) => {
      const next = new Set(prev);
      if (on) next.delete(code);
      else next.add(code);
      return next;
    });
  };

  const confirm = async () => {
    if (!templateId) return;
    const selected = validLines.filter((p) => isChecked(p.code)).map((p) => p.code);
    if (selected.length === 0) {
      message.warning('请至少勾选 1 个权限点');
      return;
    }
    setSubmitting(true);
    try {
      const res = await recommendedRoleApi.activate(templateId, {
        name: name.trim(),
        dataScope,
        permissionCodes: selected,
      });
      message.success(`已从模板复制「${name.trim()}」，生成了一份独立自定义角色`);
      onActivated(res.roleId);
    } catch (err) {
      message.error(describeApiError(err, '复制失败'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title="从模板复制角色 · 预览调权"
      okText="确认复制"
      cancelText="取消"
      width={560}
      confirmLoading={submitting}
      onOk={() => void confirm()}
      onCancel={onCancel}
      okButtonProps={{ disabled: previewQuery.isPending || checkedCount === 0 }}
      destroyOnHidden
    >
      {previewQuery.isPending || !preview ? (
        <div style={{ padding: 32, textAlign: 'center' }}>
          <Spin />
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Alert
            type="info"
            showIcon
            message="复制将基于模板生成一份独立角色副本，之后与模板互不影响，可自由修改。"
          />
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 4 }}>
              角色名称
            </div>
            <Input value={name} onChange={(e) => setNameOverride(e.target.value)} maxLength={32} />
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 4 }}>
              数据范围
            </div>
            <Select
              value={dataScope}
              onChange={setScopeOverride}
              options={SCOPE_OPTIONS}
              style={{ width: '100%' }}
            />
          </div>

          {invalidLines.length > 0 && (
            <Alert
              type="warning"
              showIcon
              message={`有 ${invalidLines.length} 个权限点因未购模块 / 未注册被过滤，复制后不包含`}
            />
          )}

          <div>
            <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 6 }}>
              可复制权限点（{checkedCount}/{validLines.length}）
            </div>
            <div
              style={{
                maxHeight: 280,
                overflowY: 'auto',
                border: '1px solid var(--border)',
                borderRadius: 'var(--r-md)',
                padding: '8px 12px',
              }}
            >
              {validLines.map((p) => (
                <div key={p.code} style={{ padding: '4px 0' }}>
                  <Checkbox
                    checked={isChecked(p.code)}
                    onChange={(e) => toggle(p.code, e.target.checked)}
                  >
                    {p.name}
                    <span style={{ color: 'var(--text-tertiary)', marginLeft: 6, fontSize: 12 }}>
                      {p.code}
                    </span>
                  </Checkbox>
                </div>
              ))}
              {invalidLines.map((p) => (
                <div key={p.code} style={{ padding: '4px 0', opacity: 0.5 }}>
                  <Tooltip title="当前套餐未购 / 该模块未注册，无法复制">
                    <span style={{ textDecoration: 'line-through', color: 'var(--text-tertiary)' }}>
                      {p.name}
                    </span>
                  </Tooltip>
                  <span style={{ marginLeft: 8 }}>
                    <CountChip>未购 / 未注册</CountChip>
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}
