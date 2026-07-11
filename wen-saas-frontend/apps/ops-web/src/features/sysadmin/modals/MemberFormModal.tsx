import { useEffect } from 'react';
import { App, DatePicker, Form, Input, Modal, Select } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { describeApiError, opsTeamRoleApi, type OpsMemberDetail } from '@wen-saas/api-client';
import { http } from '../../../lib/http';
import { applyApiErrorToAntdForm } from '../../tenants/utils/form';
import { useCreateMember, useUpdateMember } from '../hooks/useMemberMutations';

type Props = {
  open: boolean;
  /** 传入 detail 为编辑模式；不传为新建模式。 */
  editing: OpsMemberDetail | null;
  onClose: () => void;
  /** 新建成功后回调（用于选中新成员）。 */
  onCreated?: (id: string) => void;
};

type FormData = {
  username: string;
  password: string;
  realName: string;
  email: string;
  mobile: string;
  gender?: number | undefined;
  birthday?: dayjs.Dayjs | null;
  empNo?: string | undefined;
  roleIds: string[];
};

const GENDER_OPTIONS = [
  { label: '未知', value: 0 },
  { label: '男', value: 1 },
  { label: '女', value: 2 },
];

/** 后端业务错误码 → 本表单字段，用于把 409/422 域错误高亮到对应输入框（而非仅弹 toast）。 */
const ERROR_CODE_FIELD_MAP: Record<string, string> = {
  USERNAME_CONFLICT: 'username',
  WEAK_PASSWORD: 'password',
  EMAIL_CONFLICT: 'email',
  MOBILE_CONFLICT: 'mobile',
};

export function MemberFormModal({ open, editing, onClose, onCreated }: Props) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormData>();
  const isEdit = editing != null;

  const { data: roles } = useQuery({
    queryKey: ['ops', 'team-roles'],
    queryFn: () => opsTeamRoleApi(http).listRoles(),
    enabled: open,
  });

  const create = useCreateMember();
  const update = useUpdateMember(editing?.id ?? '');
  const isPending = create.isPending || update.isPending;

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue({
        realName: editing.realName,
        email: editing.email ?? '',
        mobile: editing.mobile ?? '',
        gender: editing.gender ?? undefined,
        birthday: editing.birthday ? dayjs(editing.birthday) : null,
        empNo: editing.empNo ?? undefined,
        roleIds: editing.roleIds,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ roleIds: [] });
    }
  }, [open, editing, form]);

  const submit = async () => {
    const values = await form.validateFields();
    try {
      if (isEdit) {
        await update.mutateAsync({
          realName: values.realName,
          email: values.email,
          mobile: values.mobile,
          gender: values.gender ?? null,
          birthday: values.birthday ? values.birthday.format('YYYY-MM-DD') : null,
          empNo: values.empNo ? values.empNo : null,
          roleIds: values.roleIds,
        });
      } else {
        const detail = await create.mutateAsync({
          username: values.username,
          password: values.password,
          realName: values.realName,
          email: values.email,
          mobile: values.mobile,
          roleIds: values.roleIds,
        });
        onCreated?.(detail.id);
      }
      form.resetFields();
      onClose();
    } catch (err) {
      // 422 errors[] + 业务码(用户名重复/弱密码/邮箱手机冲突)高亮到对应字段；其余兜底 toast。
      if (!applyApiErrorToAntdForm(err, form, ERROR_CODE_FIELD_MAP)) {
        void message.error(describeApiError(err, isEdit ? '保存失败' : '创建失败'));
      }
    }
  };

  const roleOptions = (roles ?? []).map((r) => ({ label: r.name, value: r.id }));

  return (
    <Modal
      open={open}
      title={isEdit ? `编辑成员 · ${editing.realName}` : '添加成员'}
      onCancel={onClose}
      onOk={() => void submit()}
      okText={isEdit ? '保存' : '创建'}
      confirmLoading={isPending}
      mask={{ closable: false }}
      destroyOnHidden
      width={560}
    >
      <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
        {!isEdit && (
          <>
            <Form.Item
              name="username"
              label="登录账号"
              extra="字母开头，4-16 位；创建后不可改"
              rules={[{ required: true, message: '请输入登录账号' }]}
            >
              <Input placeholder="例：zhang_wei" disabled={isPending} />
            </Form.Item>
            <Form.Item
              name="password"
              label="初始密码"
              extra="≥8 位且含 3 类字符；由超管线下传递给成员"
              rules={[{ required: true, message: '请输入初始密码' }]}
            >
              <Input.Password placeholder="初始密码" disabled={isPending} />
            </Form.Item>
          </>
        )}
        <Form.Item
          name="realName"
          label="姓名"
          rules={[{ required: true, message: '请输入姓名' }, { max: 64 }]}
        >
          <Input placeholder="真实姓名" disabled={isPending} />
        </Form.Item>
        <Form.Item
          name="email"
          label="邮箱"
          {...(isEdit ? { extra: '脱敏显示；如需修改请输入完整邮箱，保留原值表示不变' } : {})}
          rules={[
            { required: true, message: '请输入邮箱' },
            // 编辑态回填脱敏占位（含 *）；真实邮箱不含 *，故含 * 放行，提交后端守卫判定未修改。
            {
              validator: (_, v: string) =>
                !v || (isEdit && v.includes('*')) || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v)
                  ? Promise.resolve()
                  : Promise.reject(new Error('邮箱格式不正确')),
            },
          ]}
        >
          <Input placeholder="name@platform.com" disabled={isPending} />
        </Form.Item>
        <Form.Item
          name="mobile"
          label="手机号"
          {...(isEdit ? { extra: '脱敏显示；如需修改请输入完整号码，保留原值表示不变' } : {})}
          rules={[
            { required: true, message: '请输入手机号' },
            {
              validator: (_, v: string) =>
                !v || (isEdit && v.includes('*')) || /^1[3-9]\d{9}$/.test(v)
                  ? Promise.resolve()
                  : Promise.reject(new Error('手机号格式不正确')),
            },
          ]}
        >
          <Input placeholder="11 位手机号" disabled={isPending} />
        </Form.Item>
        {isEdit && (
          <>
            <Form.Item name="gender" label="性别">
              <Select
                allowClear
                placeholder="（选填）"
                options={GENDER_OPTIONS}
                disabled={isPending}
              />
            </Form.Item>
            <Form.Item name="birthday" label="生日">
              <DatePicker style={{ width: '100%' }} disabled={isPending} />
            </Form.Item>
            <Form.Item name="empNo" label="工号">
              <Input placeholder="（选填）" disabled={isPending} />
            </Form.Item>
          </>
        )}
        <Form.Item name="roleIds" label="角色" extra="可多选；不选则无任何运营权限">
          <Select
            mode="multiple"
            allowClear
            placeholder="选择角色"
            options={roleOptions}
            disabled={isPending}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
