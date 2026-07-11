import { useState } from 'react';
import { App, Input, Modal } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { describeApiError } from '@wen-saas/api-client';
import { useAuthStore } from '@wen-saas/auth';
import { useNavigate } from 'react-router';
import { profileApi } from './profileApi';
import { StepUpGate } from './StepUpGate';

/** 密码强度评估（对齐后端：≥8 位 + 含大小写/数字/符号；返回 0-4 等级）。 */
function pwdStrength(pwd: string): { level: number; label: string; color: string } {
  if (!pwd) return { level: 0, label: '', color: 'var(--bg-muted)' };
  let score = 0;
  if (pwd.length >= 8) score++;
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) score++;
  if (/\d/.test(pwd)) score++;
  if (/[^A-Za-z0-9]/.test(pwd)) score++;
  const map = [
    { label: '太弱', color: 'var(--danger)' },
    { label: '较弱', color: 'var(--danger)' },
    { label: '中等', color: 'var(--warning)' },
    { label: '较强', color: 'var(--success)' },
    { label: '很强', color: 'var(--success)' },
  ];
  const m = map[score] ?? map[0]!;
  return { level: score, label: m.label, color: m.color };
}

export function ChangePasswordModal({ onClose }: { onClose: () => void }) {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const clearAuth = useAuthStore((s) => s.clear);
  const [stepUpToken, setStepUpToken] = useState<string | null>(null);
  const [p1, setP1] = useState('');
  const [p2, setP2] = useState('');
  const strength = pwdStrength(p1);
  const canSubmit = Boolean(stepUpToken && p1 && p1 === p2 && strength.level >= 3);

  const mutation = useMutation({
    mutationFn: () => profileApi.changePassword({ stepUpToken: stepUpToken as string, newPassword: p1 }),
    onSuccess: async () => {
      message.success('密码已修改，请重新登录');
      // 后端改密后强制下线全部设备，本地清登录态跳登录页。
      clearAuth();
      await navigate('/login', { replace: true });
    },
    onError: (e) => {
      setStepUpToken(null); // 票据可能已过期，退回验证步
      message.error(describeApiError(e, '修改失败'));
    },
  });

  return (
    <Modal
      title="修改密码"
      open
      onCancel={onClose}
      okText="确认修改并重新登录"
      cancelText="取消"
      okButtonProps={{ disabled: !canSubmit, loading: mutation.isPending }}
      onOk={() => mutation.mutate()}
      footer={stepUpToken ? undefined : null}
      destroyOnHidden
    >
      {!stepUpToken ? (
        <div style={{ paddingTop: 8 }}>
          <StepUpGate title="修改密码前请先验证身份" onVerified={setStepUpToken} />
        </div>
      ) : (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14, paddingTop: 8 }}>
        <label style={{ fontSize: 13 }}>
          <div style={{ marginBottom: 6, color: 'var(--text-secondary)' }}>
            新密码{' '}
            <span style={{ color: 'var(--text-tertiary)' }}>
              · ≥ 8 位，含大小写/数字/符号中的 3 类
            </span>
          </div>
          <Input.Password value={p1} onChange={(e) => setP1(e.target.value)} />
        </label>
        {p1 && (
          <div style={{ display: 'flex', gap: 4, alignItems: 'center', marginTop: -6 }}>
            {[0, 1, 2, 3].map((i) => (
              <span
                key={i}
                style={{
                  flex: 1,
                  height: 4,
                  borderRadius: 2,
                  background: i < strength.level ? strength.color : 'var(--bg-muted)',
                }}
              />
            ))}
            <span
              style={{ fontSize: 11, color: 'var(--text-tertiary)', width: 40, textAlign: 'right' }}
            >
              {strength.label}
            </span>
          </div>
        )}
        <label style={{ fontSize: 13 }}>
          <div style={{ marginBottom: 6, color: 'var(--text-secondary)' }}>确认新密码</div>
          <Input.Password value={p2} onChange={(e) => setP2(e.target.value)} />
          {p2 && p1 !== p2 && (
            <div style={{ fontSize: 12, color: 'var(--danger)', marginTop: 6 }}>两次输入不一致</div>
          )}
        </label>
        <div
          style={{
            padding: 12,
            background: 'var(--warning-bg)',
            borderRadius: 8,
            fontSize: 12,
            color: 'var(--warning)',
          }}
        >
          修改成功后将立即下线所有设备，需要重新登录。
        </div>
      </div>
      )}
    </Modal>
  );
}
