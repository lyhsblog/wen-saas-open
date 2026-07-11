import { useEffect, useState } from 'react';
import { Alert, App, Button, Input, Modal, Skeleton } from 'antd';
import { useMutation, useQuery } from '@tanstack/react-query';
import { describeApiError } from '@wen-saas/api-client';
import { profileApi } from './profileApi';

/**
 * 开启登录二步验证：通道服务端推导（已绑验证器 → 输动态码；否则邮箱验证码），
 * 验证通过后开启并展示 8 组一次性恢复码。
 */
export function MfaEnableModal({
  onClose,
  onEnabled,
}: {
  onClose: () => void;
  onEnabled: () => void;
}) {
  const { message } = App.useApp();
  const [code, setCode] = useState('');
  const [codes, setCodes] = useState<string[]>([]);

  // 打开即发起：推导通道（EMAIL 会直接发码）
  const {
    data: challenge,
    isLoading,
    error,
    refetch,
    isRefetching,
  } = useQuery({
    queryKey: ['tenant', 'mfa-enable-start'],
    queryFn: () => profileApi.mfaEnableStart(),
    staleTime: 0,
    gcTime: 0,
    retry: false,
  });

  useEffect(() => {
    if (error) message.error(describeApiError(error, '发起开启失败'));
  }, [error, message]);

  const confirm = useMutation({
    mutationFn: () => profileApi.mfaEnableConfirm(code.trim()),
    onSuccess: (res) => {
      setCodes(res.recoveryCodes ?? []);
    },
    onError: (e) => message.error(describeApiError(e, '验证码错误')),
  });

  const showingCodes = codes.length > 0;
  const isTotp = challenge?.method === 'TOTP';

  return (
    <Modal
      title="开启登录二步验证"
      open
      width={520}
      onCancel={() => {
        if (!showingCodes) onClose();
      }}
      closable={!showingCodes}
      mask={{ closable: !showingCodes }}
      footer={
        showingCodes ? (
          <Button
            type="primary"
            onClick={() => {
              onEnabled();
              onClose();
            }}
          >
            我已妥善保存
          </Button>
        ) : (
          <>
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              disabled={code.trim().length < 6 || !challenge}
              loading={confirm.isPending}
              onClick={() => confirm.mutate()}
            >
              验证并开启
            </Button>
          </>
        )
      }
      destroyOnHidden
    >
      {showingCodes ? (
        <>
          <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 4 }}>请妥善保存以下恢复码</div>
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 12 }}>
            恢复码用于在丢失验证通道时兜底登录，每个码只能使用一次。关闭窗口后不可再次查看。
          </div>
          <div
            style={{
              padding: 16,
              background: 'var(--bg-muted)',
              borderRadius: 8,
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: 8,
              fontFamily: 'var(--font-mono)',
              fontSize: 14,
              letterSpacing: '0.05em',
            }}
          >
            {codes.map((c) => (
              <div
                key={c}
                style={{
                  padding: '6px 10px',
                  background: 'var(--bg-elev)',
                  border: '1px solid var(--border)',
                  borderRadius: 6,
                  textAlign: 'center',
                }}
              >
                {c}
              </div>
            ))}
          </div>
          <Alert
            type="warning"
            showIcon
            style={{ marginTop: 14 }}
            message="这些恢复码仅此一次完整显示，请截图或抄写保存到安全位置。"
          />
        </>
      ) : isLoading || !challenge ? (
        <Skeleton active paragraph={{ rows: 2 }} />
      ) : (
        <>
          <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 16 }}>
            {isTotp ? (
              '开启后每次登录需额外验证。请输入身份验证器显示的 6 位动态码确认。'
            ) : (
              <>
                开启后每次登录需额外验证。验证码已发送至{' '}
                <b>{challenge.maskedTarget ?? '账号绑定邮箱'}</b>，请在 5 分钟内输入。
                <Button
                  type="link"
                  size="small"
                  loading={isRefetching}
                  onClick={() => void refetch()}
                >
                  重新发送
                </Button>
              </>
            )}
          </div>
          <Input
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            placeholder="请输入 6 位验证码"
            size="large"
            style={{ fontFamily: 'var(--font-mono)', letterSpacing: '0.3em', textAlign: 'center' }}
            maxLength={6}
            autoFocus
          />
        </>
      )}
    </Modal>
  );
}
