import { useState } from 'react';
import { App, Button, Space, Table, Tooltip, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import {
  CountChip,
  formatDateTime,
  RowActionsBar,
  SecretRevealModal,
  StatusPill,
  toneBadge,
  type RowActionDef,
} from '@wen-saas/shared-ui';
import { describeApiError, type WebhookCreated, type WebhookView } from '@wen-saas/api-client';
import { PermissionGate, useHasPermission } from '@wen-saas/permissions';
import {
  useDeleteWebhook,
  useDisableWebhook,
  useEnableWebhook,
  useRotateWebhookSecret,
  useSendWebhookTest,
  useWebhookList,
} from './webhookQueries';
import { WebhookFormModal } from './WebhookFormModal';
import { WebhookLogsDrawer } from './WebhookLogsDrawer';

const { Text } = Typography;

/**
 * 租户后台 · Webhook 订阅管理。事件推送到外部回调 URL：订阅 CRUD / 启停 / 签名密钥轮换 /
 * 测试投递 / 投递日志。写操作按钮做组件级 webhook.manage 门控（页面路由级为 webhook.view）。
 */
export function WebhookPage() {
  const { message, modal } = App.useApp();
  // 行操作数组按权限拼装（RowActionsBar 吃纯数据，无法逐项包 PermissionGate）
  const canManage = useHasPermission('webhook.manage');
  const listQuery = useWebhookList();
  const [form, setForm] = useState<{ open: boolean; editing: WebhookView | null }>({
    open: false,
    editing: null,
  });
  const [secret, setSecret] = useState<WebhookCreated | null>(null);
  const [logsFor, setLogsFor] = useState<WebhookView | null>(null);

  const enable = useEnableWebhook();
  const disable = useDisableWebhook();
  const del = useDeleteWebhook();
  const rotate = useRotateWebhookSecret();
  const test = useSendWebhookTest();

  const run = async (p: Promise<unknown>, ok: string, fail: string) => {
    try {
      await p;
      message.success(ok);
    } catch (e) {
      message.error(describeApiError(e, fail));
    }
  };

  const doRotate = (r: WebhookView) => {
    modal.confirm({
      title: `轮换「${r.name}」的签名密钥？`,
      content: '旧密钥立即失效，回调端点须同步更换后才能通过验签。',
      onOk: async () => {
        try {
          setSecret(await rotate.mutateAsync(r.id));
        } catch (e) {
          message.error(describeApiError(e, '轮换失败'));
        }
      },
    });
  };

  const doDelete = (r: WebhookView) => {
    modal.confirm({
      title: `删除订阅「${r.name}」？`,
      content: '停止事件分发；历史投递日志保留。',
      okButtonProps: { danger: true },
      onOk: () => run(del.mutateAsync(r.id), '已删除', '删除失败'),
    });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '12px 24px',
          borderBottom: '1px solid var(--divider)',
        }}
      >
        <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--text)' }}>Webhook 订阅</div>
        <Text type="secondary" style={{ fontSize: 12 }}>
          业务事件推送到外部系统回调地址（HMAC 签名，失败自动重试）
        </Text>
        <div style={{ flex: 1 }} />
        <PermissionGate permission="webhook.manage">
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setForm({ open: true, editing: null })}
          >
            新建订阅
          </Button>
        </PermissionGate>
      </div>

      <div style={{ flex: 1, minHeight: 0, padding: 24, overflow: 'auto' }}>
        <Table<WebhookView>
          rowKey="id"
          loading={listQuery.isLoading}
          dataSource={listQuery.data ?? []}
          pagination={false}
          columns={[
            { title: '名称', dataIndex: 'name', width: 180 },
            {
              title: '回调地址',
              dataIndex: 'targetUrl',
              ellipsis: true,
              render: (v: string) => <Text copyable={{ text: v }}>{v}</Text>,
            },
            {
              title: '订阅事件',
              dataIndex: 'subscribedEvents',
              width: 260,
              render: (events: string[]) => (
                <Space size={4} wrap>
                  {events.map((e) => (
                    <CountChip key={e}>{e}</CountChip>
                  ))}
                </Space>
              ),
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 100,
              render: (v: string, r) =>
                v === 'active' ? (
                  <StatusPill badge={toneBadge('success', '启用')} />
                ) : (
                  <Tooltip
                    title={
                      r.consecutiveFailures > 0
                        ? `连续 ${r.consecutiveFailures} 个事件投递失败`
                        : undefined
                    }
                  >
                    <span>
                      <StatusPill badge={toneBadge('neutral', '停用')} />
                    </span>
                  </Tooltip>
                ),
            },
            {
              title: '连续失败',
              dataIndex: 'consecutiveFailures',
              width: 90,
              render: (v: number) =>
                v > 0 ? <Text type="danger">{v}</Text> : <Text type="secondary">0</Text>,
            },
            {
              title: '创建时间',
              dataIndex: 'createdAt',
              width: 160,
              render: (v: string) => formatDateTime(v),
            },
            {
              title: '操作',
              width: 220,
              render: (_v: unknown, r) => {
                // 只禁用「操作中」这一行：以各 mutation 的 variables（订阅 id）判定归属行；
                // 在途的那个操作本身显示 loading / 菜单项置灰。
                const pendingOf = (m: { isPending: boolean; variables?: string | undefined }) =>
                  m.isPending && m.variables === r.id;
                const rowPending = [enable, disable, test, del, rotate].some(pendingOf);
                const primary: RowActionDef[] = [
                  {
                    key: 'logs',
                    label: '日志',
                    disabled: rowPending,
                    onClick: () => setLogsFor(r),
                  },
                ];
                const more: RowActionDef[] = [];
                if (canManage) {
                  primary.push({
                    key: 'edit',
                    label: '编辑',
                    disabled: rowPending,
                    onClick: () => setForm({ open: true, editing: r }),
                  });
                  primary.push(
                    r.status === 'active'
                      ? {
                          key: 'disable',
                          label: '停用',
                          pending: pendingOf(disable),
                          disabled: rowPending,
                          onClick: () => void run(disable.mutateAsync(r.id), '已停用', '停用失败'),
                        }
                      : {
                          key: 'enable',
                          label: '启用',
                          pending: pendingOf(enable),
                          disabled: rowPending,
                          onClick: () => void run(enable.mutateAsync(r.id), '已启用', '启用失败'),
                        },
                  );
                  // 测试投递仅对启用中的订阅可用
                  if (r.status === 'active') {
                    more.push({
                      key: 'test',
                      label: '测试投递',
                      pending: pendingOf(test),
                      disabled: rowPending,
                      onClick: () =>
                        void run(
                          test.mutateAsync(r.id),
                          '测试事件已投递，稍后查看日志',
                          '测试投递失败',
                        ),
                    });
                  }
                  more.push({
                    key: 'rotate',
                    label: '轮换密钥',
                    pending: pendingOf(rotate),
                    disabled: rowPending,
                    onClick: () => doRotate(r),
                  });
                  more.push({
                    key: 'delete',
                    label: '删除',
                    danger: true,
                    pending: pendingOf(del),
                    disabled: rowPending,
                    onClick: () => doDelete(r),
                  });
                }
                return <RowActionsBar primary={primary} more={more} />;
              },
            },
          ]}
        />
      </div>

      <WebhookFormModal
        open={form.open}
        editing={form.editing}
        onClose={() => setForm({ open: false, editing: null })}
        onCreated={setSecret}
      />
      {/* 创建 / 轮换后的签名密钥一次性展示（密文落库后无法再取回明文） */}
      <SecretRevealModal
        open={!!secret}
        title="签名密钥（仅此一次展示）"
        width={520}
        fields={
          secret
            ? [
                {
                  key: 'signingSecret',
                  label: (
                    <>
                      订阅 <Text strong>{secret.name}</Text> 的 HMAC 签名密钥
                    </>
                  ),
                  value: secret.signingSecret,
                  copiedTip: '已复制签名密钥',
                },
              ]
            : []
        }
        warnText="密钥加密落库、之后无法再查看明文，请立即交付给回调端点开发者用于验签。遗失可随时「轮换密钥」生成新的（旧密钥立即失效）。"
        onClose={() => setSecret(null)}
      />
      <WebhookLogsDrawer webhook={logsFor} onClose={() => setLogsFor(null)} />
    </div>
  );
}
