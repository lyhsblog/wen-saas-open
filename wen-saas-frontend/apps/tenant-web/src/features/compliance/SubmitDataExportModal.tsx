import { App, Alert, Form, Input, Modal, Radio } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { applyApiErrorToForm, describeApiError } from '@wen-saas/api-client';
import type { DataExportView } from '@wen-saas/api-client';
import { useSubmitDataExport } from './complianceQueries';

const schema = z.object({
  reason: z.string().min(1, '申请理由必填').max(512, '最多 512 字符'),
  dataMode: z.enum(['MASKED', 'FULL']),
});

type FormValues = z.infer<typeof schema>;

const EMPTY: FormValues = { reason: '', dataMode: 'MASKED' };

type Props = {
  open: boolean;
  onCancel: () => void;
  onSubmitted: (created: DataExportView) => void;
};

/**
 * 发起客户全量数据导出（GDPR-style）。仅主管理员可发起，必填理由，
 * 每月限 1 次；敏感字段「完整 / 脱敏」二选一。提交后系统异步打包，完成后站内信通知。
 */
export function SubmitDataExportModal({ open, onCancel, onSubmitted }: Props) {
  const { message } = App.useApp();
  const submit = useSubmitDataExport();
  const {
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: EMPTY });

  const close = () => {
    reset(EMPTY);
    onCancel();
  };

  const onOk = handleSubmit(async (values) => {
    try {
      const created = await submit.mutateAsync({
        reason: values.reason,
        dataMode: values.dataMode,
      });
      void message.success('已发起全量数据导出');
      reset(EMPTY);
      onSubmitted(created);
    } catch (err) {
      const consumed = applyApiErrorToForm(err, (name, e) => setError(name as keyof FormValues, e));
      if (!consumed) void message.error(describeApiError(err, '发起失败'));
    }
  });

  return (
    <Modal
      title="发起全量数据导出"
      open={open}
      onCancel={close}
      onOk={() => void onOk()}
      okText="发起导出"
      cancelText="取消"
      okButtonProps={{ loading: isSubmitting }}
      destroyOnHidden
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="仅主管理员可发起，每月限 1 次"
        description="系统将异步打包组织成员、字典、文件、操作日志及业务模块数据，完成后站内信通知，下载链接 7 天有效。"
      />
      <Form layout="vertical">
        <Controller
          control={control}
          name="reason"
          render={({ field }) => (
            <Form.Item
              label="申请理由"
              required
              {...(errors.reason ? { validateStatus: 'error', help: errors.reason.message } : {})}
            >
              <Input.TextArea
                {...field}
                placeholder="如 销户 / 合规审计 / 数据迁移"
                autoSize={{ minRows: 3, maxRows: 6 }}
                maxLength={512}
              />
            </Form.Item>
          )}
        />
        <Controller
          control={control}
          name="dataMode"
          render={({ field }) => (
            <Form.Item label="敏感字段处理">
              <Radio.Group {...field}>
                <Radio value="MASKED">脱敏（手机 / 邮箱等掩码）</Radio>
                <Radio value="FULL">完整（明文导出）</Radio>
              </Radio.Group>
            </Form.Item>
          )}
        />
      </Form>
    </Modal>
  );
}
