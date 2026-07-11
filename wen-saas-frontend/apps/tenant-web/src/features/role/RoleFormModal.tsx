import { useEffect } from 'react';
import { App, Form, Input, Modal, Select } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import type { RoleDetail, RoleCreateRequest, RoleUpdateRequest } from '@wen-saas/domain';
import {
  applyApiErrorToForm,
  describeApiError,
  TENANT_DATA_SCOPE_LABEL,
  labelMapToOptions,
} from '@wen-saas/api-client';

// 文案单源自 @wen-saas/api-client；下方 zod enum 仅声明校验值域。
const DATA_SCOPE_OPTIONS = labelMapToOptions(TENANT_DATA_SCOPE_LABEL);

const baseSchema = z.object({
  code: z.string().optional(),
  name: z.string().min(1, '请输入角色名称').max(64, '名称最多 64 字符'),
  description: z.string().max(255, '描述最多 255 字符').nullable(),
  // 四个范围与 TENANT_DATA_SCOPE_LABEL 一一对应（下拉与数据权限 Tab 均可取 DEPT_ONLY，值域须对齐）
  dataScope: z.enum(['ALL', 'DEPT_AND_BELOW', 'DEPT_ONLY', 'SELF'] as const, {
    message: '请选择数据范围',
  }),
});

// Create schema adds superRefine to validate code field
const createSchema = baseSchema.superRefine((data, ctx) => {
  if (!data.code || data.code.trim().length === 0) {
    ctx.addIssue({ code: 'custom', path: ['code'], message: '请输入角色编码' });
  } else if (!/^[A-Z][A-Z0-9_]*$/.test(data.code)) {
    ctx.addIssue({
      code: 'custom',
      path: ['code'],
      message: '需以大写字母开头，仅含大写字母数字下划线',
    });
  }
});

type FormValues = z.infer<typeof baseSchema>;

const EMPTY_CREATE: FormValues = {
  code: '',
  name: '',
  description: null,
  dataScope: 'SELF',
};

type Props = {
  open: boolean;
  editing: RoleDetail | null;
  onCancel: () => void;
  onSubmit: (body: RoleCreateRequest | RoleUpdateRequest) => Promise<void>;
};

export function RoleFormModal({ open, editing, onCancel, onSubmit }: Props) {
  const { message } = App.useApp();
  const isEdit = editing != null;

  const {
    control,
    handleSubmit,
    reset,
    setError,
    formState: { isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(isEdit ? baseSchema : createSchema),
    defaultValues: EMPTY_CREATE,
  });

  useEffect(() => {
    if (!open) return;
    if (editing) {
      reset({
        name: editing.name ?? '',
        description: editing.description ?? null,
        dataScope: (editing.dataScope as FormValues['dataScope']) ?? 'SELF',
      });
    } else {
      reset(EMPTY_CREATE);
    }
  }, [open, editing, reset]);

  const submit = handleSubmit(async (values) => {
    try {
      if (isEdit) {
        const body: RoleUpdateRequest = {
          name: values.name,
          description: values.description,
          dataScope: values.dataScope,
        };
        await onSubmit(body);
      } else {
        const body: RoleCreateRequest = {
          code: values.code ?? '',
          name: values.name,
          description: values.description,
          dataScope: values.dataScope,
        };
        await onSubmit(body);
      }
    } catch (err) {
      const handled = applyApiErrorToForm(err, (name, e) => setError(name as keyof FormValues, e));
      if (!handled) message.error(describeApiError(err, '保存失败'));
    }
  });

  return (
    <Modal
      open={open}
      title={isEdit ? `编辑角色 — ${editing?.name ?? ''}` : '新建角色'}
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
        {!isEdit && (
          <Controller
            control={control}
            name="code"
            render={({ field, fieldState }) => (
              <Form.Item
                label="角色编码"
                required
                extra="创建后不可修改 · 大写字母 + 数字 + 下划线"
                validateStatus={fieldState.error ? 'error' : ''}
                help={fieldState.error?.message}
              >
                <Input
                  value={field.value ?? ''}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  placeholder="例如 SALES_MANAGER"
                />
              </Form.Item>
            )}
          />
        )}
        {isEdit && (
          <Form.Item label="角色编码" extra="创建后不可修改">
            <Input
              value={editing?.code}
              disabled
              style={{ fontFamily: 'var(--font-mono, monospace)' }}
            />
          </Form.Item>
        )}
        <Controller
          control={control}
          name="name"
          render={({ field, fieldState }) => (
            <Form.Item
              label="角色名称"
              required
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                placeholder="例如 销售经理"
              />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="dataScope"
          render={({ field, fieldState }) => (
            <Form.Item
              label="默认数据范围"
              required
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Select
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                options={DATA_SCOPE_OPTIONS}
                placeholder="请选择数据范围"
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
      </Form>
    </Modal>
  );
}
