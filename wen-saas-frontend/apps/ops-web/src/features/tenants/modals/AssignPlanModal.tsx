import { Form, Input, InputNumber, Modal, Select, Tooltip } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import { applyApiErrorToAntdForm } from '../utils/form';
import { useAssignPlan } from '../hooks/useTenantMutations';
import { useOpsPackages } from '../hooks/useOpsPackages';
import type { OpsPlanView } from '@wen-saas/api-client';

type Props = {
  open: boolean;
  tenantId: string;
  existingPlans: OpsPlanView[];
  onClose: () => void;
};

type FormData = {
  packageCode: string;
  ttlDays: number;
  remark: string;
};

export function AssignPlanModal({ open, tenantId, existingPlans, onClose }: Props) {
  const [form] = Form.useForm<FormData>();
  const { mutateAsync, isPending } = useAssignPlan(tenantId);
  const { catalog, byCode, isLoading: catalogLoading } = useOpsPackages();

  const isAlreadyActive = (code: string) =>
    existingPlans.some((p) => p.code === code && !p.readonly);

  const handleOk = async () => {
    const values = await form.validateFields();
    try {
      await mutateAsync({
        packageCode: values.packageCode,
        ...(values.ttlDays != null ? { ttlDays: values.ttlDays } : {}),
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
      title="勾选套餐包"
      onCancel={onClose}
      onOk={() => void handleOk()}
      confirmLoading={isPending}
      mask={{ closable: false }}
      destroyOnHidden
      okText="确认勾选"
      width={480}
    >
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item
          name="packageCode"
          label="套餐包"
          rules={[{ required: true, message: '请选择套餐包' }]}
        >
          <Select
            placeholder="选择套餐包"
            loading={catalogLoading}
            onChange={(code: string) => {
              // 选包后把有效天数默认值切到该包的 defaultTtlDays（无则保留当前/365）
              const def = byCode.get(code)?.defaultTtlDays;
              if (def != null) form.setFieldValue('ttlDays', def);
            }}
          >
            {catalog.map((pkg) => {
              const archived = pkg.status !== 'ACTIVE';
              const active = isAlreadyActive(pkg.code);
              return (
                <Select.Option key={pkg.code} value={pkg.code} disabled={archived || active}>
                  <span>
                    {pkg.name}
                    {archived && (
                      <span style={{ color: 'var(--text-tertiary)', fontSize: 12 }}>
                        {' '}
                        （已下架）
                      </span>
                    )}
                    {active && (
                      <span style={{ color: 'var(--success)', fontSize: 12 }}> （已生效）</span>
                    )}
                  </span>
                </Select.Option>
              );
            })}
          </Select>
        </Form.Item>
        <Form.Item
          name="ttlDays"
          label={
            <span>
              有效天数&nbsp;
              <Tooltip title="留空则按套餐默认有效期">
                <InfoCircleOutlined style={{ color: 'var(--text-tertiary)' }} />
              </Tooltip>
            </span>
          }
          initialValue={365}
        >
          <InputNumber min={1} max={3650} style={{ width: '100%' }} addonAfter="天" />
        </Form.Item>
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
