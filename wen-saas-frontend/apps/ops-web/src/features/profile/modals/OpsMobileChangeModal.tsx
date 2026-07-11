import { useState } from 'react';
import { App, Button, Input, Modal } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { describeApiError, opsProfileApi } from '@wen-saas/api-client';
import { http } from '../../../lib/http';
import { StepUpGate } from './StepUpGate';

const api = opsProfileApi(http);

/**
 * 运营个人中心 · 改手机号：step-up 验证身份 → 输入新手机号 → 新号验证码确认。
 * 手机号是登录凭据；身份复验统一走 step-up（任选 TOTP/短信/邮件之一）。
 */
export function OpsMobileChangeModal({
  onClose,
  onDone,
}: {
  onClose: () => void;
  onDone: () => void;
}) {
  const { message } = App.useApp();
  const [stepUpToken, setStepUpToken] = useState<string | null>(null);
  const [masked, setMasked] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [newMobile, setNewMobile] = useState('');
  const [newSent, setNewSent] = useState(false);

  const sendNew = useMutation({
    mutationFn: () => api.sendNewMobileCode(stepUpToken as string, newMobile.trim()),
    onSuccess: (s) => {
      setMasked(s.maskedTarget ?? null);
      setNewSent(true);
    },
    onError: (e) => void message.error(describeApiError(e, '发送失败')),
  });
  const confirmNew = useMutation({
    mutationFn: () => api.confirmNewMobile(stepUpToken as string, code.trim()),
    onSuccess: () => {
      void message.success('手机号已更新');
      onDone();
      onClose();
    },
    onError: (e) => void message.error(describeApiError(e, '验证码错误')),
  });

  const codeInput = (
    <Input
      value={code}
      onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
      placeholder="请输入 6 位验证码"
      size="large"
      maxLength={6}
      style={{ fontFamily: 'var(--font-mono)', letterSpacing: '0.3em', textAlign: 'center' }}
      autoFocus
    />
  );

  return (
    <Modal
      title="修改手机号（运营账号）"
      open
      width={520}
      onCancel={onClose}
      footer={null}
      destroyOnHidden
    >
      {!stepUpToken ? (
        <StepUpGate title="修改手机号前请先验证身份" onVerified={setStepUpToken} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
            身份已验证，请输入<strong>新手机号</strong>并完成验证。
          </div>
          <Input
            value={newMobile}
            onChange={(e) => setNewMobile(e.target.value.replace(/\D/g, '').slice(0, 11))}
            placeholder="请输入新手机号"
            disabled={newSent}
            maxLength={11}
          />
          {newSent && (
            <>
              <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
                验证码已发送至 <b>{masked ?? newMobile}</b>。
              </div>
              {codeInput}
            </>
          )}
          {!newSent ? (
            <FooterBtns
              onCancel={onClose}
              primaryLabel="发送验证码"
              primaryDisabled={!/^1[3-9]\d{9}$/.test(newMobile.trim())}
              primaryLoading={sendNew.isPending}
              onPrimary={() => sendNew.mutate()}
            />
          ) : (
            <FooterBtns
              onCancel={onClose}
              onResend={() => sendNew.mutate()}
              resending={sendNew.isPending}
              primaryLabel="确认修改"
              primaryDisabled={code.trim().length < 4}
              primaryLoading={confirmNew.isPending}
              onPrimary={() => confirmNew.mutate()}
            />
          )}
        </div>
      )}
    </Modal>
  );
}

function FooterBtns({
  onCancel,
  onResend,
  resending,
  primaryLabel,
  primaryDisabled,
  primaryLoading,
  onPrimary,
}: {
  onCancel: () => void;
  onResend?: () => void;
  resending?: boolean;
  primaryLabel: string;
  primaryDisabled?: boolean;
  primaryLoading?: boolean;
  onPrimary: () => void;
}) {
  return (
    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 4 }}>
      <Button onClick={onCancel}>取消</Button>
      {onResend && (
        <Button onClick={onResend} loading={resending ?? false}>
          重发验证码
        </Button>
      )}
      <Button
        type="primary"
        disabled={primaryDisabled ?? false}
        loading={primaryLoading ?? false}
        onClick={onPrimary}
      >
        {primaryLabel}
      </Button>
    </div>
  );
}
