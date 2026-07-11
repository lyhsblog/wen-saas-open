import { useEffect } from 'react';
import { App, DatePicker, Form, Input, InputNumber, Modal, Select, Switch } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  describeApiError,
  type ApiKeyCreated,
  type ApiKeyUpsertRequest,
  type ApiKeyView,
} from '@wen-saas/api-client';
import { useCreateApiKey, useUpdateApiKey } from './apiKeyQueries';

interface FormValues {
  name: string;
  expiresAt?: Dayjs;
  ipWhitelist?: string[];
  rateLimitQps?: number;
  requireSignature: boolean;
}

/** 签发 / 编辑凭证。IP 白名单 tags 自由录入（单 IP 或 CIDR，仅 IPv4）；留空=不限来源 IP。 */
export function ApiKeyFormModal({
  open,
  editing,
  onClose,
  onCreated,
}: {
  open: boolean;
  editing: ApiKeyView | null;
  onClose: () => void;
  onCreated: (created: ApiKeyCreated) => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const isEdit = !!editing;
  const create = useCreateApiKey();
  const update = useUpdateApiKey();

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue({
        name: editing.name,
        ...(editing.expiresAt ? { expiresAt: dayjs(editing.expiresAt) } : {}),
        ipWhitelist: editing.ipWhitelist,
        ...(editing.rateLimitQps != null ? { rateLimitQps: editing.rateLimitQps } : {}),
        requireSignature: editing.requireSignature,
      });
    } else {
      form.resetFields();
    }
  }, [open, editing, form]);

  const submit = async () => {
    const v = await form.validateFields();
    const body: ApiKeyUpsertRequest = { name: v.name.trim(), requireSignature: v.requireSignature };
    if (v.expiresAt) body.expiresAt = v.expiresAt.format('YYYY-MM-DDTHH:mm:ss');
    if (v.ipWhitelist?.length) body.ipWhitelist = v.ipWhitelist;
    if (v.rateLimitQps != null) body.rateLimitQps = v.rateLimitQps;
    try {
      if (isEdit && editing) {
        await update.mutateAsync({ id: editing.id, body });
        message.success('已保存');
        onClose();
      } else {
        const created = await create.mutateAsync(body);
        message.success('凭证已签发');
        onClose();
        onCreated(created);
      }
    } catch (e) {
      message.error(describeApiError(e, '保存失败'));
    }
  };

  return (
    <Modal
      title={isEdit ? '编辑凭证' : '签发凭证'}
      open={open}
      onCancel={onClose}
      onOk={() => void submit()}
      okButtonProps={{ loading: create.isPending || update.isPending }}
      destroyOnHidden
      width={560}
    >
      <Form form={form} layout="vertical" initialValues={{ requireSignature: true }}>
        <Form.Item name="name" label="名称" rules={[{ required: true, message: '名称必填' }]}>
          <Input maxLength={100} placeholder="如：ERP 对接" />
        </Form.Item>
        <Form.Item name="expiresAt" label="有效期至" extra="留空=长期有效">
          <DatePicker showTime style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="ipWhitelist"
          label="IP 白名单"
          extra="单 IP 或 CIDR（仅 IPv4，如 1.2.3.4、10.0.0.0/8），回车添加；留空=不限来源"
        >
          <Select mode="tags" open={false} tokenSeparators={[',', ' ']} placeholder="输入后回车" />
        </Form.Item>
        <Form.Item
          name="rateLimitQps"
          label="凭证级限流"
          extra="每窗口（默认 60s）允许请求数；留空=仅受租户级限流"
        >
          <InputNumber min={1} style={{ width: 200 }} placeholder="如 100" />
        </Form.Item>
        <Form.Item
          name="requireSignature"
          label="强制 HMAC 验签"
          valuePropName="checked"
          extra="关闭后调用方可不带签名头（仅凭 API Key），安全性降低，仅建议内网低敏场景"
        >
          <Switch />
        </Form.Item>
      </Form>
    </Modal>
  );
}
