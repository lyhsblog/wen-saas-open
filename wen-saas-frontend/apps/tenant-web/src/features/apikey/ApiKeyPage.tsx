import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { App, Button, Space, Table, Tabs, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import {
  CountChip,
  formatDateTime,
  RowTextAction,
  SecretRevealModal,
  StatusPill,
  toneBadge,
} from '@wen-saas/shared-ui';
import { describeApiError, type ApiKeyCreated, type ApiKeyView } from '@wen-saas/api-client';
import { PermissionGate } from '@wen-saas/permissions';
import {
  useApiKeyList,
  useDisableApiKey,
  useEnableApiKey,
  useRotateApiKeySecret,
} from './apiKeyQueries';
import { ApiCallLogsPanel } from './ApiCallLogsPanel';
import { ApiKeyFormModal } from './ApiKeyFormModal';

const { Text } = Typography;

/**
 * 租户后台 · 开放 API 凭证管理。外部系统经 AppKey 调用 /api/v1/openapi/** 的凭证签发与治理：
 * 签发 / 编辑 / 启停 / 密钥轮换。凭证不删除、只启停（调用日志按凭证关联可溯）。
 */
export function ApiKeyPage() {
  const { message, modal } = App.useApp();
  // URL ?tab= 与 Tab key 双向同步：可从文档 / 告警直链「调用日志」，切换回写 URL，
  // 刷新 / 复制链接保留当前页签（replace 模式不污染历史）。
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get('tab') === 'logs' ? 'logs' : 'keys';
  const setActiveTab = (key: string) => {
    const next = new URLSearchParams(searchParams);
    if (key === 'logs') next.set('tab', key);
    else next.delete('tab');
    setSearchParams(next, { replace: true });
  };
  const listQuery = useApiKeyList();
  const [form, setForm] = useState<{ open: boolean; editing: ApiKeyView | null }>({
    open: false,
    editing: null,
  });
  const [secret, setSecret] = useState<ApiKeyCreated | null>(null);

  const enable = useEnableApiKey();
  const disable = useDisableApiKey();
  const rotate = useRotateApiKeySecret();

  const run = async (p: Promise<unknown>, ok: string, fail: string) => {
    try {
      await p;
      message.success(ok);
    } catch (e) {
      message.error(describeApiError(e, fail));
    }
  };

  const doRotate = (r: ApiKeyView) => {
    modal.confirm({
      title: `轮换「${r.name}」的 Secret？`,
      content: 'API Key 标识不变；旧 Secret 立即失效，调用方须同步更换。',
      onOk: async () => {
        try {
          setSecret(await rotate.mutateAsync(r.id));
        } catch (e) {
          message.error(describeApiError(e, '轮换失败'));
        }
      },
    });
  };

  const doDisable = (r: ApiKeyView) => {
    modal.confirm({
      title: `停用凭证「${r.name}」？`,
      content: '即时生效，使用该凭证的调用将全部 401；可随时重新启用。',
      okButtonProps: { danger: true },
      onOk: () => run(disable.mutateAsync(r.id), '已停用', '停用失败'),
    });
  };

  const keysTable = (
    <Table<ApiKeyView>
      rowKey="id"
      loading={listQuery.isLoading}
      dataSource={listQuery.data ?? []}
      pagination={false}
      columns={[
        { title: '名称', dataIndex: 'name', width: 160 },
        {
          title: 'API Key',
          dataIndex: 'apiKey',
          width: 300,
          render: (v: string) => (
            <Text copyable={{ text: v }} style={{ fontFamily: 'monospace', fontSize: 12 }}>
              {v}
            </Text>
          ),
        },
        {
          title: '状态',
          dataIndex: 'status',
          width: 90,
          render: (v: string, r) => {
            const expired = r.expiresAt && new Date(r.expiresAt) < new Date();
            if (expired) return <StatusPill badge={toneBadge('warning', '已过期')} />;
            return (
              <StatusPill
                badge={v === 'active' ? toneBadge('success', '启用') : toneBadge('neutral', '停用')}
              />
            );
          },
        },
        {
          title: '验签',
          dataIndex: 'requireSignature',
          width: 70,
          render: (v: boolean) => (v ? '强制' : '可选'),
        },
        {
          title: 'IP 白名单',
          dataIndex: 'ipWhitelist',
          width: 180,
          render: (list: string[]) =>
            list?.length ? (
              <Space size={4} wrap>
                {list.map((ip) => (
                  <CountChip key={ip}>{ip}</CountChip>
                ))}
              </Space>
            ) : (
              <Text type="secondary">不限</Text>
            ),
        },
        {
          title: '限流',
          dataIndex: 'rateLimitQps',
          width: 90,
          render: (v: number | null) => (v == null ? '租户级' : `${v}/窗口`),
        },
        {
          title: '有效期至',
          dataIndex: 'expiresAt',
          width: 150,
          render: (v: string | null) => (v ? formatDateTime(v) : '长期'),
        },
        {
          title: '最近使用',
          dataIndex: 'lastUsedAt',
          width: 150,
          render: (v: string | null) => (v ? formatDateTime(v) : '-'),
        },
        {
          title: '操作',
          width: 220,
          render: (_v: unknown, r) => {
            // 只禁用「操作中」这一行：以各 mutation 的 variables（凭证 id）判定归属行；
            // 在途的那个操作本身显示 loading。
            const pendingOf = (m: { isPending: boolean; variables?: string | undefined }) =>
              m.isPending && m.variables === r.id;
            const rowPending = [enable, disable, rotate].some(pendingOf);
            return (
              <PermissionGate permission="api_key.manage">
                <Space size="middle">
                  <RowTextAction
                    disabled={rowPending}
                    onClick={() => setForm({ open: true, editing: r })}
                  >
                    编辑
                  </RowTextAction>
                  {r.status === 'active' ? (
                    <RowTextAction
                      danger
                      pending={pendingOf(disable)}
                      disabled={rowPending}
                      onClick={() => doDisable(r)}
                    >
                      停用
                    </RowTextAction>
                  ) : (
                    <RowTextAction
                      pending={pendingOf(enable)}
                      disabled={rowPending}
                      onClick={() => void run(enable.mutateAsync(r.id), '已启用', '启用失败')}
                    >
                      启用
                    </RowTextAction>
                  )}
                  <RowTextAction
                    pending={pendingOf(rotate)}
                    disabled={rowPending}
                    onClick={() => doRotate(r)}
                  >
                    轮换密钥
                  </RowTextAction>
                </Space>
              </PermissionGate>
            );
          },
        },
      ]}
    />
  );

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
        <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--text)' }}>开放 API</div>
        <Text type="secondary" style={{ fontSize: 12 }}>
          外部系统对接凭证（AppKey + HMAC 签名），访问 /api/v1/openapi/** 开放端点
        </Text>
        <div style={{ flex: 1 }} />
        <PermissionGate permission="api_key.manage">
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setForm({ open: true, editing: null })}
          >
            签发凭证
          </Button>
        </PermissionGate>
      </div>

      <div style={{ flex: 1, minHeight: 0, padding: '0 24px 24px', overflow: 'auto' }}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            { key: 'keys', label: '密钥列表', children: keysTable },
            { key: 'logs', label: '调用日志', children: <ApiCallLogsPanel /> },
          ]}
        />
      </div>

      <ApiKeyFormModal
        open={form.open}
        editing={form.editing}
        onClose={() => setForm({ open: false, editing: null })}
        onCreated={setSecret}
      />
      {/* 签发 / 轮换后的一次性凭证展示：apiKey（可再查）+ secret（仅此一次） */}
      <SecretRevealModal
        open={!!secret}
        title="API 凭证（secret 仅此一次展示）"
        fields={
          secret
            ? [
                {
                  key: 'apiKey',
                  label: (
                    <>
                      凭证 <Text strong>{secret.name}</Text> 的 API Key（请求头
                      X-Wen-API-Key，可随时在列表查看）
                    </>
                  ),
                  value: secret.apiKey,
                  copiedTip: '已复制 API Key',
                },
                {
                  key: 'secret',
                  label: 'Secret（HMAC 签名密钥，加密落库后无法再查看明文；遗失可随时轮换）',
                  value: secret.secret,
                  copiedTip: '已复制 Secret',
                },
              ]
            : []
        }
        onClose={() => setSecret(null)}
      />
    </div>
  );
}
