import { useState } from 'react';
import { App, Button, Input, Modal } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { describeApiError } from '@wen-saas/api-client';
import { profileApi } from './profileApi';
import { StepUpGate } from './StepUpGate';

const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/**
 * 改邮箱（租户端自助）：step-up 验证身份 → 输入新邮箱 → 新邮箱验证码确认（后端做租户内唯一性校验）。
 */
export function EmailChangeModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { message } = App.useApp();
  const [stepUpToken, setStepUpToken] = useState<string | null>(null);
  const [masked, setMasked] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [newEmail, setNewEmail] = useState('');
  const [newSent, setNewSent] = useState(false);

  const sendNew = useMutation({
    mutationFn: () => profileApi.sendNewEmailCode(stepUpToken as string, newEmail.trim()),
    onSuccess: (s) => {
      setMasked(s.maskedTarget ?? null);
      setNewSent(true);
    },
    onError: (e) => message.error(describeApiError(e, '发送失败')),
  });
  const confirmNew = useMutation({
    mutationFn: () => profileApi.confirmNewEmail(stepUpToken as string, code.trim()),
    onSuccess: () => {
      message.success('邮箱已更新');
      onDone();
      onClose();
    },
    onError: (e) => message.error(describeApiError(e, '修改失败')),
  });

  return (
    <Modal title="修改邮箱" open width={520} onCancel={onClose} footer={null} destroyOnHidden>
      {!stepUpToken ? (
        <StepUpGate title="修改邮箱前请先验证身份" onVerified={setStepUpToken} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
            身份已验证，请输入<strong>新邮箱</strong>并完成验证。
          </div>
          <Input
            value={newEmail}
            onChange={(e) => setNewEmail(e.target.value.trim())}
            placeholder="请输入新邮箱"
            disabled={newSent}
            maxLength={128}
          />
          {newSent && (
            <>
              <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
                验证码已发送至 <b>{masked ?? newEmail}</b>。
              </div>
              <Input
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="请输入 6 位验证码"
                size="large"
                maxLength={6}
                style={{
                  fontFamily: 'var(--font-mono)',
                  letterSpacing: '0.3em',
                  textAlign: 'center',
                }}
                autoFocus
              />
            </>
          )}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 4 }}>
            <Button onClick={onClose}>取消</Button>
            {!newSent ? (
              <Button
                type="primary"
                disabled={!EMAIL_RE.test(newEmail.trim())}
                loading={sendNew.isPending}
                onClick={() => sendNew.mutate()}
              >
                发送验证码
              </Button>
            ) : (
              <Button
                type="primary"
                disabled={code.trim().length < 4}
                loading={confirmNew.isPending}
                onClick={() => confirmNew.mutate()}
              >
                确认修改
              </Button>
            )}
          </div>
        </div>
      )}
    </Modal>
  );
}
