import { useEffect } from 'react';
import { App, Form, Input, Modal, Radio } from 'antd';
import { describeApiError, type OpsRoleDataScope, type OpsRoleDetail } from '@wen-saas/api-client';
import { applyApiErrorToAntdForm } from '../../tenants/utils/form';
import { DATA_SCOPE_OPTIONS } from '../utils/sysadminMeta';
import { useCreateRole, useUpdateRole } from '../hooks/useRoleMutations';

type Props = {
  open: boolean;
  /** 传入 detail 为编辑模式；不传为新建模式。 */
  editing: OpsRoleDetail | null;
  onClose: () => void;
  onCreated?: (id: string) => void;
};

type FormData = {
  code: string;
  name: string;
  description: string;
  dataScope: OpsRoleDataScope;
};

export function RoleFormModal({ open, editing, onClose, onCreated }: Props) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormData>();
  const isEdit = editing != null;

  const create = useCreateRole();
  const update = useUpdateRole(editing?.id ?? '');
  const isPending = create.isPending || update.isPending;

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue({
        code: editing.code,
        name: editing.name,
        description: editing.description ?? '',
        dataScope: editing.dataScope,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ dataScope: 'ALL' });
    }
  }, [open, editing, form]);

  const submit = async () => {
    const values = await form.validateFields();
    try {
      if (isEdit) {
        await update.mutateAsync({
          name: values.name,
          description: values.description ? values.description : null,
          dataScope: values.dataScope,
        });
      } else {
        const detail = await create.mutateAsync({
          code: values.code,
          name: values.name,
          description: values.description ? values.description : null,
          dataScope: values.dataScope,
        });
        onCreated?.(detail.id);
      }
      form.resetFields();
      onClose();
    } catch (err) {
      if (!applyApiErrorToAntdForm(err, form)) {
        void message.error(describeApiError(err, isEdit ? '保存失败' : '创建失败'));
      }
    }
  };

  return (
    <Modal
      open={open}
      title={isEdit ? `编辑角色 · ${editing.name}` : '新建自定义角色'}
      onCancel={onClose}
      onOk={() => void submit()}
      okText={isEdit ? '保存' : '创建'}
      confirmLoading={isPending}
      mask={{ closable: false }}
      destroyOnHidden
      width={520}
    >
      <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
        <Form.Item
          name="code"
          label="角色编码"
          extra="大写字母开头，仅大写字母 / 数字 / 下划线；创建后不可改"
          rules={[
            { required: true, message: '请输入角色编码' },
            { pattern: /^[A-Z][A-Z0-9_]{1,63}$/, message: '格式：大写字母开头，仅 A-Z 0-9 _' },
          ]}
        >
          <Input placeholder="例：OPS_FINANCE" disabled={isEdit || isPending} />
        </Form.Item>
        <Form.Item
          name="name"
          label="角色名称"
          rules={[{ required: true, message: '请输入角色名称' }, { max: 128 }]}
        >
          <Input placeholder="例：财务对账" disabled={isPending} />
        </Form.Item>
        <Form.Item name="description" label="描述" rules={[{ max: 1024 }]}>
          <Input.TextArea
            rows={2}
            maxLength={1024}
            showCount
            placeholder="（选填）"
            disabled={isPending}
          />
        </Form.Item>
        <Form.Item
          name="dataScope"
          label="数据范围"
          extra="全部数据 = 看所有租户；仅本人负责 = 仅看 csm 为本人的租户"
          rules={[{ required: true, message: '请选择数据范围' }]}
        >
          <Radio.Group disabled={isPending} options={DATA_SCOPE_OPTIONS} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
