import { Form, Input, Modal } from 'antd';
import { applyApiErrorToAntdForm } from '../utils/form';
import { useUpdateRemark } from '../hooks/useTenantMutations';

type Props = {
  open: boolean;
  tenantId: string;
  currentRemark: string | null;
  onClose: () => void;
};

type FormData = {
  remark: string;
};

export function UpdateRemarkModal({ open, tenantId, currentRemark, onClose }: Props) {
  const [form] = Form.useForm<FormData>();
  const { mutateAsync, isPending } = useUpdateRemark(tenantId);

  const handleOk = async () => {
    const values = await form.validateFields();
    try {
      const remark = values.remark?.trim() ?? null;
      await mutateAsync({ ...(remark ? { remark } : { remark: null }) });
      form.resetFields();
      onClose();
    } catch (err) {
      applyApiErrorToAntdForm(err, form);
    }
  };

  return (
    <Modal
      open={open}
      title="编辑运营备注"
      onCancel={onClose}
      onOk={() => void handleOk()}
      confirmLoading={isPending}
      mask={{ closable: false }}
      destroyOnHidden
      okText="保存"
      width={480}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ remark: currentRemark ?? '' }}
        style={{ marginTop: 16 }}
      >
        <Form.Item
          name="remark"
          label="备注（仅运营可见）"
          rules={[{ max: 500, message: '不超过 500 字符' }]}
        >
          <Input.TextArea
            rows={4}
            placeholder="销售跟进记录、合同备注、特殊说明等"
            maxLength={500}
            showCount
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
