import { useEffect } from 'react';
import { App, Form, Input, Modal, Select } from 'antd';
import {
  describeApiError,
  type WebhookCreated,
  type WebhookUpsertRequest,
  type WebhookView,
} from '@wen-saas/api-client';
import { useCreateWebhook, useUpdateWebhook } from './webhookQueries';

interface FormValues {
  name: string;
  targetUrl: string;
  subscribedEvents: string[];
}

/**
 * 内建事件目录：下拉默认全量展示、可直接勾选。事件本身无白名单（任何经
 * DomainEventPublisher 发出的 code 都可订阅），故仅作快捷选项，自定义 code 仍可自由输入。
 */
const BUILTIN_EVENT_OPTIONS = [
  { value: 'sys.member_disabled', label: '成员被停用（sys.member_disabled）' },
  { value: 'crm_demo.customer_created', label: '客户创建 Demo（crm_demo.customer_created）' },
  { value: 'crm_demo.customer_updated', label: '客户更新 Demo（crm_demo.customer_updated）' },
  { value: 'crm_demo.customer_deleted', label: '客户删除 Demo（crm_demo.customer_deleted）' },
  {
    value: 'crm_demo.customer_stage_changed',
    label: '客户阶段变更 Demo（crm_demo.customer_stage_changed）',
  },
];

/**
 * 新建 / 编辑订阅。订阅事件 tags 模式：下拉预置内建事件目录可勾选，
 * 也可输入任意事件 code 回车添加（无需预注册，精确匹配）。
 */
export function WebhookFormModal({
  open,
  editing,
  onClose,
  onCreated,
}: {
  open: boolean;
  editing: WebhookView | null;
  onClose: () => void;
  /** 创建成功后回调（携带一次性明文密钥，由父组件弹出密钥框）。 */
  onCreated: (created: WebhookCreated) => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const isEdit = !!editing;
  const create = useCreateWebhook();
  const update = useUpdateWebhook();

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue({
        name: editing.name,
        targetUrl: editing.targetUrl,
        subscribedEvents: editing.subscribedEvents,
      });
    } else {
      form.resetFields();
    }
  }, [open, editing, form]);

  const submit = async () => {
    const v = await form.validateFields();
    const body: WebhookUpsertRequest = {
      name: v.name.trim(),
      targetUrl: v.targetUrl.trim(),
      subscribedEvents: v.subscribedEvents,
    };
    try {
      if (isEdit && editing) {
        await update.mutateAsync({ id: editing.id, body });
        message.success('已保存');
        onClose();
      } else {
        const created = await create.mutateAsync(body);
        message.success('订阅已创建');
        onClose();
        onCreated(created);
      }
    } catch (e) {
      message.error(describeApiError(e, '保存失败'));
    }
  };

  return (
    <Modal
      title={isEdit ? '编辑订阅' : '新建订阅'}
      open={open}
      onCancel={onClose}
      onOk={() => void submit()}
      okButtonProps={{ loading: create.isPending || update.isPending }}
      destroyOnHidden
      width={560}
    >
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="名称" rules={[{ required: true, message: '名称必填' }]}>
          <Input maxLength={100} placeholder="如：ERP 客户同步" />
        </Form.Item>
        <Form.Item
          name="targetUrl"
          label="回调地址"
          rules={[{ required: true, message: '回调地址必填' }]}
          extra="仅 http(s)；生产环境请使用 HTTPS。私网/环回地址默认拒绝"
        >
          <Input maxLength={500} placeholder="https://example.com/webhooks/wen-saas" />
        </Form.Item>
        <Form.Item
          name="subscribedEvents"
          label="订阅事件"
          rules={[{ required: true, message: '至少订阅一个事件' }]}
          extra="下拉勾选内建事件，或输入自定义事件 code 回车添加（精确匹配，无通配）"
        >
          <Select
            mode="tags"
            options={BUILTIN_EVENT_OPTIONS}
            tokenSeparators={[',', ' ']}
            placeholder="勾选内建事件或输入事件 code"
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
