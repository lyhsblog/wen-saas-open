import { useEffect, useMemo } from 'react';
import { App, Form, Input, InputNumber, Modal, Select } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import type { DictGroup, DictItem, DictItemCreateRequest } from '@wen-saas/domain';
import { applyApiErrorToForm, describeApiError } from '@wen-saas/api-client';

type ParentOption = { id: number; code: string; name: string };

function flattenForParent(items: DictItem[], depth: number, acc: ParentOption[]): ParentOption[] {
  for (const it of items) {
    if (it.id != null && it.code) {
      acc.push({ id: it.id, code: it.code, name: `${'　'.repeat(depth)}${it.name ?? it.code}` });
    }
    if (it.children && it.children.length > 0) flattenForParent(it.children, depth + 1, acc);
  }
  return acc;
}

const schema = z.object({
  code: z
    .string()
    .min(1, '请输入编码')
    .regex(/^[A-Za-z0-9_\-.]+$/, '仅允许字母 / 数字 / _ / - / .'),
  name: z.string().min(1, '请输入名称'),
  parentId: z.number().nullable(),
  sortOrder: z.number().nullable(),
  remark: z.string().nullable(),
});
type FormValues = z.infer<typeof schema>;

const EMPTY: FormValues = {
  code: '',
  name: '',
  parentId: null,
  sortOrder: 0,
  remark: null,
};

type Props = {
  open: boolean;
  group: DictGroup;
  editing: DictItem | null;
  itemsTree: DictItem[];
  onCancel: () => void;
  onSubmit: (body: DictItemCreateRequest) => Promise<void>;
};

export function DictItemFormModal({ open, group, editing, itemsTree, onCancel, onSubmit }: Props) {
  const { message } = App.useApp();
  const isEdit = editing != null;
  const parentOptions = useMemo(() => flattenForParent(itemsTree, 0, []), [itemsTree]);

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
        parentId: null,
        sortOrder: editing.sortOrder ?? 0,
        remark: editing.remark ?? null,
      });
    } else {
      reset(EMPTY);
    }
  }, [open, editing, reset]);

  const submit = handleSubmit(async (values) => {
    const body: DictItemCreateRequest = {
      code: values.code,
      name: values.name,
      parentId: values.parentId != null ? String(values.parentId) : null,
      sortOrder: values.sortOrder ?? null,
      remark: values.remark ?? null,
    };
    try {
      await onSubmit(body);
    } catch (err) {
      const handled = applyApiErrorToForm(err, (name, e) => setError(name as keyof FormValues, e));
      if (!handled) message.error(describeApiError(err, '保存失败'));
    }
  });

  return (
    <Modal
      open={open}
      title={isEdit ? `编辑字典项 — ${editing.name}` : `新增字典项 — ${group.name}`}
      okText="保存"
      cancelText="取消"
      confirmLoading={isSubmitting}
      onCancel={onCancel}
      onOk={() => {
        void submit();
      }}
      destroyOnHidden
      width={520}
    >
      <Form layout="vertical" requiredMark component="div">
        <Controller
          control={control}
          name="code"
          render={({ field, fieldState }) => (
            <Form.Item
              label="编码"
              required
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                placeholder="分组内唯一"
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
              label="名称"
              required
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
            </Form.Item>
          )}
        />
        {group.supportHierarchy && !isEdit && (
          <Controller
            control={control}
            name="parentId"
            render={({ field, fieldState }) => (
              <Form.Item
                label="上级"
                validateStatus={fieldState.error ? 'error' : ''}
                help={fieldState.error?.message}
              >
                <Select
                  allowClear
                  placeholder="不选则为一级"
                  value={field.value ?? undefined}
                  onChange={(v) => field.onChange(v ?? null)}
                  onBlur={field.onBlur}
                  options={parentOptions.map((p) => ({
                    label: `${p.name} (${p.code})`,
                    value: p.id,
                  }))}
                />
              </Form.Item>
            )}
          />
        )}
        <Controller
          control={control}
          name="sortOrder"
          render={({ field, fieldState }) => (
            <Form.Item
              label="排序权重"
              extra="数字越小越靠前"
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <InputNumber
                style={{ width: '100%' }}
                min={0}
                max={9999}
                value={field.value}
                onChange={(v) => field.onChange(typeof v === 'number' ? v : null)}
                onBlur={field.onBlur}
              />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="remark"
          render={({ field, fieldState }) => (
            <Form.Item
              label="备注"
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input.TextArea
                rows={2}
                placeholder="可选"
                value={field.value ?? ''}
                onChange={(e) => field.onChange(e.target.value || null)}
                onBlur={field.onBlur}
              />
            </Form.Item>
          )}
        />
      </Form>
    </Modal>
  );
}
