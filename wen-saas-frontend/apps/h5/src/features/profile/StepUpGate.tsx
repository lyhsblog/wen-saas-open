import { useMemo, useState } from 'react';
import { Button, Input, Radio, Space, Toast } from 'antd-mobile';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ApiError, type StepUpMethod } from '@wen-saas/api-client';
import { stepUp } from '../../lib/api';

// 暂无短信认证资源，SMS 通道统一置「暂未开放」；接入后翻开关即恢复（与登录页 AUTH_RESOURCES_READY 同口径）
const SMS_READY = false;

const CHANNELS: { v: StepUpMethod; t: string; needsCode: boolean }[] = [
  { v: 'TOTP', t: '身份验证器 (TOTP)', needsCode: false },
  { v: 'SMS', t: '短信验证码', needsCode: true },
  { v: 'EMAIL', t: '邮箱验证码', needsCode: true },
];

const errMsg = (e: unknown, fb: string) =>
  e instanceof ApiError ? (e.problem.detail ?? fb) : fb;

/** H5 敏感操作前置身份验证：选一条已配置通道验证身份，成功后回调 step-up 票据。 */
export function StepUpGate({
  title = '验证身份',
  onVerified,
}: {
  title?: string;
  onVerified: (stepUpToken: string) => void;
}) {
  const { data: channels } = useQuery({
    queryKey: ['h5', 'stepup', 'channels'],
    queryFn: () => stepUp.getChannels(),
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
    mutationFn: (m: StepUpMethod) => stepUp.challenge(m),
    onSuccess: (r) => {
      setMasked(r.maskedTarget ?? null);
      setSent(true);
    },
    onError: (e) => Toast.show({ content: errMsg(e, '发送验证码失败') }),
  });

  const verify = useMutation({
    mutationFn: () => stepUp.verify(method as StepUpMethod, code.trim()),
    onSuccess: (t) => {
      if (t.stepUpToken) onVerified(t.stepUpToken);
      else Toast.show({ content: '验证失败' });
    },
    onError: (e) => Toast.show({ content: errMsg(e, '验证失败') }),
  });

  const needsCode = method ? CHANNELS.find((c) => c.v === method)?.needsCode : false;

  return (
    <div style={{ padding: 16 }}>
      <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 12 }}>
        {title}：请选择一种已配置的通道完成验证。
      </div>
      <Radio.Group
        value={method ?? ''}
        onChange={(v) => {
          setPicked(v as StepUpMethod);
          setSent(false);
          setMasked(null);
          setCode('');
        }}
      >
        <Space direction="vertical" block>
          {CHANNELS.map((c) => (
            <Radio key={c.v} value={c.v} disabled={!avail[c.v]}>
              {c.t}
              {!avail[c.v] && (
                <span style={{ fontSize: 12, color: 'var(--text-tertiary)', marginLeft: 6 }}>
                  （{disabledHint(c.v)}）
                </span>
              )}
            </Radio>
          ))}
        </Space>
      </Radio.Group>

      {method && (
        <div style={{ marginTop: 16 }}>
          {needsCode && !sent ? (
            <Button
              block
              color="primary"
              loading={challenge.isPending}
              onClick={() => challenge.mutate(method)}
            >
              发送验证码
            </Button>
          ) : (
            <>
              <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 10 }}>
                {method === 'TOTP'
                  ? '请打开 Microsoft Authenticator，输入当前 6 位动态码。'
                  : `验证码已发送至 ${masked ?? '账号绑定通道'}，请在 5 分钟内输入。`}
              </div>
              <Input
                value={code}
                onChange={(v) => setCode(v.replace(/\D/g, '').slice(0, 6))}
                placeholder="请输入 6 位验证码"
                type="tel"
                maxLength={6}
                style={{ '--text-align': 'center', letterSpacing: '0.3em' }}
              />
              <Button
                block
                color="primary"
                style={{ marginTop: 12 }}
                disabled={code.trim().length < 4}
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
