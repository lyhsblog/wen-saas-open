import { useMemo, useState } from 'react';
import { Alert, App, Button, Checkbox, Form, Input, Modal, Segmented, Select, Space } from 'antd';
import { CountChip } from '@wen-saas/shared-ui';
import { useQuery } from '@tanstack/react-query';
import { opsNotificationTemplateApi } from '@wen-saas/api-client';
import type {
  ChannelContent,
  NotificationTemplateDetail,
  NotificationTemplatePriority,
  NotificationTemplateStatus,
  NotificationVarDef,
} from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { applyApiErrorToAntdForm } from '../tenants/utils/form';
import { useCreateTemplate, useUpdateTemplate } from './hooks/useNotificationTemplateMutations';
import { CATEGORY_OPTIONS, CHANNEL_ORDER, PRIORITY_OPTIONS, renderWithVars } from './utils/meta';

type Props = {
  open: boolean;
  /** 传入 detail 为编辑模式；不传为新建模式。 */
  editing: NotificationTemplateDetail | null;
  onClose: () => void;
  onSaved?: (code: string) => void;
};

type ScalarForm = {
  templateCode: string;
  moduleCode: string;
  name: string;
  description: string;
  priority: NotificationTemplatePriority;
  notificationCategory: string | undefined;
  status: NotificationTemplateStatus;
};

const CODE_PATTERN = /^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$/;
const STATION = 'STATION';
const SMS = 'SMS';
const TITLE_CHANNELS = new Set([STATION, 'EMAIL']);

function emptyContent(): ChannelContent {
  return { title: '', body: '' };
}

/**
 * 薄包装：用 `open` + 编码作 key 让内部编辑态每次打开重新挂载初始化（useState 初始值），
 * 避免在 effect 里 setState（项目 eslint 规则 react-hooks/set-state-in-effect 禁止）。
 */
export function OpsNotificationTemplateFormModal(props: Props) {
  const { open, editing } = props;
  return (
    <TemplateFormModalInner
      key={open ? (editing?.templateCode ?? '__new__') : '__closed__'}
      {...props}
    />
  );
}

function TemplateFormModalInner({ open, editing, onClose, onSaved }: Props) {
  const { modal } = App.useApp();
  const [form] = Form.useForm<ScalarForm>();
  const isEdit = editing != null;

  const initialChannels = editing
    ? editing.defaultChannels.includes(STATION)
      ? editing.defaultChannels
      : [STATION, ...editing.defaultChannels]
    : [STATION];
  const initialContents: Record<string, ChannelContent> = (() => {
    const c: Record<string, ChannelContent> = {};
    if (editing) {
      for (const [k, v] of Object.entries(editing.contents)) {
        c[k] = { title: v.title ?? '', body: v.body ?? '' };
      }
    }
    if (!c[STATION]) c[STATION] = emptyContent();
    return c;
  })();

  // 渠道开关 / 各渠道文案 / 可用变量：嵌套结构走本地 state（不入 antd Form）。
  const [channels, setChannels] = useState<string[]>(initialChannels);
  const [contents, setContents] = useState<Record<string, ChannelContent>>(initialContents);
  const [vars, setVars] = useState<NotificationVarDef[]>(editing?.availableVars ?? []);
  const [activeChannel, setActiveChannel] = useState<string>(STATION);
  const [preview, setPreview] = useState(false);

  const { data: channelMeta } = useQuery({
    queryKey: ['ops', 'notification-template-channels'],
    queryFn: () => opsNotificationTemplateApi(http).listChannels(),
    enabled: open,
  });

  const create = useCreateTemplate();
  const update = useUpdateTemplate(editing?.templateCode ?? '');
  const isPending = create.isPending || update.isPending;

  const orderedChannels = useMemo(
    () => CHANNEL_ORDER.filter((c) => channels.includes(c)),
    [channels],
  );

  const toggleChannel = (channel: string, on: boolean) => {
    if (channel === STATION) return; // 保底通道不可禁用
    setChannels((prev) => {
      if (on) return prev.includes(channel) ? prev : [...prev, channel];
      return prev.filter((c) => c !== channel);
    });
    if (on) {
      setContents((prev) => (prev[channel] ? prev : { ...prev, [channel]: emptyContent() }));
      setActiveChannel(channel);
    } else if (activeChannel === channel) {
      setActiveChannel(STATION);
    }
  };

  const updateContent = (channel: string, patch: Partial<ChannelContent>) => {
    setContents((prev) => ({
      ...prev,
      [channel]: { ...(prev[channel] ?? emptyContent()), ...patch },
    }));
  };

  /** 客户端校验启用渠道文案：正文必填；STATION/EMAIL 标题必填。返回错误信息或 null。 */
  function validateContents(): string | null {
    for (const ch of orderedChannels) {
      const cc = contents[ch];
      if (!cc || !cc.body?.trim()) return `渠道「${ch}」正文不能为空`;
      if (TITLE_CHANNELS.has(ch) && !cc.title?.trim()) return `渠道「${ch}」标题不能为空`;
    }
    return null;
  }

  const submit = async () => {
    const values = await form.validateFields();
    const contentError = validateContents();
    if (contentError) {
      void modal.error({ title: '文案校验未通过', content: contentError });
      return;
    }
    const payloadContents: Record<string, ChannelContent> = {};
    for (const ch of orderedChannels) payloadContents[ch] = contents[ch] ?? emptyContent();
    const cleanVars = vars
      .filter((v) => v.name.trim())
      .map((v) => ({
        name: v.name.trim(),
        sampleValue: v.sampleValue ?? null,
        required: v.required,
      }));

    try {
      if (isEdit) {
        await update.mutateAsync({
          name: values.name,
          description: values.description ? values.description : null,
          availableVars: cleanVars,
          defaultChannels: orderedChannels,
          contents: payloadContents,
          notificationCategory: values.notificationCategory ?? null,
          priority: values.priority,
          status: values.status,
        });
        onSaved?.(editing.templateCode);
      } else {
        const detail = await create.mutateAsync({
          templateCode: values.templateCode,
          moduleCode: values.moduleCode ? values.moduleCode : null,
          name: values.name,
          description: values.description ? values.description : null,
          availableVars: cleanVars,
          defaultChannels: orderedChannels,
          contents: payloadContents,
          notificationCategory: values.notificationCategory ?? null,
          priority: values.priority,
          status: values.status,
        });
        onSaved?.(detail.templateCode);
      }
      onClose();
    } catch (err) {
      // 字段级错误（409 CODE_CONFLICT / 422）写入表单；非字段级由 mutation onError toast。
      applyApiErrorToAntdForm(err, form);
    }
  };

  const activeContent = contents[activeChannel] ?? emptyContent();
  const needTitle = TITLE_CHANNELS.has(activeChannel);

  const editor = (
    <div style={{ display: 'flex', gap: 16 }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        {needTitle && (
          <Input
            placeholder={activeChannel === 'EMAIL' ? '邮件主题' : '标题'}
            value={activeContent.title ?? ''}
            disabled={isPending}
            onChange={(e) => updateContent(activeChannel, { title: e.target.value })}
            style={{ marginBottom: 8 }}
          />
        )}
        <Input.TextArea
          rows={6}
          placeholder={activeChannel === SMS ? '短信正文（建议 ≤ 70 字）' : '正文'}
          value={activeContent.body ?? ''}
          disabled={isPending}
          onChange={(e) => updateContent(activeChannel, { body: e.target.value })}
        />
        {activeChannel === SMS && (
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>
            当前 {(activeContent.body ?? '').length} 字 · 建议 ≤ 70 字（按条计费）
          </div>
        )}
      </div>
      {preview && (
        <div
          style={{
            width: 240,
            flexShrink: 0,
            background: 'var(--bg-muted)',
            borderRadius: 'var(--r-sm)',
            padding: 12,
            fontSize: 13,
          }}
        >
          <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginBottom: 6 }}>
            预览（示例值替换）
          </div>
          {needTitle && (
            <div style={{ fontWeight: 600, marginBottom: 6 }}>
              {renderWithVars(activeContent.title, vars) || '（无标题）'}
            </div>
          )}
          <div style={{ whiteSpace: 'pre-wrap', color: 'var(--text-secondary)' }}>
            {renderWithVars(activeContent.body, vars) || '（无正文）'}
          </div>
        </div>
      )}
    </div>
  );

  const footer = [
    <Button key="cancel" onClick={onClose}>
      取消
    </Button>,
    <Button key="save" type="primary" loading={isPending} onClick={() => void submit()}>
      保存
    </Button>,
  ];

  return (
    <Modal
      open={open}
      title={isEdit ? `编辑模板 · ${editing.name}` : '新建通知模板'}
      onCancel={onClose}
      footer={footer}
      mask={{ closable: false }}
      destroyOnHidden
      width={720}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="平台默认模板（业务方 / 运营维护）。站内信为保底通道，始终发送、不可禁用。"
      />
      <Form
        form={form}
        layout="vertical"
        style={{ marginTop: 8 }}
        initialValues={{
          templateCode: editing?.templateCode ?? '',
          moduleCode: editing?.moduleCode ?? '',
          name: editing?.name ?? '',
          description: editing?.description ?? '',
          priority: editing?.priority ?? 'NORMAL',
          notificationCategory: editing?.notificationCategory ?? undefined,
          status: editing?.status ?? 'ACTIVE',
        }}
      >
        <div style={{ display: 'flex', gap: 12 }}>
          <Form.Item
            name="name"
            label="模板名称"
            style={{ flex: 1 }}
            rules={[{ required: true, message: '请输入模板名称' }, { max: 255 }]}
          >
            <Input placeholder="如 客户跟进提醒" disabled={isPending} />
          </Form.Item>
          <Form.Item
            name="moduleCode"
            label="业务模块"
            style={{ width: 160 }}
            extra={isEdit ? '来源模块创建后不可改' : undefined}
            rules={[{ max: 80 }]}
          >
            <Input
              placeholder="如 crm"
              disabled={isEdit || isPending}
              style={{ fontFamily: 'var(--font-mono)' }}
            />
          </Form.Item>
        </div>
        <Form.Item
          name="templateCode"
          label="模板编码"
          extra={
            isEdit
              ? '编码创建后不可修改'
              : '格式 {module}.{name}，小写字母 / 数字 / 下划线，创建后不可改'
          }
          rules={
            isEdit
              ? []
              : [
                  { required: true, message: '请输入模板编码' },
                  {
                    pattern: CODE_PATTERN,
                    message: '格式：{module}.{name}，如 crm.follow_reminder',
                  },
                ]
          }
        >
          <Input
            placeholder="如 crm.follow_reminder"
            disabled={isEdit || isPending}
            style={{ fontFamily: 'var(--font-mono)' }}
          />
        </Form.Item>
        <Form.Item name="description" label="描述">
          <Input.TextArea rows={2} placeholder="用途说明（选填）" disabled={isPending} />
        </Form.Item>
        <div style={{ display: 'flex', gap: 12 }}>
          <Form.Item name="priority" label="优先级" style={{ width: 160 }}>
            <Select options={PRIORITY_OPTIONS} disabled={isPending} />
          </Form.Item>
          <Form.Item name="notificationCategory" label="通知类别" style={{ flex: 1 }}>
            <Select
              allowClear
              placeholder="不限（不参与偏好过滤）"
              options={CATEGORY_OPTIONS}
              disabled={isPending}
            />
          </Form.Item>
          <Form.Item name="status" label="状态" style={{ width: 120 }}>
            <Select
              options={[
                { label: '启用', value: 'ACTIVE' },
                { label: '停用', value: 'INACTIVE' },
              ]}
              disabled={isPending}
            />
          </Form.Item>
        </div>

        <Form.Item label="可用变量（含示例值，供预览）">
          <VarsEditor value={vars} disabled={isPending} onChange={setVars} />
        </Form.Item>

        <Form.Item
          label={
            <Space>
              <span>渠道与文案</span>
              <Button size="small" type="text" onClick={() => setPreview((p) => !p)}>
                {preview ? '关闭预览' : '预览'}
              </Button>
            </Space>
          }
        >
          <Space style={{ marginBottom: 10, flexWrap: 'wrap' }}>
            {(channelMeta ?? []).map((m) => (
              <Checkbox
                key={m.channel}
                checked={channels.includes(m.channel)}
                disabled={m.fallback || isPending}
                onChange={(e) => toggleChannel(m.channel, e.target.checked)}
              >
                {m.label}
                {m.fallback && (
                  <span style={{ marginLeft: 4 }}>
                    <CountChip>保底</CountChip>
                  </span>
                )}
              </Checkbox>
            ))}
          </Space>
          <Segmented
            value={activeChannel}
            onChange={(v) => setActiveChannel(String(v))}
            options={orderedChannels.map((c) => ({
              label: channelMeta?.find((m) => m.channel === c)?.label ?? c,
              value: c,
            }))}
            style={{ marginBottom: 10 }}
          />
          {editor}
        </Form.Item>
      </Form>
    </Modal>
  );
}

function VarsEditor({
  value,
  disabled,
  onChange,
}: {
  value: NotificationVarDef[];
  disabled: boolean;
  onChange: (next: NotificationVarDef[]) => void;
}) {
  const update = (idx: number, patch: Partial<NotificationVarDef>) => {
    onChange(value.map((v, i) => (i === idx ? { ...v, ...patch } : v)));
  };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      {value.map((v, idx) => (
        <Space key={idx} style={{ width: '100%' }}>
          <Input
            placeholder="变量名"
            value={v.name}
            disabled={disabled}
            style={{ width: 160, fontFamily: 'var(--font-mono)' }}
            onChange={(e) => update(idx, { name: e.target.value })}
          />
          <Input
            placeholder="示例值"
            value={v.sampleValue ?? ''}
            disabled={disabled}
            style={{ width: 200 }}
            onChange={(e) => update(idx, { sampleValue: e.target.value })}
          />
          <Checkbox
            checked={v.required}
            disabled={disabled}
            onChange={(e) => update(idx, { required: e.target.checked })}
          >
            必填
          </Checkbox>
          <Button
            size="small"
            type="text"
            danger
            disabled={disabled}
            onClick={() => onChange(value.filter((_, i) => i !== idx))}
          >
            删除
          </Button>
        </Space>
      ))}
      <Button
        size="small"
        type="dashed"
        disabled={disabled}
        onClick={() => onChange([...value, { name: '', sampleValue: '', required: false }])}
        style={{ alignSelf: 'flex-start' }}
      >
        + 添加变量
      </Button>
    </div>
  );
}
