import { useEffect } from 'react';
import { Alert, App, Form, Input, Modal } from 'antd';
import { describeApiError } from '@wen-saas/api-client';
import type { RecRoleDetail } from '@wen-saas/api-client';
import { applyApiErrorToAntdForm } from '../tenants/utils/form';
import { useCopyRecRole } from './hooks/useRecRoleMutations';

type Props = {
  open: boolean;
  /** 复制源模板。 */
  source: RecRoleDetail;
  onClose: () => void;
  /** 复制成功后回调（选中新模板）。 */
  onCopied: (id: string) => void;
};

type FormData = {
  name: string;
};

export function OpsRecRoleCopyModal({ open, source, onClose, onCopied }: Props) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormData>();
  const copy = useCopyRecRole(source.id);

  useEffect(() => {
    if (open) {
      form.setFieldsValue({ name: `${source.name}（副本）` });
    }
  }, [open, source.name, form]);

  const submit = async () => {
    const values = await form.validateFields();
    try {
      const detail = await copy.mutateAsync({ name: values.name });
      form.resetFields();
      onCopied(detail.id);
      onClose();
    } catch (err) {
      if (!applyApiErrorToAntdForm(err, form)) {
        void message.error(describeApiError(err, '复制失败'));
      }
    }
  };

  return (
    <Modal
      open={open}
      title={`复制推荐角色 · ${source.name}`}
      onCancel={onClose}
      onOk={() => void submit()}
      okText="复制为草稿"
      confirmLoading={copy.isPending}
      mask={{ closable: false }}
      destroyOnHidden
      width={480}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="将以源模板的描述、默认数据范围、关联套餐包、权限点为初值生成一个新草稿；请重填名称。"
      />
      <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
        <Form.Item
          name="name"
          label="新模板名称"
          extra="运营内部唯一"
          rules={[{ required: true, message: '请输入模板名称' }, { max: 128 }]}
        >
          <Input placeholder="例：客户经理（副本）" disabled={copy.isPending} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
