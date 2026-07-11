import { useEffect, useState } from 'react';
import { App, Button, Input, Modal, QRCode, Skeleton } from 'antd';
import { useMutation, useQuery } from '@tanstack/react-query';
import { describeApiError } from '@wen-saas/api-client';
import { profileApi } from './profileApi';

/**
 * 绑定/重新绑定身份验证器：展示二维码 + 密钥 → 输入验证器动态码确认。
 * 绑定独立于登录二步验证开关：绑定后可用于登录验证与敏感操作身份验证，不重置恢复码。
 */
export function TotpBindModal({
  rebind,
  onClose,
  onDone,
}: {
  /** 已绑定时的重新绑定（覆盖旧密钥）。 */
  rebind: boolean;
  onClose: () => void;
  onDone: () => void;
}) {
  const { message } = App.useApp();
  const [code, setCode] = useState('');

  const {
    data: challenge,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['tenant', 'totp-bind-start'],
    queryFn: () => profileApi.totpBindStart(),
    staleTime: 0,
    gcTime: 0,
    retry: false,
  });

  useEffect(() => {
    if (error) message.error(describeApiError(error, '发起绑定失败'));
  }, [error, message]);

  const confirm = useMutation({
    mutationFn: () => profileApi.totpBindConfirm(code.trim()),
    onSuccess: () => {
      message.success(rebind ? '身份验证器已重新绑定' : '身份验证器已绑定');
      onDone();
      onClose();
    },
    onError: (e) => message.error(describeApiError(e, '验证码错误')),
  });

  return (
    <Modal
      title={rebind ? '重新绑定身份验证器' : '绑定身份验证器'}
      open
      width={520}
      onCancel={onClose}
      footer={
        <>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            disabled={code.trim().length < 6 || !challenge}
            loading={confirm.isPending}
            onClick={() => confirm.mutate()}
          >
            验证并绑定
          </Button>
        </>
      }
      destroyOnHidden
    >
      {rebind && (
        <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 12 }}>
          重新绑定将覆盖旧密钥，旧验证器立即失效；登录二步验证开关与恢复码不受影响。
        </div>
      )}
      {isLoading || !challenge ? (
        <Skeleton active paragraph={{ rows: 3 }} />
      ) : (
        <div
          style={{
            display: 'flex',
            gap: 14,
            alignItems: 'center',
            marginBottom: 16,
            padding: 14,
            background: 'var(--bg-muted)',
            borderRadius: 8,
          }}
        >
          {challenge.otpauthUri && (
            <div style={{ background: '#fff', padding: 6, borderRadius: 6, flexShrink: 0 }}>
              <QRCode value={challenge.otpauthUri} size={96} bordered={false} />
            </div>
          )}
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 6 }}>
              用 Microsoft Authenticator 等验证器扫码或手动添加密钥后，输入显示的 6 位动态码。
            </div>
            <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>密钥：</div>
            <code style={{ fontFamily: 'var(--font-mono)', fontSize: 12, wordBreak: 'break-all' }}>
              {challenge.totpSecret}
            </code>
          </div>
        </div>
      )}
      <Input
        value={code}
        onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
        placeholder="请输入 6 位动态码"
        size="large"
        style={{ fontFamily: 'var(--font-mono)', letterSpacing: '0.3em', textAlign: 'center' }}
        maxLength={6}
        autoFocus
      />
    </Modal>
  );
}
