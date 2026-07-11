import { Checkbox, Form, Input } from 'antd';
import { DangerConfirm } from '@wen-saas/shared-ui';
import { useRevokePlan } from '../hooks/useTenantMutations';

type Props = {
  open: boolean;
  tenantId: string;
  packageCode: string;
  packageName: string;
  onClose: () => void;
};

type FormData = {
  reason: string;
  notifyAdmin: boolean;
};

export function RevokePlanModal({ open, tenantId, packageCode, packageName, onClose }: Props) {
  const [form] = Form.useForm<FormData>();
  const { mutateAsync, isPending } = useRevokePlan(tenantId);

  const handleConfirm = async () => {
    const values = await form.validateFields();
    await mutateAsync({
      packageCode,
      body: {
        reason: values.reason,
        ...(values.notifyAdmin != null ? { notifyAdmin: values.notifyAdmin } : {}),
      },
    });
    form.resetFields();
    onClose();
  };

  const handleCancel = () => {
    form.resetFields();
    onClose();
  };

  return (
    <DangerConfirm
      open={open}
      title={`取消套餐：${packageName}`}
      lead={`您即将取消该租户的「${packageName}」套餐`}
      consequences={[
        '套餐立即对该租户不可见，所有相关功能将无法使用',
        '数据保留，重新勾选套餐后数据可恢复',
        '若勾选"通知主管理员"，系统将发送站内通知',
      ]}
      confirmPhrase={packageCode}
      okText="确认取消"
      confirming={isPending}
      onCancel={handleCancel}
      onConfirm={() => void handleConfirm()}
      extra={
        <Form form={form} layout="vertical">
          <Form.Item
            name="reason"
            label="取消原因"
            rules={[
              { required: true, message: '请填写取消原因' },
              { max: 500, message: '不超过 500 字符' },
            ]}
          >
            <Input.TextArea rows={3} placeholder="请说明取消套餐的原因" maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="notifyAdmin" valuePropName="checked" initialValue={true}>
            <Checkbox>通知主管理员</Checkbox>
          </Form.Item>
        </Form>
      }
    />
  );
}
