import { useEffect, useMemo } from 'react';
import { App, Form, Input, InputNumber, Modal, TreeSelect } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import type { DeptView, DeptCreateRequest, DeptUpdateRequest } from '@wen-saas/domain';
import { applyApiErrorToForm, describeApiError } from '@wen-saas/api-client';

const schema = z.object({
  name: z.string().min(1, '请输入部门名称').max(64, '名称最多 64 字符'),
  parentId: z.string().nullable(),
  sortOrder: z.number().int().min(0).max(9999).nullable(),
});

type FormValues = z.infer<typeof schema>;

const EMPTY_CREATE: FormValues = { name: '', parentId: null, sortOrder: null };

/** 递归构建树节点供 TreeSelect 使用 */
type TreeSelectNode = { value: string; title: string; children?: TreeSelectNode[] };

function buildTreeSelectData(nodes: DeptView[], excludeId?: number): TreeSelectNode[] {
  return nodes
    .filter((n) => n.id !== excludeId)
    .map((n) => {
      const kids = n.children?.length ? buildTreeSelectData(n.children, excludeId) : undefined;
      return {
        value: String(n.id),
        title: n.name ?? '',
        ...(kids ? { children: kids } : {}),
      };
    });
}

type Props = {
  open: boolean;
  editing: DeptView | null;
  treeData: DeptView[];
  defaultParentId: string | null;
  onCancel: () => void;
  /**
   * 提交。第二参 parentId 为表单选中的上级（null=一级）：
   * 新建即建于该上级下；编辑时由调用方对比原上级决定是否触发「移动」
   * （移动 = 拖拽 或 编辑上级字段）。
   */
  onSubmit: (body: DeptCreateRequest | DeptUpdateRequest, parentId: string | null) => Promise<void>;
};

export function DeptFormModal({
  open,
  editing,
  treeData,
  defaultParentId,
  onCancel,
  onSubmit,
}: Props) {
  const { message } = App.useApp();
  const isEdit = editing != null;
  // 根节点（无上级）不可改上级：根不能被移动到他人之下
  const isRootEdit = isEdit && !editing?.parentId;

  const treeSelectData = useMemo(
    () => buildTreeSelectData(treeData, editing?.id ?? undefined),
    [treeData, editing],
  );

  const {
    control,
    handleSubmit,
    reset,
    setError,
    formState: { isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: EMPTY_CREATE,
  });

  useEffect(() => {
    if (!open) return;
    if (editing) {
      // parentId 为 "0"/0/空 均表示一级部门（雪花 id 序列化为字符串，根哨兵=0）→ 归一为 null 显示「无」
      const pid = editing.parentId ? String(editing.parentId) : null;
      reset({
        name: editing.name ?? '',
        parentId: pid === '0' ? null : pid,
        sortOrder: editing.sortOrder ?? null,
      });
    } else {
      reset({ ...EMPTY_CREATE, parentId: defaultParentId });
    }
  }, [open, editing, defaultParentId, reset]);

  const submit = handleSubmit(async (values) => {
    try {
      if (isEdit) {
        const body: DeptUpdateRequest = {
          name: values.name,
          sortOrder: values.sortOrder,
        };
        await onSubmit(body, values.parentId ?? null);
      } else {
        const body: DeptCreateRequest = {
          name: values.name,
          parentId: values.parentId ?? null,
          sortOrder: values.sortOrder,
        };
        await onSubmit(body, values.parentId ?? null);
      }
    } catch (err) {
      const handled = applyApiErrorToForm(err, (name, e) => setError(name as keyof FormValues, e));
      if (!handled) message.error(describeApiError(err, '保存失败'));
    }
  });

  return (
    <Modal
      open={open}
      title={isEdit ? `编辑部门 — ${editing?.name ?? ''}` : '新建部门'}
      okText="保存"
      cancelText="取消"
      confirmLoading={isSubmitting}
      onCancel={onCancel}
      onOk={() => {
        void submit();
      }}
      destroyOnHidden
      width={480}
    >
      <Form layout="vertical" requiredMark component="div">
        <Controller
          control={control}
          name="name"
          render={({ field, fieldState }) => (
            <Form.Item
              label="部门名称"
              required
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                placeholder="例如 研发部"
              />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="parentId"
          render={({ field, fieldState }) => (
            <Form.Item
              label="上级部门"
              extra={
                isRootEdit
                  ? '根部门不可调整上级'
                  : isEdit
                    ? '改变上级即移动该部门，保存时二次确认'
                    : '留空表示一级部门'
              }
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <TreeSelect
                {...(field.value ? { value: field.value } : {})}
                onChange={(v) => field.onChange(v ?? null)}
                treeData={treeSelectData}
                placeholder="无（一级部门）"
                allowClear
                treeDefaultExpandAll
                disabled={isRootEdit}
                style={{ width: '100%' }}
              />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="sortOrder"
          render={({ field, fieldState }) => (
            <Form.Item
              label="排序权重"
              extra="数字小的排在前面"
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <InputNumber
                value={field.value ?? null}
                onChange={(v) => field.onChange(v ?? null)}
                onBlur={field.onBlur}
                placeholder="可选"
                min={0}
                max={9999}
                style={{ width: '100%' }}
              />
            </Form.Item>
          )}
        />
      </Form>
    </Modal>
  );
}
