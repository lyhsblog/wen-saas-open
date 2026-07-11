import { Form, Input, InputNumber, Modal } from 'antd';
import { applyApiErrorToAntdForm } from '../utils/form';
import { useAddSeatAddon } from '../hooks/useTenantMutations';

type Props = {
  open: boolean;
  tenantId: string;
  onClose: () => void;
};

type FormData = {
  quantity: number;
  remark: string;
};

export function AddSeatAddonModal({ open, tenantId, onClose }: Props) {
  const [form] = Form.useForm<FormData>();
  const { mutateAsync, isPending } = useAddSeatAddon(tenantId);

  const handleOk = async () => {
    const values = await form.validateFields();
    try {
      await mutateAsync({
        quantity: values.quantity,
        ...(values.remark ? { remark: values.remark } : {}),
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
      title="加购席位"
      onCancel={onClose}
      onOk={() => void handleOk()}
      confirmLoading={isPending}
      mask={{ closable: false }}
      destroyOnHidden
      okText="确认加购"
      width={420}
    >
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item
          name="quantity"
          label="加购数量"
          rules={[
            { required: true, message: '请输入加购数量' },
            { type: 'number', min: 1, message: '至少加购 1 个' },
          ]}
        >
          <InputNumber min={1} max={9999} style={{ width: '100%' }} addonAfter="个" />
        </Form.Item>
        <div
          style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: -12, marginBottom: 16 }}
        >
          席位永久有效，不受套餐到期影响
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
