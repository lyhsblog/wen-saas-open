import { App, Form, Input, Modal, Select } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { applyApiErrorToForm, describeApiError } from '@wen-saas/api-client';
import type { TicketDetailView, TicketPriority, TicketType } from '@wen-saas/api-client';
import { PRIORITY_META, TYPE_LABEL } from './utils/ticketMeta';
import { useSubmitTicket } from './ticketQueries';

const schema = z.object({
  title: z.string().min(1, '请输入标题').max(120, '标题最多 120 字符'),
  type: z.enum(['BUG', 'CONSULT', 'SUGGESTION', 'COMPLAINT'] as const, { message: '请选择类型' }),
  priority: z.enum(['LOW', 'NORMAL', 'HIGH', 'URGENT'] as const, { message: '请选择优先级' }),
  content: z.string().min(1, '请描述你遇到的问题').max(5000, '内容最多 5000 字符'),
});

type FormValues = z.infer<typeof schema>;

const EMPTY: FormValues = { title: '', type: 'BUG', priority: 'NORMAL', content: '' };

// 文案单源自 ticketMeta（→ @wen-saas/api-client）；此处仅定提单表单的展示顺序与「（SLA 内响应）」格式。
const TYPE_OPTIONS = (Object.keys(TYPE_LABEL) as TicketType[]).map((value) => ({
  value,
  label: TYPE_LABEL[value],
}));

const PRIORITY_OPTIONS = (['URGENT', 'HIGH', 'NORMAL', 'LOW'] as TicketPriority[]).map((value) => ({
  value,
  label: `${PRIORITY_META[value].label}（${PRIORITY_META[value].sla}内响应）`,
}));

type Props = {
  open: boolean;
  onCancel: () => void;
  onSubmitted: (created: TicketDetailView) => void;
};

export function SubmitTicketModal({ open, onCancel, onSubmitted }: Props) {
  const { message } = App.useApp();
  const submit = useSubmitTicket();
  const {
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: EMPTY });

  const close = () => {
    reset(EMPTY);
    onCancel();
  };

  const onOk = handleSubmit(async (values) => {
    try {
      const created = await submit.mutateAsync(values);
      void message.success('工单已提交');
      reset(EMPTY);
      onSubmitted(created);
    } catch (err) {
      const consumed = applyApiErrorToForm(err, (name, e) => setError(name as keyof FormValues, e));
      if (!consumed) void message.error(describeApiError(err, '提交失败'));
    }
  });

  return (
    <Modal
      title="提交工单"
      open={open}
      onCancel={close}
      onOk={() => void onOk()}
      okText="提交"
      cancelText="取消"
      okButtonProps={{ loading: isSubmitting }}
      destroyOnHidden
    >
      <Form layout="vertical">
        <Controller
          control={control}
          name="title"
          render={({ field }) => (
            <Form.Item
              label="标题"
              required
              {...(errors.title ? { validateStatus: 'error', help: errors.title.message } : {})}
            >
              <Input {...field} placeholder="一句话描述问题" maxLength={120} />
            </Form.Item>
          )}
        />
        <div style={{ display: 'flex', gap: 12 }}>
          <Controller
            control={control}
            name="type"
            render={({ field }) => (
              <Form.Item
                label="类型"
                required
                style={{ flex: 1 }}
                {...(errors.type ? { validateStatus: 'error', help: errors.type.message } : {})}
              >
                <Select {...field} options={TYPE_OPTIONS} />
              </Form.Item>
            )}
          />
          <Controller
            control={control}
            name="priority"
            render={({ field }) => (
              <Form.Item
                label="优先级"
                required
                style={{ flex: 1 }}
                {...(errors.priority
                  ? { validateStatus: 'error', help: errors.priority.message }
                  : {})}
              >
                <Select {...field} options={PRIORITY_OPTIONS} />
              </Form.Item>
            )}
          />
        </div>
        <Controller
          control={control}
          name="content"
          render={({ field }) => (
            <Form.Item
              label="问题描述"
              required
              {...(errors.content ? { validateStatus: 'error', help: errors.content.message } : {})}
            >
              <Input.TextArea
                {...field}
                placeholder="详细描述问题现象、复现步骤、期望结果等"
                autoSize={{ minRows: 4, maxRows: 8 }}
                maxLength={5000}
              />
            </Form.Item>
          )}
        />
      </Form>
    </Modal>
  );
}
