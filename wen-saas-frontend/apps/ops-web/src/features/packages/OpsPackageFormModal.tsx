import { useEffect } from 'react';
import { Alert, Button, Checkbox, Form, Input, Modal, Select, Space, Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { opsPackageAdminApi } from '@wen-saas/api-client';
import type { PackageDetail } from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { applyApiErrorToAntdForm } from '../tenants/utils/form';
import { useCreatePackage, useUpdatePackage } from './hooks/usePackageMutations';
import { TTL_OPTIONS, ttlFromFormValue, ttlToFormValue } from './utils/statusMeta';

type Props = {
  open: boolean;
  /** 传入 detail 为编辑模式；不传为新建模式。 */
  editing: PackageDetail | null;
  onClose: () => void;
  /** 新建成功后回调（用于选中新套餐）。 */
  onCreated?: (code: string) => void;
};

type FormData = {
  code: string;
  name: string;
  description: string;
  /** Select 值：0 = 永久（TTL_PERMANENT），其余为天数。提交时经 ttlFromFormValue 转回 null。 */
  defaultTtlDays: number;
  moduleCodes: string[];
};

const CODE_PATTERN = /^[A-Z][A-Z0-9_]{1,63}$/;

export function OpsPackageFormModal({ open, editing, onClose, onCreated }: Props) {
  const [form] = Form.useForm<FormData>();
  const isEdit = editing != null;
  const isSystem = editing?.isSystem === true;
  // 上架/下架态：禁止移除已含模块（服务端兜底，前端禁用取消勾选）。
  const lockRemoval =
    editing != null && (editing.status === 'ACTIVE' || editing.status === 'INACTIVE');
  const lockedModules = lockRemoval ? new Set(editing.moduleCodes) : new Set<string>();

  const { data: modules, isLoading: modulesLoading } = useQuery({
    queryKey: ['ops', 'package-modules'],
    queryFn: () => opsPackageAdminApi(http).listAvailableModules(),
    enabled: open,
  });

  const create = useCreatePackage();
  const update = useUpdatePackage(editing?.code ?? '');
  const isPending = create.isPending || update.isPending;

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue({
        code: editing.code,
        name: editing.name,
        description: editing.description ?? '',
        defaultTtlDays: ttlToFormValue(editing.defaultTtlDays),
        moduleCodes: editing.moduleCodes,
      });
    } else {
      form.setFieldsValue({
        code: '',
        name: '',
        description: '',
        defaultTtlDays: 365,
        moduleCodes: [],
      });
    }
  }, [open, editing, form]);

  const submit = async (publishImmediately: boolean) => {
    const values = await form.validateFields();
    try {
      if (isEdit) {
        await update.mutateAsync({
          name: values.name,
          description: values.description ? values.description : null,
          defaultTtlDays: ttlFromFormValue(values.defaultTtlDays),
          moduleCodes: values.moduleCodes,
        });
      } else {
        const detail = await create.mutateAsync({
          code: values.code,
          name: values.name,
          description: values.description ? values.description : null,
          defaultTtlDays: ttlFromFormValue(values.defaultTtlDays),
          moduleCodes: values.moduleCodes,
          publishImmediately,
        });
        onCreated?.(detail.code);
      }
      form.resetFields();
      onClose();
    } catch (err) {
      if (!applyApiErrorToAntdForm(err, form)) {
        // 非字段级错误已由 mutation onError toast
      }
    }
  };

  const footer = isEdit
    ? [
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button key="save" type="primary" loading={isPending} onClick={() => void submit(false)}>
          保存
        </Button>,
      ]
    : [
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button key="draft" loading={isPending} onClick={() => void submit(false)}>
          保存为草稿
        </Button>,
        <Button key="publish" type="primary" loading={isPending} onClick={() => void submit(true)}>
          保存并上架
        </Button>,
      ];

  return (
    <Modal
      open={open}
      title={isEdit ? `编辑套餐 · ${editing.name}` : '新建套餐包'}
      onCancel={onClose}
      footer={footer}
      mask={{ closable: false }}
      destroyOnHidden
      width={560}
    >
      {isSystem && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="基础包由底座自动决定包含的模块，运营仅可修改名称与描述，有效期与模块不可改。"
        />
      )}
      {lockRemoval && !isSystem && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="上架后编辑限制：可改名 / 改描述 / 加模块，但禁止移除已含模块、禁止改编码。如需减模块，请新建套餐替代旧包。"
        />
      )}
      <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
        <Form.Item
          name="name"
          label="套餐名称"
          rules={[{ required: true, message: '请输入套餐名称' }, { max: 128 }]}
        >
          <Input placeholder="面向客户展示的套餐名" disabled={isPending} />
        </Form.Item>
        <Form.Item
          name="code"
          label="套餐编码"
          extra={
            isEdit
              ? '编码创建后不可修改'
              : '大写字母开头，仅含大写字母 / 数字 / 下划线，创建后不可改'
          }
          rules={
            isEdit
              ? []
              : [
                  { required: true, message: '请输入套餐编码' },
                  {
                    pattern: CODE_PATTERN,
                    message: '大写字母开头，仅含大写字母 / 数字 / 下划线，2–64 位',
                  },
                ]
          }
        >
          <Input
            placeholder="如 CRM_STD"
            disabled={isEdit || isPending}
            style={{ fontFamily: 'var(--font-mono)' }}
          />
        </Form.Item>
        <Form.Item name="description" label="描述" rules={[{ max: 1024 }]}>
          <Input.TextArea
            rows={2}
            maxLength={1024}
            showCount
            placeholder="套餐简介（选填）"
            disabled={isPending}
          />
        </Form.Item>
        <Form.Item name="defaultTtlDays" label="默认有效期">
          <Select<number>
            options={TTL_OPTIONS}
            disabled={isSystem || isPending}
            placeholder="选择默认有效期"
          />
        </Form.Item>
        <Form.Item name="moduleCodes" label="包含模块">
          {modulesLoading ? (
            <div style={{ padding: 24, textAlign: 'center' }}>
              <Spin />
            </div>
          ) : (
            <Checkbox.Group disabled={isSystem || isPending} style={{ width: '100%' }}>
              <Space direction="vertical" size={6} style={{ width: '100%' }}>
                {(modules ?? []).map((m) => (
                  <Checkbox
                    key={m.code}
                    value={m.code}
                    disabled={lockedModules.has(m.code) || isSystem || isPending}
                  >
                    <span style={{ fontWeight: 500 }}>{m.name}</span>
                    <span style={{ color: 'var(--text-tertiary)', fontSize: 12, marginLeft: 6 }}>
                      {m.code}
                    </span>
                    {m.description && (
                      <span style={{ color: 'var(--text-tertiary)', fontSize: 12, marginLeft: 8 }}>
                        {m.description}
                      </span>
                    )}
                  </Checkbox>
                ))}
              </Space>
            </Checkbox.Group>
          )}
        </Form.Item>
      </Form>
    </Modal>
  );
}
