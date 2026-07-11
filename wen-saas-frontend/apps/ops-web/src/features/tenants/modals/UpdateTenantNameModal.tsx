import { Form, Input } from 'antd';
import { DangerConfirm } from '@wen-saas/shared-ui';
import { applyApiErrorToAntdForm } from '../utils/form';
import { useUpdateName } from '../hooks/useTenantMutations';

type Props = {
  open: boolean;
  tenantId: string;
  currentName: string;
  onClose: () => void;
};

type FormData = {
  newName: string;
  reason: string;
};

export function UpdateTenantNameModal({ open, tenantId, currentName, onClose }: Props) {
  const [form] = Form.useForm<FormData>();
  const { mutateAsync, isPending } = useUpdateName(tenantId);

  const handleConfirm = async () => {
    const values = await form.validateFields();
    try {
      await mutateAsync({ newName: values.newName, reason: values.reason });
      form.resetFields();
      onClose();
    } catch (err) {
      applyApiErrorToAntdForm(err, form);
    }
  };

  const handleCancel = () => {
    form.resetFields();
    onClose();
  };

  return (
    <DangerConfirm
      open={open}
      title="编辑租户名称"
      lead="修改租户名称可能导致与已签合同或沟通记录不一致"
      consequences={[
        '变更将记录在账单变更历史中，需填写修改原因',
        '若与客户有书面合同，建议同步更新合同记录',
      ]}
      okText="确认修改"
      confirming={isPending}
      onCancel={handleCancel}
      onConfirm={() => void handleConfirm()}
      extra={
        <Form form={form} layout="vertical">
          <Form.Item label="当前名称">
            <span style={{ color: 'var(--text-secondary)' }}>{currentName}</span>
          </Form.Item>
          <Form.Item
            name="newName"
            label="新名称"
            rules={[
              { required: true, message: '请输入新名称' },
              { min: 1, message: '名称不能为空' },
              { max: 64, message: '不超过 64 个字符' },
            ]}
          >
            <Input placeholder="新租户名称" maxLength={64} showCount />
          </Form.Item>
          <Form.Item
            name="reason"
            label="修改原因"
            rules={[
              { required: true, message: '请填写修改原因' },
              { max: 500, message: '不超过 500 字符' },
            ]}
          >
            <Input.TextArea
              rows={2}
              placeholder="修改原因（将记录在审计日志中）"
              maxLength={500}
              showCount
            />
          </Form.Item>
        </Form>
      }
    />
  );
}
