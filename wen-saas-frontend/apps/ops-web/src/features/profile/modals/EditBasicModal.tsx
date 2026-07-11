import { DatePicker, Form, Modal, Radio } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import type { OpsProfileBasic, UpdateOpsProfileRequest } from '@wen-saas/api-client';
import { applyApiErrorToAntdForm } from '../../tenants/utils/form';
import { useUpdateProfile } from '../profileQueries';

type FormValues = {
  gender?: number;
  birthday?: Dayjs | null;
};

export function EditBasicModal({
  basic,
  onClose,
}: {
  basic: OpsProfileBasic | undefined;
  onClose: () => void;
}) {
  const [form] = Form.useForm<FormValues>();
  const mutation = useUpdateProfile();

  const initial: FormValues = {
    gender: basic?.gender ?? 0,
    birthday: basic?.birthday ? dayjs(basic.birthday) : null,
  };

  const submit = () => {
    void form.validateFields().then((vals) => {
      const body: UpdateOpsProfileRequest = {
        // 姓名不可自助修改：沿用原值占位（后端忽略 realName），仅提交性别/生日。
        // 头像改由资料卡「更换头像」独立入口即传即存（O5-1），编辑弹窗不再携带 avatarFileId，
        // 不传该字段后端按未修改处理。
        realName: basic?.realName ?? '',
        ...(vals.gender != null ? { gender: vals.gender } : {}),
        ...(vals.birthday ? { birthday: vals.birthday.format('YYYY-MM-DD') } : {}),
      };
      mutation.mutate(body, {
        onSuccess: () => onClose(),
        onError: (err) => {
          applyApiErrorToAntdForm(err, form);
        },
      });
    });
  };

  return (
    <Modal
      open
      title="编辑基本信息"
      onCancel={onClose}
      onOk={submit}
      okText="保存"
      cancelText="取消"
      confirmLoading={mutation.isPending}
      destroyOnHidden
    >
      <Form<FormValues> form={form} layout="vertical" initialValues={initial}>
        <Form.Item label="姓名" extra="姓名由运营管理员在「运营成员管理」处维护，不可自助修改">
          <span style={{ color: 'var(--text-secondary, rgba(0,0,0,0.65))' }}>
            {basic?.realName ?? '—'}
          </span>
        </Form.Item>
        <Form.Item label="性别" name="gender">
          <Radio.Group>
            <Radio value={0}>未知</Radio>
            <Radio value={1}>男</Radio>
            <Radio value={2}>女</Radio>
          </Radio.Group>
        </Form.Item>
        <Form.Item label="生日" name="birthday">
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
