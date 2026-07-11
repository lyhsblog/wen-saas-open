import { Form, Input, InputNumber, Modal } from 'antd';
import { applyApiErrorToAntdForm } from '../utils/form';
import { useRenewPlan } from '../hooks/useTenantMutations';

type Props = {
  open: boolean;
  tenantId: string;
  packageCode: string;
  packageName: string;
  onClose: () => void;
};

type FormData = {
  ttlDays: number;
  remark: string;
};

export function RenewPlanModal({ open, tenantId, packageCode, packageName, onClose }: Props) {
  const [form] = Form.useForm<FormData>();
  const { mutateAsync, isPending } = useRenewPlan(tenantId);

  const handleOk = async () => {
    const values = await form.validateFields();
    try {
      await mutateAsync({
        packageCode,
        body: {
          ttlDays: values.ttlDays,
          ...(values.remark ? { remark: values.remark } : {}),
        },
      });
      form.resetFields();
      onClose();
    } catch (err) {
      if (!applyApiErrorToAntdForm(err, form)) {
        // error already shown by hook
      }
    }
  };

  return (
    <Modal
      open={open}
      title={`续费套餐：${packageName}`}
      onCancel={onClose}
      onOk={() => void handleOk()}
      confirmLoading={isPending}
      mask={{ closable: false }}
      destroyOnHidden
      okText="确认续费"
      width={440}
    >
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item
          name="ttlDays"
          label="续费天数"
          initialValue={365}
          rules={[
            { required: true, message: '请输入续费天数' },
            { type: 'number', min: 1, message: '至少 1 天' },
          ]}
        >
          <InputNumber min={1} max={3650} style={{ width: '100%' }} addonAfter="天" />
        </Form.Item>
        <div
          style={{
            fontSize: 12,
            color: 'var(--text-tertiary)',
            marginTop: -12,
            marginBottom: 16,
          }}
        >
          从今日起算新周期
        </div>
        <Form.Item
          name="remark"
          label="备注（选填）"
          rules={[{ max: 500, message: '不超过 500 字符' }]}
        >
          <Input.TextArea rows={2} maxLength={500} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
