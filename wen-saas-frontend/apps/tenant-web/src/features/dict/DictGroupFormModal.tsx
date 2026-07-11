import { useEffect } from 'react';
import { App, Form, Input, Modal, Switch } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import type { DictGroup, DictGroupCreateRequest } from '@wen-saas/domain';
import { applyApiErrorToForm, describeApiError } from '@wen-saas/api-client';

const schema = z.object({
  code: z
    .string()
    .min(1, '请输入编码')
    .regex(/^[A-Za-z][A-Za-z0-9_]*$/, '需以字母开头，仅含字母数字下划线'),
  name: z.string().min(1, '请输入分组名称'),
  description: z.string().nullable(),
  supportHierarchy: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

const EMPTY: FormValues = {
  code: '',
  name: '',
  description: null,
  supportHierarchy: false,
};

type Props = {
  open: boolean;
  editing: DictGroup | null;
  onCancel: () => void;
  /**
   * 提交回调。422 字段错误由 modal 自行映射到表单；非 422 的错误请向上抛出，
   * modal 会用 describeApiError 在 modal 内 toast。
   */
  onSubmit: (body: DictGroupCreateRequest) => Promise<void>;
};

export function DictGroupFormModal({ open, editing, onCancel, onSubmit }: Props) {
  const { message } = App.useApp();
  const isEdit = editing != null;
  const {
    control,
    handleSubmit,
    reset,
    setError,
    formState: { isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: EMPTY,
  });

  useEffect(() => {
    if (!open) return;
    if (editing) {
      reset({
        code: editing.code ?? '',
        name: editing.name ?? '',
        description: editing.description ?? null,
        supportHierarchy: editing.supportHierarchy ?? false,
      });
    } else {
      reset(EMPTY);
    }
  }, [open, editing, reset]);

  const submit = handleSubmit(async (values) => {
    try {
      await onSubmit({
        code: values.code,
        name: values.name,
        description: values.description,
        supportHierarchy: values.supportHierarchy,
      });
    } catch (err) {
      const handled = applyApiErrorToForm(err, (name, e) => setError(name as keyof FormValues, e));
      if (!handled) message.error(describeApiError(err, '保存失败'));
    }
  });

  return (
    <Modal
      open={open}
      title={isEdit ? `编辑字典分组 — ${editing.name}` : '新建字典分组'}
      okText="保存"
      cancelText="取消"
      confirmLoading={isSubmitting}
      onCancel={onCancel}
      onOk={() => {
        void submit();
      }}
      destroyOnHidden
      width={560}
    >
      <Form layout="vertical" requiredMark component="div">
        <Controller
          control={control}
          name="code"
          render={({ field, fieldState }) => (
            <Form.Item
              label="分组编码"
              required
              extra="创建后不可改 · 字母 + 数字 + 下划线"
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                placeholder="例如 PROJECT_STAGE"
                disabled={isEdit}
              />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="name"
          render={({ field, fieldState }) => (
            <Form.Item
              label="分组名称"
              required
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                placeholder="例如 项目阶段"
              />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="description"
          render={({ field, fieldState }) => (
            <Form.Item
              label="描述"
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input.TextArea
                value={field.value ?? ''}
                onChange={(e) => field.onChange(e.target.value || null)}
                onBlur={field.onBlur}
                placeholder="可选"
                rows={2}
              />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="supportHierarchy"
          render={({ field, fieldState }) => (
            <Form.Item
              label="支持层级"
              extra="创建后不可改；树形最大 5 层"
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Switch checked={field.value} onChange={field.onChange} disabled={isEdit} />
            </Form.Item>
          )}
        />
      </Form>
    </Modal>
  );
}
