import { useEffect, useMemo } from 'react';
import { App, Form, Input, Modal, Select, TreeSelect } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import type { DeptView } from '@wen-saas/domain';
import type { MemberDetail, PositionOption } from '@wen-saas/api-client';
import { applyApiErrorToForm, describeApiError } from '@wen-saas/api-client';

// 编辑态下手机/邮箱回填的是脱敏占位（含 *）。真实联系方式不含 *，故含 * 一律视为
// 「未改动的脱敏占位」放行校验；提交后由后端守卫判定与当前值掩码一致即不覆盖。
const isMaskedPlaceholder = (v: string | undefined) => !!v && v.includes('*');

// 手机号必填（O4-1）：创建账号时强制必填，从根上消除「空手机号→无法走双通道改」的死锁；
// 编辑时若该成员当前手机号为空（存量账号）也强制补录，已有手机号则保持脱敏占位可不改。
// 主部门：创建必填（后端强制成员归属主部门）；编辑未归属部门的成员（如初始管理员）时可留空
// 保持不归属，否则改姓名等基础信息都会被主部门必填卡死。
const makeSchema = (mobileRequired: boolean, deptRequired: boolean) =>
  z
    .object({
      username: z.string().min(4, '账号 4-16 位').max(16, '账号 4-16 位'),
      realName: z.string().min(1, '请输入姓名').max(64),
      mainDeptId: deptRequired ? z.string().min(1, '请选择主部门') : z.string(),
      secondaryDeptIds: z.array(z.string()),
      mobile: mobileRequired
        ? z
            .string()
            .min(1, '请输入手机号')
            .refine(
              (v) => isMaskedPlaceholder(v) || /^1[3-9]\d{9}$/.test(v),
              '请输入正确的 11 位手机号',
            )
        : z
            .string()
            .max(32)
            .optional()
            .refine(
              (v) => !v || isMaskedPlaceholder(v) || /^1[3-9]\d{9}$/.test(v),
              '手机号格式不正确',
            ),
      email: z
        .string()
        .max(128)
        .optional()
        .refine(
          (v) => !v || isMaskedPlaceholder(v) || z.string().email().safeParse(v).success,
          '邮箱格式不正确',
        ),
      positionCode: z.string().optional(),
      roleIds: z.array(z.string()),
    })
    // 主部门留空（保持不归属）时不允许设置兼任部门，避免后端语义冲突
    .refine((v) => v.mainDeptId || v.secondaryDeptIds.length === 0, {
      message: '设置兼任部门前请先选择主部门',
      path: ['secondaryDeptIds'],
    });

export type MemberFormValues = z.infer<ReturnType<typeof makeSchema>>;
type FormValues = MemberFormValues;

type RoleOption = { id: string; name: string };

type Props = {
  open: boolean;
  editing: MemberDetail | null;
  defaultDeptId: string;
  treeData: DeptView[];
  roleOptions: RoleOption[];
  positionOptions: PositionOption[];
  onCancel: () => void;
  onSubmit: (values: FormValues) => Promise<void>;
};

type TreeNode = { value: string; title: string; children?: TreeNode[] };
function toTreeNodes(nodes: DeptView[]): TreeNode[] {
  return nodes.map((n) => {
    const kids = n.children?.length ? toTreeNodes(n.children) : undefined;
    return { value: String(n.id), title: n.name ?? '', ...(kids ? { children: kids } : {}) };
  });
}

export function MemberFormModal({
  open,
  editing,
  defaultDeptId,
  treeData,
  roleOptions,
  positionOptions,
  onCancel,
  onSubmit,
}: Props) {
  const { message } = App.useApp();
  const isEdit = editing != null;
  // 创建必填；编辑时仅当存量空手机号才强制补录（已有号码=脱敏占位，允许不改）。
  const mobileRequired = !isEdit || !editing?.mobile;
  // 创建必填；编辑未归属部门的成员（如初始管理员）可留空保持不归属，已有主部门则不可清空。
  const deptRequired = !isEdit || editing?.mainDept != null;
  const schema = useMemo(
    () => makeSchema(mobileRequired, deptRequired),
    [mobileRequired, deptRequired],
  );
  const treeNodes = useMemo(() => toTreeNodes(treeData), [treeData]);

  // 岗位下拉项：字典项为准；若成员当前岗位 code 已从字典删除，仍补一条占位避免回显丢失。
  const positionSelectOptions = useMemo(() => {
    const opts = positionOptions.map((p) => ({ value: p.code, label: p.name }));
    const cur = editing?.positionCode;
    if (cur && !opts.some((o) => o.value === cur)) {
      opts.push({ value: cur, label: editing?.positionName ?? cur });
    }
    return opts;
  }, [positionOptions, editing?.positionCode, editing?.positionName]);

  const {
    control,
    handleSubmit,
    reset,
    setError,
    watch,
    formState: { isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      username: '',
      realName: '',
      mainDeptId: defaultDeptId,
      secondaryDeptIds: [],
      mobile: '',
      email: '',
      positionCode: '',
      roleIds: [],
    },
  });

  const mainDeptId = watch('mainDeptId');

  useEffect(() => {
    if (!open) return;
    if (editing) {
      reset({
        username: editing.username,
        realName: editing.realName ?? '',
        mainDeptId: editing.mainDept ? editing.mainDept.deptId : defaultDeptId,
        secondaryDeptIds: editing.secondaryDepts.map((d) => d.deptId),
        mobile: editing.mobile ?? '',
        email: editing.email ?? '',
        positionCode: editing.positionCode ?? '',
        roleIds: editing.roleIds,
      });
    } else {
      reset({
        username: '',
        realName: '',
        mainDeptId: defaultDeptId,
        secondaryDeptIds: [],
        mobile: '',
        email: '',
        positionCode: '',
        roleIds: [],
      });
    }
  }, [open, editing, defaultDeptId, reset]);

  const submit = handleSubmit(async (values) => {
    try {
      await onSubmit(values);
    } catch (err) {
      const handled = applyApiErrorToForm(err, (name, e) => setError(name as keyof FormValues, e));
      if (!handled) message.error(describeApiError(err, '保存失败'));
    }
  });

  return (
    <Modal
      open={open}
      title={isEdit ? `编辑成员 — ${editing?.realName ?? editing?.username ?? ''}` : '添加成员'}
      okText={isEdit ? '保存' : '保存并生成激活链接'}
      cancelText="取消"
      confirmLoading={isSubmitting}
      onCancel={onCancel}
      onOk={() => void submit()}
      destroyOnHidden
      width={520}
    >
      <Form layout="vertical" requiredMark component="div">
        <Controller
          control={control}
          name="username"
          render={({ field, fieldState }) => (
            <Form.Item
              label="账号"
              required
              extra="字母开头，字母数字 - _，4-16 位；创建后不可修改"
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input {...field} disabled={isEdit} placeholder="如 zhangsan" />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="realName"
          render={({ field, fieldState }) => (
            <Form.Item
              label="姓名"
              required
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input {...field} placeholder="如 张三" />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="mainDeptId"
          render={({ field, fieldState }) => (
            <Form.Item
              label="主部门"
              required={deptRequired}
              {...(deptRequired ? {} : { extra: '该成员当前未归属部门，可留空保持现状' })}
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <TreeSelect
                {...(field.value ? { value: field.value } : {})}
                onChange={(v) => field.onChange(v ?? '')}
                treeData={treeNodes}
                treeDefaultExpandAll
                allowClear={!deptRequired}
                placeholder={deptRequired ? '选择主部门' : '可留空'}
                style={{ width: '100%' }}
              />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="secondaryDeptIds"
          render={({ field }) => (
            <Form.Item label="兼任部门" extra="可选，多选；不含主部门">
              <TreeSelect
                value={field.value.filter((id) => id !== mainDeptId)}
                onChange={(v) => field.onChange(v ?? [])}
                treeData={treeNodes}
                treeDefaultExpandAll
                treeCheckable
                placeholder="可选"
                style={{ width: '100%' }}
                maxTagCount="responsive"
              />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="positionCode"
          render={({ field }) => (
            <Form.Item label="岗位" extra="可选；来自字典 POSITION，可在字典管理中维护">
              <Select
                allowClear
                value={field.value || undefined}
                onChange={(v) => field.onChange(v ?? '')}
                options={positionSelectOptions}
                placeholder={positionOptions.length ? '请选择岗位' : '暂无岗位，去字典管理维护'}
                notFoundContent="暂无岗位，去字典管理维护"
                style={{ width: '100%' }}
              />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="roleIds"
          render={({ field }) => (
            <Form.Item label="角色" extra="可选，多选">
              <Select
                mode="multiple"
                value={field.value}
                onChange={(v) => field.onChange(v ?? [])}
                options={roleOptions.map((r) => ({ value: r.id, label: r.name }))}
                placeholder="可选"
                style={{ width: '100%' }}
                maxTagCount="responsive"
              />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="mobile"
          render={({ field, fieldState }) => (
            <Form.Item
              label="手机号"
              required={mobileRequired}
              extra={
                isEdit
                  ? editing?.mobile
                    ? '脱敏显示；如需修改请输入完整号码，保留原值表示不变'
                    : '该成员尚未登记手机号，请补录（作为登录/短信验证码/二步验证的核心通道）'
                  : '作为登录账号 / 短信验证码 / 二步验证的核心通道'
              }
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input {...field} placeholder={mobileRequired ? '请输入 11 位手机号' : '可选'} />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="email"
          render={({ field, fieldState }) => (
            <Form.Item
              label="邮箱"
              {...(isEdit ? { extra: '脱敏显示；如需修改请输入完整邮箱，保留原值表示不变' } : {})}
              validateStatus={fieldState.error ? 'error' : ''}
              help={fieldState.error?.message}
            >
              <Input {...field} placeholder="可选" />
            </Form.Item>
          )}
        />
      </Form>
    </Modal>
  );
}
