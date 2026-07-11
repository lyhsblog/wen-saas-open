import { useMemo, useState } from 'react';
import { Alert, App, Form, Input, Modal, Progress } from 'antd';
import { useNavigate } from 'react-router';
import { describeApiError } from '@wen-saas/api-client';
import { useAuthStore } from '@wen-saas/auth';
import { authService } from '../../../lib/http';
import { useChangePassword } from '../profileQueries';
import { StepUpGate } from './StepUpGate';

type FormValues = {
  newPassword: string;
  confirmPassword: string;
};

function pwdScore(pw: string): {
  level: 0 | 1 | 2 | 3 | 4;
  label: string;
  percent: number;
  color: string;
} {
  if (!pw) return { level: 0, label: '弱', percent: 0, color: '#FCA5A5' };
  let kinds = 0;
  if (/[a-z]/.test(pw)) kinds++;
  if (/[A-Z]/.test(pw)) kinds++;
  if (/\d/.test(pw)) kinds++;
  if (/[^A-Za-z0-9]/.test(pw)) kinds++;
  const long = pw.length >= 12;
  const mid = pw.length >= 8;
  if (!mid || kinds < 2) return { level: 1, label: '弱', percent: 25, color: '#F87171' };
  if (kinds < 3) return { level: 2, label: '中', percent: 50, color: '#FBBF24' };
  if (!long) return { level: 3, label: '强', percent: 75, color: '#34D399' };
  return { level: 4, label: '极强', percent: 100, color: '#10B981' };
}

export function ChangePasswordModal({ onClose }: { onClose: () => void }) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const newPwd = Form.useWatch<string | undefined>('newPassword', form);
  const mutation = useChangePassword();
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);
  const [stepUpToken, setStepUpToken] = useState<string | null>(null);

  const score = useMemo(() => pwdScore(newPwd ?? ''), [newPwd]);
  const canSubmit = score.level >= 2;

  const submit = () => {
    if (!stepUpToken) return;
    void form.validateFields().then((vals) => {
      if (vals.newPassword !== vals.confirmPassword) {
        form.setFields([{ name: 'confirmPassword', errors: ['两次输入不一致'] }]);
        return;
      }
      setSubmitting(true);
      mutation.mutate(
        { stepUpToken, newPassword: vals.newPassword },
        {
          onSuccess: () => {
            void message.success('密码已修改，请重新登录');
            // 后端会强制全下线，前端清本地态 + 跳登录页
            void (async () => {
              try {
                await authService.logout();
              } catch {
                // token 此时已失效是预期，吞掉
              }
              useAuthStore.getState().clear();
              onClose();
              await navigate('/login', { replace: true });
            })();
          },
          onError: (err) => {
            setSubmitting(false);
            // 票据过期（401）时退回验证步重新验证
            setStepUpToken(null);
            void message.error(describeApiError(err, '修改失败'));
          },
        },
      );
    });
  };

  return (
    <Modal
      open
      title="修改密码（运营账号）"
      onCancel={onClose}
      onOk={submit}
      okText="确认修改并重新登录"
      cancelText="取消"
      footer={stepUpToken ? undefined : null}
      okButtonProps={{ disabled: !canSubmit || submitting }}
      confirmLoading={submitting}
      destroyOnHidden
    >
      {!stepUpToken ? (
        <StepUpGate title="修改密码前请先验证身份" onVerified={setStepUpToken} />
      ) : (
        <>
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message="提交后会强制下线所有设备（含当前），需重新登录"
          />
          <Form<FormValues> form={form} layout="vertical">
            <Form.Item
              label="新密码"
              name="newPassword"
              rules={[
                { required: true, message: '请输入新密码' },
                { max: 128, message: '新密码最长 128 位' },
                { min: 8, message: '至少 8 位' },
              ]}
            >
              <Input.Password autoComplete="new-password" autoFocus />
            </Form.Item>
            {newPwd ? (
              <div style={{ marginBottom: 12, marginTop: -8 }}>
                <Progress
                  percent={score.percent}
                  size="small"
                  showInfo={false}
                  strokeColor={score.color}
                />
                <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 2 }}>
                  强度：<span style={{ color: score.color }}>{score.label}</span>
                  <span style={{ marginLeft: 8 }}>至少 8 位 · 含大写/小写/数字/符号中的 3 种</span>
                </div>
              </div>
            ) : null}
            <Form.Item
              label="确认新密码"
              name="confirmPassword"
              dependencies={['newPassword']}
              rules={[{ required: true, message: '请再次输入新密码' }]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
          </Form>
        </>
      )}
    </Modal>
  );
}
