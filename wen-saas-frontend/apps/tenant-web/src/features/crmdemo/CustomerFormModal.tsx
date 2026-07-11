import { useEffect } from 'react';
import { App, Form, Input, Modal } from 'antd';
import {
  describeApiError,
  type CrmCustomerCreateRequest,
  type CrmCustomerUpdateRequest,
} from '@wen-saas/api-client';
import { CustomFieldFormFields } from '../customfield/CustomFieldFormFields';
import { DictEnumSelect } from '../customfield/DictEnumSelect';
import { EntityRefPicker } from '../customfield/EntityRefPicker';
import { CRM_CUSTOMER_OBJECT } from './crmApi';
import { CRM_SOURCE_DICT } from './crmSource';
import { useCreateCustomer, useCustomerDetail, useUpdateCustomer } from './crmQueries';

/** 从详情视图的 extra 中剥出表单用的原始自定义字段值（去掉 __label 衍生键）。 */
function rawCustom(extra: Record<string, unknown> | null | undefined): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(extra ?? {})) {
    if (!k.endsWith('__label')) out[k] = v;
  }
  return out;
}

/** 从 extra 的 __label 衍生键抽出 ref 字段展示名（fieldCode→展示名），供编辑回显选择器显示名字而非 id。 */
function refLabels(extra: Record<string, unknown> | null | undefined): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(extra ?? {})) {
    if (k.endsWith('__label') && typeof v === 'string' && v) {
      out[k.slice(0, -'__label'.length)] = v;
    }
  }
  return out;
}

/** 提交前剔除空值（清空的可选字段 = 取消该值，避免空串触发 enum/ref 等校验）。 */
function cleanCustom(c: Record<string, unknown> | undefined): Record<string, unknown> | undefined {
  if (!c) return undefined;
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(c)) {
    if (v === '' || v === null || v === undefined) continue;
    out[k] = v;
  }
  return Object.keys(out).length ? out : undefined;
}

interface FormValues {
  name: string;
  code?: string;
  source?: string;
  ownerId?: string;
  ownerDeptId?: string;
  custom?: Record<string, unknown>;
}

export function CustomerFormModal({
  open,
  editId,
  onClose,
}: {
  open: boolean;
  editId: string | null;
  onClose: () => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const isEdit = !!editId;
  const detailQuery = useCustomerDetail(open && editId ? editId : null);
  const create = useCreateCustomer();
  const update = useUpdateCustomer();

  useEffect(() => {
    if (!open) return;
    if (isEdit) {
      const d = detailQuery.data;
      if (d)
        form.setFieldsValue({
          name: d.name,
          code: d.code ?? '',
          ...(d.source ? { source: d.source } : {}),
          ...(d.ownerId ? { ownerId: d.ownerId } : {}),
          ...(d.ownerDeptId ? { ownerDeptId: d.ownerDeptId } : {}),
          custom: rawCustom(d.extra),
        });
    } else {
      form.resetFields();
    }
  }, [open, isEdit, detailQuery.data, form]);

  const submit = async () => {
    const v = await form.validateFields();
    const custom = cleanCustom(v.custom);
    try {
      if (isEdit && editId) {
        const body: CrmCustomerUpdateRequest = { name: v.name };
        if (v.code) body.code = v.code;
        if (v.source) body.source = v.source;
        if (v.ownerId) body.ownerId = v.ownerId;
        if (v.ownerDeptId) body.ownerDeptId = v.ownerDeptId;
        if (custom) body.custom = custom;
        await update.mutateAsync({ id: editId, body });
        message.success('已保存');
      } else {
        const body: CrmCustomerCreateRequest = { name: v.name };
        if (v.code) body.code = v.code;
        if (v.source) body.source = v.source;
        if (v.ownerId) body.ownerId = v.ownerId;
        if (v.ownerDeptId) body.ownerDeptId = v.ownerDeptId;
        if (custom) body.custom = custom;
        await create.mutateAsync(body);
        message.success('客户已创建');
      }
      onClose();
    } catch (e) {
      message.error(describeApiError(e, '保存失败'));
    }
  };

  return (
    <Modal
      title={isEdit ? '编辑客户' : '新增客户'}
      open={open}
      onCancel={onClose}
      onOk={() => void submit()}
      okButtonProps={{ loading: create.isPending || update.isPending }}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="name"
          label="客户名称"
          rules={[{ required: true, message: '客户名称必填' }]}
        >
          <Input maxLength={255} />
        </Form.Item>
        <Form.Item name="code" label="客户编号">
          <Input maxLength={64} />
        </Form.Item>
        <Form.Item name="source" label="来源">
          <DictEnumSelect dictCode={CRM_SOURCE_DICT} allowClear />
        </Form.Item>
        <Form.Item name="ownerId" label="负责人" extra={isEdit ? undefined : '不选则默认为当前用户'}>
          <EntityRefPicker
            objectCode="sys.user"
            {...(detailQuery.data?.ownerName ? { label: detailQuery.data.ownerName } : {})}
          />
        </Form.Item>
        <Form.Item
          name="ownerDeptId"
          label="归属部门"
          extra={isEdit ? undefined : '不选则默认为当前用户主部门'}
        >
          <EntityRefPicker
            objectCode="sys.dept"
            {...(detailQuery.data?.ownerDeptName ? { label: detailQuery.data.ownerDeptName } : {})}
          />
        </Form.Item>
        <CustomFieldFormFields
          objectCode={CRM_CUSTOMER_OBJECT}
          refLabels={refLabels(detailQuery.data?.extra)}
        />
      </Form>
    </Modal>
  );
}
