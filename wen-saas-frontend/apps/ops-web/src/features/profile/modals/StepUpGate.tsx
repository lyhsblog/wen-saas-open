import { useMemo, useState } from 'react';
import { Alert, App, Button, Input, Radio, Tooltip } from 'antd';
import { useMutation, useQuery } from '@tanstack/react-query';
import { describeApiError, stepUpApi, type StepUpMethod } from '@wen-saas/api-client';
import { http } from '../../../lib/http';

const api = stepUpApi(http);

// 暂无短信认证资源，SMS 通道统一置「暂未开放」；接入后翻开关即恢复（与登录页 AUTH_RESOURCES_READY 同口径）
const SMS_READY = false;

type ChannelDef = {
  v: StepUpMethod;
  t: string;
  needsCode: boolean; // SMS/EMAIL 需发码，TOTP 直接输动态码
};

const CHANNELS: ChannelDef[] = [
  { v: 'TOTP', t: '身份验证器 (TOTP)', needsCode: false },
  { v: 'SMS', t: '短信验证码', needsCode: true },
  { v: 'EMAIL', t: '邮箱验证码', needsCode: true },
];

/**
 * 敏感操作前置身份验证网关：选一条已配置通道验证身份，成功后回调 step-up 票据。
 * 未配置通道置灰 + tooltip 提示。运营账号强制 MFA，通常三通道可用。
 */
export function StepUpGate({
  title = '验证身份',
  onVerified,
}: {
  title?: string;
  onVerified: (stepUpToken: string) => void;
}) {
  const { message } = App.useApp();
  const { data: channels, isLoading } = useQuery({
    queryKey: ['stepup', 'channels'],
    queryFn: () => api.getChannels(),
    staleTime: 0,
  });

  const [picked, setPicked] = useState<StepUpMethod | null>(null);
  const [masked, setMasked] = useState<string | null>(null);
  const [sent, setSent] = useState(false);
  const [code, setCode] = useState('');

  const avail = useMemo(
    () => ({
      TOTP: channels?.totp?.available ?? false,
      SMS: SMS_READY && (channels?.sms?.available ?? false),
      EMAIL: channels?.email?.available ?? false,
    }),
    [channels],
  );

  // 默认选中首个可用通道（派生而非 effect 里 setState）
  const firstAvailable = useMemo(() => CHANNELS.find((c) => avail[c.v])?.v ?? null, [avail]);
  const method = picked ?? firstAvailable;

  const disabledHint = (m: StepUpMethod) =>
    m === 'TOTP'
      ? '未绑定身份验证器'
      : m === 'SMS'
        ? SMS_READY
          ? '账号未绑定手机号'
          : '暂未开放(短信，暂无认证资源)'
        : '账号未绑定邮箱';

  const challenge = useMutation({
    mutationFn: (m: StepUpMethod) => api.challenge(m),
    onSuccess: (r) => {
      setMasked(r.maskedTarget ?? null);
      setSent(true);
    },
    onError: (e) => void message.error(describeApiError(e, '发送验证码失败')),
  });

  const verify = useMutation({
    mutationFn: () => api.verify(method as StepUpMethod, code.trim()),
    onSuccess: (t) => {
      if (t.stepUpToken) onVerified(t.stepUpToken);
      else void message.error('验证失败：未获取到票据');
    },
    onError: (e) => void message.error(describeApiError(e, '验证失败')),
  });

  const needsCode = method ? CHANNELS.find((c) => c.v === method)?.needsCode : false;
  const codeReady = method === 'TOTP' || sent;

  return (
    <div>
      <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 12 }}>
        {title}：请选择一种已配置的通道完成验证。
      </div>
      <Radio.Group
        value={method ?? undefined}
        onChange={(e) => {
          setPicked(e.target.value as StepUpMethod);
          setSent(false);
          setMasked(null);
          setCode('');
        }}
        style={{ display: 'flex', flexDirection: 'column', gap: 8, width: '100%' }}
      >
        {CHANNELS.map((c) => {
          const on = avail[c.v];
          const radio = (
            <Radio
              key={c.v}
              value={c.v}
              disabled={!on || isLoading}
              style={{
                padding: 12,
                border: `1.5px solid ${method === c.v ? 'var(--brand, #2b5fff)' : 'var(--border, #e5e7eb)'}`,
                borderRadius: 8,
                margin: 0,
                opacity: on ? 1 : 0.55,
              }}
            >
              {c.t}
              {!on && (
                <span style={{ fontSize: 12, color: 'var(--text-tertiary)', marginLeft: 6 }}>
                  （{disabledHint(c.v)}）
                </span>
              )}
            </Radio>
          );
          return on ? (
            radio
          ) : (
            <Tooltip key={c.v} title={disabledHint(c.v)}>
              {radio}
            </Tooltip>
          );
        })}
      </Radio.Group>

      {method && (
        <div style={{ marginTop: 16 }}>
          {needsCode && !sent ? (
            <Button
              type="primary"
              loading={challenge.isPending}
              onClick={() => challenge.mutate(method)}
            >
              发送验证码
            </Button>
          ) : (
            <>
              {method === 'TOTP' ? (
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message="请打开 Microsoft Authenticator，输入当前 6 位动态码。"
                />
              ) : (
                <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 12 }}>
                  验证码已发送至 <b>{masked ?? '账号绑定通道'}</b>，请在 5 分钟内输入。
                  <Button
                    type="link"
                    size="small"
                    loading={challenge.isPending}
                    onClick={() => challenge.mutate(method)}
                  >
                    重新发送
                  </Button>
                </div>
              )}
              <Input
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="请输入 6 位验证码"
                size="large"
                style={{ fontFamily: 'var(--font-mono)', letterSpacing: '0.3em', textAlign: 'center' }}
                maxLength={6}
                autoFocus
              />
              <Button
                type="primary"
                block
                style={{ marginTop: 12 }}
                disabled={code.trim().length < 4 || !codeReady}
                loading={verify.isPending}
                onClick={() => verify.mutate()}
              >
                验证身份
              </Button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
