import { useEffect, useState } from 'react';
import { CountChip, ErrorState, StatusPill, toneBadge, useTabStatus } from '@wen-saas/shared-ui';
import { App, Button, Card, Collapse, Select, Space, Spin, Switch } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import type {
  ExportApprovalAuthority,
  ExportObjectRule,
  ExportPolicyView,
} from '@wen-saas/api-client';
import { describeApiError } from '@wen-saas/api-client';
import { useExportPolicy, useUpdateExportPolicy } from './complianceQueries';
import { moduleDisplayName } from '../../lib/moduleNames';

const AUTHORITY_OPTIONS = [
  { value: 'MAIN_ADMIN', label: '仅主管理员' },
  { value: 'ROLE_PERMISSION', label: '持「导出审批」权限的角色' },
];

const TIMEOUT_OPTIONS = [
  { value: 0, label: '不提醒' },
  { value: 12, label: '12 小时' },
  { value: 24, label: '24 小时' },
  { value: 48, label: '48 小时' },
];

/**
 * 导出审批策略配置卡片（仅有 base.compliance.config 权限者渲染）。审批触发由「各导出对象是否需审批」
 * 开关驱动（不再按数量阈值），导出对象按所属模块分组、可收起/展开；另配审批权限 / 超时提醒。
 */
export function ExportPolicyCard() {
  const { data, isLoading, isError, refetch } = useExportPolicy();
  return (
    <Card title="导出审批策略" size="small">
      {isError ? (
        <ErrorState
          title="策略加载失败"
          description="请稍后重试；若持续失败请联系管理员。"
          primaryAction={{ label: '重试', onClick: () => void refetch() }}
        />
      ) : isLoading || !data ? (
        <Spin />
      ) : (
        <PolicyForm initial={data} />
      )}
    </Card>
  );
}

/** 加载完成后挂载；用 props 初始化本地态（避免 effect 内 setState）。 */
function PolicyForm({ initial }: { initial: ExportPolicyView }) {
  const { message } = App.useApp();
  const update = useUpdateExportPolicy();
  const [switches, setSwitches] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(initial.objects.map((o) => [o.code, o.requiresApproval])),
  );
  const [authority, setAuthority] = useState<ExportApprovalAuthority>(initial.approvalAuthority);
  const [timeout, setTimeoutHours] = useState(initial.timeoutHours ?? 0);

  // 与服务端当前值比对得出脏状态：保存成功后 query 失效重取，initial 更新即自动归零
  const dirty =
    initial.objects.some((o) => (switches[o.code] ?? o.requiresApproval) !== o.requiresApproval) ||
    authority !== initial.approvalAuthority ||
    timeout !== (initial.timeoutHours ?? 0);

  // 有未保存改动时标记页签「未保存」，误关 / 刷新会被二次确认拦截
  const { setDirty: setTabDirty } = useTabStatus();
  useEffect(() => {
    setTabDirty(dirty);
    return () => setTabDirty(false);
  }, [dirty, setTabDirty]);

  const discard = () => {
    setSwitches(Object.fromEntries(initial.objects.map((o) => [o.code, o.requiresApproval])));
    setAuthority(initial.approvalAuthority);
    setTimeoutHours(initial.timeoutHours ?? 0);
  };

  const save = async () => {
    try {
      await update.mutateAsync({
        objects: initial.objects.map((o) => ({
          code: o.code,
          requiresApproval: switches[o.code] ?? o.requiresApproval,
        })),
        approvalAuthority: authority,
        timeoutHours: timeout > 0 ? timeout : null,
      });
      void message.success('策略已更新');
    } catch (err) {
      void message.error(describeApiError(err, '保存失败'));
    }
  };

  // 按所属模块分组（保持后端声明顺序）
  const groups = new Map<string, ExportObjectRule[]>();
  for (const o of initial.objects) {
    const list = groups.get(o.module) ?? [];
    list.push(o);
    groups.set(o.module, list);
  }

  const collapseItems = Array.from(groups.entries()).map(([moduleCode, objs]) => {
    const needCount = objs.filter((o) => switches[o.code] ?? o.requiresApproval).length;
    return {
      key: moduleCode,
      label: (
        <Space>
          <span style={{ fontWeight: 600 }}>{moduleDisplayName(moduleCode)}</span>
          <CountChip>{objs.length} 项</CountChip>
          {needCount > 0 && <StatusPill badge={toneBadge('warning', `${needCount} 需审批`)} />}
        </Space>
      ),
      children: (
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          {objs.map((o) => (
            <div
              key={o.code}
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}
            >
              <span>{o.name}</span>
              <Switch
                checked={switches[o.code] ?? o.requiresApproval}
                onChange={(checked) => setSwitches((s) => ({ ...s, [o.code]: checked }))}
              />
            </div>
          ))}
        </Space>
      ),
    };
  });

  return (
    <>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Field label="各导出对象是否需审批" help="开启后，该对象的导出需先经审批通过才执行">
          <Collapse
            size="small"
            defaultActiveKey={Array.from(groups.keys())}
            items={collapseItems}
          />
        </Field>
        <Field label="审批权限" help="可在「角色与权限」中赋予指定角色">
          <Select
            value={authority}
            onChange={(v) => setAuthority(v)}
            options={AUTHORITY_OPTIONS}
            style={{ width: 320 }}
          />
        </Field>
        <Field label="审批超时提醒" help="超过该时长未处理计入总览「超时未处理」">
          <Select
            value={timeout}
            onChange={(v) => setTimeoutHours(v)}
            options={TIMEOUT_OPTIONS}
            style={{ width: 220 }}
          />
        </Field>
      </Space>

      {/* 吸底保存栏：滚动中始终可见，改动前保存按钮禁用并提示保存模型。
          注意不能放进上面的 Space——ant-space-item 包装盒等高于自身，sticky 会失去活动空间。 */}
      <div
        style={{
          position: 'sticky',
          bottom: 0,
          zIndex: 10,
          margin: '16px -12px -12px',
          padding: '12px 16px',
          background: 'var(--bg-elev, #fff)',
          borderTop: '1px solid var(--divider, #f0f0f0)',
          display: 'flex',
          alignItems: 'center',
          gap: 12,
        }}
      >
        <span
          style={{
            fontSize: 12,
            color: dirty ? 'var(--warning, #d46b08)' : 'var(--text-tertiary, #8c8c8c)',
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          <InfoCircleOutlined />
          {dirty ? '有未保存的更改' : '修改后需点击「保存策略」生效'}
        </span>
        <div style={{ flex: 1 }} />
        <Button disabled={!dirty || update.isPending} onClick={discard}>
          放弃更改
        </Button>
        <Button
          type="primary"
          disabled={!dirty}
          loading={update.isPending}
          onClick={() => void save()}
        >
          保存策略
        </Button>
      </div>
    </>
  );
}

function Field({
  label,
  help,
  children,
}: {
  label: string;
  help: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <div style={{ fontWeight: 600, marginBottom: 4 }}>{label}</div>
      {children}
      <div style={{ color: 'var(--text-tertiary, #8c8c8c)', fontSize: 12, marginTop: 4 }}>
        {help}
      </div>
    </div>
  );
}
