import { useEffect } from 'react';
import { Alert, App, Form, Input, Modal } from 'antd';
import { describeApiError } from '@wen-saas/api-client';
import type { PackageDetail } from '@wen-saas/api-client';
import { applyApiErrorToAntdForm } from '../tenants/utils/form';
import { useCopyPackage } from './hooks/usePackageMutations';

type Props = {
  open: boolean;
  /** 复制源套餐。 */
  source: PackageDetail;
  onClose: () => void;
  /** 复制成功后回调（选中新套餐）。 */
  onCopied: (code: string) => void;
};

type FormData = {
  code: string;
  name: string;
};

const CODE_PATTERN = /^[A-Z][A-Z0-9_]{1,63}$/;

export function OpsPackageCopyModal({ open, source, onClose, onCopied }: Props) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormData>();
  const copy = useCopyPackage(source.code);

  useEffect(() => {
    if (open) {
      form.setFieldsValue({ code: '', name: `${source.name}（副本）` });
    }
  }, [open, source.name, form]);

  const submit = async () => {
    const values = await form.validateFields();
    try {
      const detail = await copy.mutateAsync({ code: values.code, name: values.name });
      form.resetFields();
      onCopied(detail.code);
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
      title={`复制套餐 · ${source.name}`}
      onCancel={onClose}
      onOk={() => void submit()}
      okText="复制为草稿"
      confirmLoading={copy.isPending}
      mask={{ closable: false }}
      destroyOnHidden
      width={520}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="将以源套餐的描述、默认有效期、模块为初值生成一个新草稿；请重填编码与名称。"
      />
      <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
        <Form.Item
          name="name"
          label="新套餐名称"
          rules={[{ required: true, message: '请输入套餐名称' }, { max: 128 }]}
        >
          <Input placeholder="面向客户展示的套餐名" disabled={copy.isPending} />
        </Form.Item>
        <Form.Item
          name="code"
          label="新套餐编码"
          extra="大写字母开头，仅含大写字母 / 数字 / 下划线，创建后不可改"
          rules={[
            { required: true, message: '请输入套餐编码' },
            {
              pattern: CODE_PATTERN,
              message: '大写字母开头，仅含大写字母 / 数字 / 下划线，2–64 位',
            },
          ]}
        >
          <Input
            placeholder="如 CRM_STD_COPY"
            disabled={copy.isPending}
            style={{ fontFamily: 'var(--font-mono)' }}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
