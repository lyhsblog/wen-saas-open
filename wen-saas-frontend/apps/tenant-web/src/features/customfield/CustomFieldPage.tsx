import { useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Typography,
} from 'antd';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { CountChip, RowTextAction } from '@wen-saas/shared-ui';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/lib/table';
import {
  applyApiErrorToForm,
  describeApiError,
  type CreateFieldRequest,
  type CustomFieldDef,
  type CustomFieldTypeCode,
} from '@wen-saas/api-client';
import {
  useCfFields,
  useCfObjects,
  useCfQuota,
  useCreateField,
  useDeleteField,
  useDictGroups,
  useRefObjectOptions,
  useRenameField,
  useToggleIndex,
} from './customFieldQueries';

const TYPE_OPTIONS: { value: CustomFieldTypeCode; label: string }[] = [
  { value: 'text', label: '文本' },
  { value: 'long_text', label: '长文本' },
  { value: 'number', label: '数字' },
  { value: 'date', label: '日期' },
  { value: 'bool', label: '布尔' },
  { value: 'enum', label: '枚举（关联字典）' },
  { value: 'ref', label: '关联（关联对象）' },
];
const typeLabel = (t: string) => TYPE_OPTIONS.find((o) => o.value === t.toLowerCase())?.label ?? t;

export function CustomFieldPage() {
  const { message, modal } = App.useApp();
  const objectsQuery = useCfObjects();
  const objects = objectsQuery.data ?? [];
  const refObjectsQuery = useRefObjectOptions();
  const refObjects = (refObjectsQuery.data ?? []).map((t) => ({ value: t.code, label: t.label }));
  const dictGroupsQuery = useDictGroups();
  const dictGroups = (dictGroupsQuery.data ?? [])
    .filter((g): g is typeof g & { code: string } => !!g.code)
    .map((g) => ({
      value: g.code,
      label: g.name ? `${g.name}（${g.code}）` : g.code,
    }));
  const [objectCode, setObjectCode] = useState<string | null>(null);
  const effectiveObject = objectCode ?? objects[0]?.code ?? null;

  const quotaQuery = useCfQuota();
  const quota = quotaQuery.data;
  const fieldsQuery = useCfFields(effectiveObject);
  const fields = fieldsQuery.data ?? [];

  const createField = useCreateField();
  const renameField = useRenameField(effectiveObject ?? '');
  const toggleIndex = useToggleIndex(effectiveObject ?? '');
  const deleteField = useDeleteField(effectiveObject ?? '');

  const [createOpen, setCreateOpen] = useState(false);
  const [renameTarget, setRenameTarget] = useState<CustomFieldDef | null>(null);

  const onToggleIndex = (d: CustomFieldDef) => {
    modal.confirm({
      title: d.indexed ? '取消索引化？' : '启用索引化？',
      content: '该操作会创建/删除表达式索引，可能短暂影响写入性能，建议低峰期执行。',
      onOk: async () => {
        try {
          await toggleIndex.mutateAsync({ id: d.id, indexed: !d.indexed });
          message.success('已更新索引设置');
        } catch (e) {
          message.error(describeApiError(e, '索引切换失败'));
        }
      },
    });
  };

  const onRename = (d: CustomFieldDef) => setRenameTarget(d);

  const onDelete = (d: CustomFieldDef) => {
    modal.confirm({
      title: `删除字段「${d.fieldName}」？`,
      content: '软删 + 释放索引；业务数据中的值保留，90 天后清理。',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteField.mutateAsync(d.id);
          message.success('已删除');
        } catch (e) {
          message.error(describeApiError(e, '删除失败'));
        }
      },
    });
  };

  const columns: ColumnsType<CustomFieldDef> = useMemo(
    () => [
      { title: '字段编码', dataIndex: 'fieldCode', key: 'fieldCode' },
      { title: '字段名', dataIndex: 'fieldName', key: 'fieldName' },
      { title: '类型', key: 'type', render: (_v, d) => typeLabel(d.type) },
      {
        title: '索引',
        key: 'indexed',
        render: (_v, d) =>
          d.indexed ? <CountChip tone="brand">已索引</CountChip> : <CountChip>未索引</CountChip>,
      },
      {
        title: '关联',
        key: 'ref',
        render: (_v, d) => d.refDictCode ?? d.refObjectCode ?? '-',
      },
      {
        title: '操作',
        key: 'actions',
        render: (_v, d) => (
          <Space>
            <RowTextAction onClick={() => onRename(d)}>改名</RowTextAction>
            <RowTextAction onClick={() => onToggleIndex(d)}>
              {d.indexed ? '取消索引' : '索引化'}
            </RowTextAction>
            <RowTextAction danger onClick={() => onDelete(d)}>
              删除
            </RowTextAction>
          </Space>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          字段管理
        </Typography.Title>
        <Select
          style={{ minWidth: 220 }}
          placeholder="选择业务对象"
          {...(effectiveObject ? { value: effectiveObject } : {})}
          onChange={(v: string) => setObjectCode(v)}
          options={objects.map((o) => ({ value: o.code, label: o.name }))}
        />
        {quota && (
          <Typography.Text type="secondary">
            字段 {quota.fieldUsed}/{quota.fieldQuota} · 索引 {quota.indexUsed}/{quota.indexQuota}
          </Typography.Text>
        )}
        <div style={{ flex: 1 }} />
        <Button
          type="primary"
          icon={<PlusOutlined />}
          disabled={!effectiveObject}
          onClick={() => setCreateOpen(true)}
        >
          新增字段
        </Button>
      </div>

      <Table<CustomFieldDef>
        rowKey="id"
        columns={columns}
        dataSource={fields}
        loading={fieldsQuery.isLoading}
        pagination={false}
        size="middle"
      />

      {effectiveObject && (
        <CreateFieldModal
          open={createOpen}
          objectCode={effectiveObject}
          refObjects={refObjects}
          dictGroups={dictGroups}
          dictGroupsLoading={dictGroupsQuery.isLoading}
          onCancel={() => setCreateOpen(false)}
          onSubmit={async (body) => {
            await createField.mutateAsync(body);
            message.success('字段已创建');
            setCreateOpen(false);
          }}
        />
      )}

      <RenameFieldModal
        target={renameTarget}
        onCancel={() => setRenameTarget(null)}
        onSubmit={async (name) => {
          if (!renameTarget) return;
          await renameField.mutateAsync({ id: renameTarget.id, name });
          message.success('已改名');
          setRenameTarget(null);
        }}
      />
    </div>
  );
}

const renameSchema = z.object({
  fieldName: z.string().trim().min(1, '必填').max(255, '最长 255 字符'),
});
type RenameValues = z.infer<typeof renameSchema>;

function RenameFieldModal({
  target,
  onCancel,
  onSubmit,
}: {
  target: CustomFieldDef | null;
  onCancel: () => void;
  /** 422 字段错误由 modal 映射到表单；非 422 错误在 modal 内 toast，成功提示与关闭由调用方负责。 */
  onSubmit: (name: string) => Promise<void>;
}) {
  const { message } = App.useApp();
  const {
    control,
    handleSubmit,
    reset,
    setError,
    formState: { isSubmitting },
  } = useForm<RenameValues>({
    resolver: zodResolver(renameSchema),
    defaultValues: { fieldName: '' },
  });

  useEffect(() => {
    if (target) reset({ fieldName: target.fieldName });
  }, [target, reset]);

  const submit = handleSubmit(async (values) => {
    try {
      await onSubmit(values.fieldName);
    } catch (err) {
      // 后端 rename 请求体字段为 name，回填到表单的 fieldName。
      const handled = applyApiErrorToForm(
        err,
        (name, e) => setError(name as keyof RenameValues, e),
        { fieldMap: { name: 'fieldName' } },
      );
      if (!handled) message.error(describeApiError(err, '改名失败'));
    }
  });

  return (
    <Modal
      title="改字段名"
      open={!!target}
      onCancel={onCancel}
      confirmLoading={isSubmitting}
      onOk={() => void submit()}
      destroyOnHidden
    >
      <Form layout="vertical" requiredMark component="div">
        <Controller
          control={control}
          name="fieldName"
          render={({ field, fieldState }) => (
            <Form.Item
              label="字段名"
              required
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input {...field} placeholder="客户来源" />
            </Form.Item>
          )}
        />
      </Form>
    </Modal>
  );
}

const createSchema = z
  .object({
    fieldCode: z
      .string()
      .min(1, '必填')
      .regex(/^cf_[a-z0-9_]*$/, '需 cf_ 前缀，小写字母/数字/下划线'),
    fieldName: z.string().min(1, '必填'),
    type: z.enum(['text', 'long_text', 'number', 'date', 'bool', 'enum', 'ref'] as const, {
      message: '必选',
    }),
    required: z.boolean(),
    indexed: z.boolean(),
    refDictCode: z.string(),
    refObjectCode: z.string(),
    displayOrder: z.number().min(0, '不能小于 0').nullable(),
  })
  .superRefine((v, ctx) => {
    if (v.type === 'enum' && !v.refDictCode) {
      ctx.addIssue({ code: 'custom', path: ['refDictCode'], message: '枚举需选字典分组' });
    }
    if (v.type === 'ref' && !v.refObjectCode) {
      ctx.addIssue({ code: 'custom', path: ['refObjectCode'], message: '关联需选对象' });
    }
  });
type CreateValues = z.infer<typeof createSchema>;

const CREATE_EMPTY: CreateValues = {
  fieldCode: '',
  fieldName: '',
  type: '' as CustomFieldTypeCode,
  required: false,
  indexed: false,
  refDictCode: '',
  refObjectCode: '',
  displayOrder: 0,
};

function CreateFieldModal({
  open,
  objectCode,
  refObjects,
  dictGroups,
  dictGroupsLoading,
  onCancel,
  onSubmit,
}: {
  open: boolean;
  objectCode: string;
  /** REF 字段可关联的对象（内建数据实体 成员 / 部门 + 已注册业务对象；不含角色）。 */
  refObjects: { value: string; label: string }[];
  /** ENUM 字段可关联的字典分组（code/name）。 */
  dictGroups: { value: string; label: string }[];
  dictGroupsLoading: boolean;
  onCancel: () => void;
  /** 422 字段错误由 modal 映射到表单；非 422 错误在 modal 内 toast，成功提示与关闭由调用方负责。 */
  onSubmit: (body: CreateFieldRequest) => Promise<void>;
}) {
  const { message } = App.useApp();
  const {
    control,
    handleSubmit,
    reset,
    setError,
    formState: { isSubmitting },
  } = useForm<CreateValues>({
    resolver: zodResolver(createSchema),
    defaultValues: CREATE_EMPTY,
  });
  const type = useWatch({ control, name: 'type' });

  useEffect(() => {
    if (open) reset(CREATE_EMPTY);
  }, [open, reset]);

  const submit = handleSubmit(async (values) => {
    try {
      // 关联字段仅在对应类型下提交（切换类型后残留的选择不带出）。
      await onSubmit({
        objectCode,
        fieldCode: values.fieldCode,
        fieldName: values.fieldName,
        type: values.type,
        required: values.required,
        indexed: values.indexed,
        ...(values.type === 'enum' ? { refDictCode: values.refDictCode } : {}),
        ...(values.type === 'ref' ? { refObjectCode: values.refObjectCode } : {}),
        ...(values.displayOrder != null ? { displayOrder: values.displayOrder } : {}),
      });
    } catch (err) {
      const handled = applyApiErrorToForm(err, (name, e) =>
        setError(name as keyof CreateValues, e),
      );
      if (!handled) message.error(describeApiError(err, '创建失败'));
    }
  });

  return (
    <Modal
      title="新增字段"
      open={open}
      onCancel={onCancel}
      confirmLoading={isSubmitting}
      onOk={() => void submit()}
      destroyOnHidden
    >
      <Form layout="vertical" requiredMark component="div">
        <Controller
          control={control}
          name="fieldCode"
          render={({ field, fieldState }) => (
            <Form.Item
              label="字段编码"
              required
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input {...field} placeholder="cf_source" />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="fieldName"
          render={({ field, fieldState }) => (
            <Form.Item
              label="字段名"
              required
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input {...field} placeholder="客户来源" />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="type"
          render={({ field, fieldState }) => (
            <Form.Item
              label="类型"
              required
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Select
                {...(field.value ? { value: field.value } : {})}
                onChange={field.onChange}
                onBlur={field.onBlur}
                options={TYPE_OPTIONS}
                placeholder="选择类型"
              />
            </Form.Item>
          )}
        />
        {type === 'enum' && (
          <Controller
            control={control}
            name="refDictCode"
            render={({ field, fieldState }) => (
              <Form.Item
                label="关联字典分组"
                required
                validateStatus={fieldState.error ? 'error' : ''}
                help={fieldState.error?.message}
              >
                <Select
                  {...(field.value ? { value: field.value } : {})}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  options={dictGroups}
                  placeholder="选择字典分组"
                  showSearch
                  optionFilterProp="label"
                  loading={dictGroupsLoading}
                />
              </Form.Item>
            )}
          />
        )}
        {type === 'ref' && (
          <Controller
            control={control}
            name="refObjectCode"
            render={({ field, fieldState }) => (
              <Form.Item
                label="关联对象"
                required
                validateStatus={fieldState.error ? 'error' : ''}
                help={fieldState.error?.message}
              >
                <Select
                  {...(field.value ? { value: field.value } : {})}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  options={refObjects}
                  placeholder="选择对象"
                  showSearch
                  optionFilterProp="label"
                />
              </Form.Item>
            )}
          />
        )}
        <Space size="large">
          <Controller
            control={control}
            name="required"
            render={({ field }) => (
              <Form.Item label="必填">
                <Switch checked={field.value} onChange={field.onChange} />
              </Form.Item>
            )}
          />
          <Controller
            control={control}
            name="indexed"
            render={({ field }) => (
              <Form.Item label="索引化">
                <Switch checked={field.value} onChange={field.onChange} />
              </Form.Item>
            )}
          />
          <Controller
            control={control}
            name="displayOrder"
            render={({ field, fieldState }) => (
              <Form.Item
                label="排序"
                validateStatus={fieldState.error ? 'error' : ''}
                help={fieldState.error?.message}
              >
                <InputNumber
                  min={0}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                />
              </Form.Item>
            )}
          />
        </Space>
      </Form>
    </Modal>
  );
}
