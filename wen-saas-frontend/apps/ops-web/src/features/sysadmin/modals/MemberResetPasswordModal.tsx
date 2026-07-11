import { Alert, App, Form, Input, Modal } from 'antd';
import { describeApiError, type OpsMemberListItem } from '@wen-saas/api-client';
import { applyApiErrorToAntdForm } from '../../tenants/utils/form';
import { useResetMemberPassword } from '../hooks/useMemberMutations';

type Props = {
  open: boolean;
  member: OpsMemberListItem;
  onClose: () => void;
};

type FormData = { newPassword: string };

export function MemberResetPasswordModal({ open, member, onClose }: Props) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormData>();
  const reset = useResetMemberPassword(member.id);

  const submit = async () => {
    const values = await form.validateFields();
    try {
      await reset.mutateAsync({ newPassword: values.newPassword });
      form.resetFields();
      onClose();
    } catch (err) {
      // 弱密码(WEAK_PASSWORD)高亮到新密码字段；其余兜底 toast。
      if (!applyApiErrorToAntdForm(err, form, { WEAK_PASSWORD: 'newPassword' })) {
        void message.error(describeApiError(err, '重置失败'));
      }
    }
  };

  return (
    <Modal
      open={open}
      title={`重置密码 · ${member.realName}`}
      onCancel={onClose}
      onOk={() => void submit()}
      okText="重置"
      okButtonProps={{ danger: true }}
      confirmLoading={reset.isPending}
      mask={{ closable: false }}
      destroyOnHidden
      width={480}
    >
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="重置后该成员当前全部会话会被立即下线，需用新密码重新登录。请线下安全地告知本人。"
      />
      <Form form={form} layout="vertical">
        <Form.Item
          name="newPassword"
          label="新密码"
          extra="≥8 位且含 3 类字符"
          rules={[{ required: true, message: '请输入新密码' }]}
        >
          <Input.Password placeholder="新密码" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
