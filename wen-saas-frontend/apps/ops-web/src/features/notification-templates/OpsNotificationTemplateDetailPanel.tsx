import { useMemo, useState } from 'react';
import { App, Button, Empty, Segmented, Spin, Switch } from 'antd';
import { CloseOutlined, EditOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { opsNotificationTemplateApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { CountChip, ErrorState, StatusPill } from '@wen-saas/shared-ui';
import { OpsNotificationTemplateFormModal } from './OpsNotificationTemplateFormModal';
import {
  useDeleteTemplate,
  useDisableTemplate,
  useEnableTemplate,
} from './hooks/useNotificationTemplateMutations';
import {
  CHANNEL_ORDER,
  categoryLabel,
  channelLabel,
  priorityLabel,
  renderWithVars,
  statusBadge,
} from './utils/meta';

type Props = {
  code: string;
  onClose: () => void;
  onDeleted: () => void;
};

const TITLE_CHANNELS = new Set(['STATION', 'EMAIL']);

export function OpsNotificationTemplateDetailPanel({ code, onClose, onDeleted }: Props) {
  const { modal } = App.useApp();
  const [editOpen, setEditOpen] = useState(false);
  const [preview, setPreview] = useState(false);
  const [activeChannel, setActiveChannel] = useState<string | null>(null);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['ops', 'notification-template', code],
    queryFn: () => opsNotificationTemplateApi(http).getTemplate(code),
  });

  const enable = useEnableTemplate();
  const disable = useDisableTemplate();
  const del = useDeleteTemplate();

  const orderedChannels = useMemo(
    () => (data ? CHANNEL_ORDER.filter((c) => data.defaultChannels.includes(c)) : []),
    [data],
  );
  const channel = activeChannel ?? orderedChannels[0] ?? null;

  const confirmDelete = () => {
    modal.confirm({
      title: '确认删除此模板？',
      content: '删除后该模板不再可用，已发送的历史消息保留快照。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await del.mutateAsync(code);
        onDeleted();
      },
    });
  };

  const toggleStatus = (active: boolean) => {
    if (active) void enable.mutateAsync(code);
    else void disable.mutateAsync(code);
  };

  return (
    <div
      style={{
        flex: 1,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--bg)',
        overflow: 'hidden',
      }}
    >
      {isLoading ? (
        <div style={{ padding: 80, textAlign: 'center' }}>
          <Spin size="large" />
        </div>
      ) : isError || !data ? (
        <ErrorState
          title="加载通知模板详情失败"
          description={error instanceof Error ? error.message : undefined}
          primaryAction={{ label: '重试', onClick: () => void refetch() }}
        />
      ) : (
        <>
          {/* Header */}
          <div
            style={{
              padding: '16px 24px',
              borderBottom: '1px solid var(--divider)',
              display: 'flex',
              alignItems: 'flex-start',
              gap: 12,
              flexShrink: 0,
            }}
          >
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                <span style={{ fontWeight: 600, fontSize: 18 }}>{data.name}</span>
                <StatusPill badge={statusBadge(data.status)} />
                {data.preset && <CountChip tone="brand">预置</CountChip>}
              </div>
              <div
                style={{
                  fontSize: 12,
                  color: 'var(--text-tertiary)',
                  display: 'flex',
                  gap: 12,
                  flexWrap: 'wrap',
                }}
              >
                <code style={{ fontFamily: 'var(--font-mono)' }}>{data.templateCode}</code>
                {data.moduleCode && <span>模块 {data.moduleCode}</span>}
                <span>优先级 {priorityLabel(data.priority)}</span>
                <span>类别 {categoryLabel(data.notificationCategory)}</span>
              </div>
              {data.description && (
                <div style={{ marginTop: 8, color: 'var(--text-secondary)', fontSize: 13 }}>
                  {data.description}
                </div>
              )}
            </div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              {data.preset ? (
                <span style={{ color: 'var(--text-tertiary)', fontSize: 13 }}>
                  底座预置（只读）
                </span>
              ) : (
                <>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
                    <Switch
                      checked={data.status === 'ACTIVE'}
                      loading={enable.isPending || disable.isPending}
                      onChange={toggleStatus}
                    />
                    {data.status === 'ACTIVE' ? '启用' : '停用'}
                  </span>
                  <Button icon={<EditOutlined />} onClick={() => setEditOpen(true)}>
                    编辑
                  </Button>
                  <Button danger onClick={confirmDelete}>
                    删除
                  </Button>
                </>
              )}
              <Button type="text" icon={<CloseOutlined />} onClick={onClose} aria-label="关闭" />
            </div>
          </div>

          {/* Body */}
          <div style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: '16px 24px 24px' }}>
            {/* 可用变量 */}
            <SectionTitle>可用变量</SectionTitle>
            {data.availableVars.length === 0 ? (
              <div style={{ color: 'var(--text-tertiary)', fontSize: 13, marginBottom: 16 }}>
                未声明可用变量
              </div>
            ) : (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 16 }}>
                {data.availableVars.map((v) => (
                  <CountChip key={v.name}>
                    <code style={{ fontFamily: 'var(--font-mono)' }}>{`{${v.name}}`}</code>
                    {v.sampleValue && (
                      <span style={{ color: 'var(--text-tertiary)', marginLeft: 6 }}>
                        例：{v.sampleValue}
                      </span>
                    )}
                    {v.required && (
                      <span style={{ color: 'var(--danger)', marginLeft: 6 }}>必填</span>
                    )}
                  </CountChip>
                ))}
              </div>
            )}

            {/* 渠道与文案 */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                marginBottom: 8,
              }}
            >
              <SectionTitle noMargin>渠道与文案</SectionTitle>
              <div style={{ flex: 1 }} />
              <Button size="small" type="text" onClick={() => setPreview((p) => !p)}>
                {preview ? '关闭预览' : '预览'}
              </Button>
            </div>
            {orderedChannels.length === 0 || !channel ? (
              <Empty description="未配置渠道" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <>
                <Segmented
                  value={channel}
                  onChange={(v) => setActiveChannel(String(v))}
                  options={orderedChannels.map((c) => ({ label: channelLabel(c), value: c }))}
                  style={{ marginBottom: 12 }}
                />
                <ChannelView
                  title={data.contents[channel]?.title ?? null}
                  body={data.contents[channel]?.body ?? null}
                  needTitle={TITLE_CHANNELS.has(channel)}
                  preview={preview}
                  vars={data.availableVars}
                />
              </>
            )}
          </div>

          <OpsNotificationTemplateFormModal
            open={editOpen}
            editing={data}
            onClose={() => setEditOpen(false)}
          />
        </>
      )}
    </div>
  );
}

function ChannelView({
  title,
  body,
  needTitle,
  preview,
  vars,
}: {
  title: string | null;
  body: string | null;
  needTitle: boolean;
  preview: boolean;
  vars: { name: string; sampleValue: string | null; required: boolean }[];
}) {
  const showTitle = (raw: string | null) => (preview ? renderWithVars(raw, vars) : (raw ?? ''));
  return (
    <div
      style={{
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-md)',
        padding: 16,
        background: 'var(--bg-elev)',
      }}
    >
      {needTitle && (
        <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 8 }}>
          {showTitle(title) || <span style={{ color: 'var(--text-tertiary)' }}>（无标题）</span>}
        </div>
      )}
      <div style={{ whiteSpace: 'pre-wrap', color: 'var(--text-secondary)', fontSize: 13 }}>
        {showTitle(body) || <span style={{ color: 'var(--text-tertiary)' }}>（无正文）</span>}
      </div>
    </div>
  );
}

function SectionTitle({ children, noMargin }: { children: React.ReactNode; noMargin?: boolean }) {
  return (
    <div style={{ fontWeight: 600, fontSize: 14, marginBottom: noMargin ? 0 : 8 }}>{children}</div>
  );
}
