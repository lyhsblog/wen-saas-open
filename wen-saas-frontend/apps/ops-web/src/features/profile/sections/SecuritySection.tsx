import { useState } from 'react';
import { App, Button, Modal, Skeleton, Switch } from 'antd';
import { CountChip, StatusPill, toneBadge } from '@wen-saas/shared-ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { describeApiError, opsProfileApi, stepUpApi } from '@wen-saas/api-client';
import { http } from '../../../lib/http';
import { FieldRow, SectionCard } from '../components/SectionCard';
import { MFA_METHOD_LABEL, SMS_CLOSED_TAG, SMS_READY } from '../constants';
import { ChangePasswordModal } from '../modals/ChangePasswordModal';
import { OpsMfaEnableModal } from '../modals/OpsMfaEnableModal';
import { StepUpGate } from '../modals/StepUpGate';
import { TotpBindModal } from '../modals/TotpBindModal';
import { profileKeys, useSecurityQuery } from '../profileQueries';

const stepUp = stepUpApi(http);
const api = opsProfileApi(http);

function fmtDuration(seconds: number | undefined): string {
  if (!seconds || seconds <= 0) return '—';
  if (seconds >= 3600) return `${Math.round(seconds / 3600)} 小时`;
  if (seconds >= 60) return `${Math.round(seconds / 60)} 分钟`;
  return `${seconds} 秒`;
}

export function SecuritySection() {
  const { message } = App.useApp();
  const qc = useQueryClient();
  const { data: security, isLoading } = useSecurityQuery();
  // 身份验证方式总览：与敏感操作「验证身份」弹窗同一数据源，展示哪些方式当前可用
  const { data: channels } = useQuery({
    queryKey: ['stepup', 'channels'],
    queryFn: () => stepUp.getChannels(),
    staleTime: 0,
  });
  const [pwdOpen, setPwdOpen] = useState(false);
  const [enableOpen, setEnableOpen] = useState(false);
  const [disableOpen, setDisableOpen] = useState(false);
  const [bindOpen, setBindOpen] = useState(false);
  const mfaEnabled = security?.mfaEnabled === true;
  const mfaRequired = security?.mfaRequired === true;
  const mfaMethodLabel = security?.mfaMethod ? MFA_METHOD_LABEL[security.mfaMethod] : null;
  const totpBound = channels?.totp?.available === true;

  const invalidateSecurity = () => {
    void qc.invalidateQueries({ queryKey: profileKeys.security() });
    void qc.invalidateQueries({ queryKey: ['stepup', 'channels'] });
  };

  const disable = useMutation({
    mutationFn: (stepUpToken: string) => api.mfaDisable(stepUpToken),
    onSuccess: () => {
      void message.success('登录二步验证已关闭');
      setDisableOpen(false);
      invalidateSecurity();
    },
    onError: (e) => void message.error(describeApiError(e, '关闭失败')),
  });

  if (isLoading) {
    return (
      <SectionCard id="security" title="安全设置">
        <Skeleton active paragraph={{ rows: 5 }} />
      </SectionCard>
    );
  }
  return (
    <SectionCard id="security" title="安全设置">
      <FieldRow
        label="登录密码"
        value="••••••••"
        hint="修改前需先验证身份；修改后将强制下线所有设备，需重新登录。"
        action={
          <Button size="small" onClick={() => setPwdOpen(true)}>
            修改密码
          </Button>
        }
      />
      <FieldRow
        label="登录二步验证"
        value={
          <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <Switch
              checked={mfaEnabled}
              disabled={mfaRequired && mfaEnabled}
              onChange={(next) => {
                if (next) setEnableOpen(true);
                else setDisableOpen(true);
              }}
            />
            {mfaEnabled && mfaMethodLabel ? (
              <StatusPill badge={toneBadge('success', mfaMethodLabel)} />
            ) : null}
            {mfaRequired ? (
              <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>平台强制开启</span>
            ) : null}
          </span>
        }
        hint="开启后每次登录需额外验证一步；验证方式自动选用最强可用项（已绑验证器用动态码，否则邮箱验证码）。"
      />
      <FieldRow
        label="身份验证方式"
        value={
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <span
              style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}
            >
              <span>身份验证器 (TOTP)</span>
              <StatusPill
                badge={totpBound ? toneBadge('success', '已绑定') : toneBadge('neutral', '未绑定')}
              />
              <Button
                type="link"
                size="small"
                style={{ padding: 0 }}
                onClick={() => setBindOpen(true)}
              >
                {totpBound ? '重新绑定' : '绑定'}
              </Button>
            </span>
            <span
              style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}
            >
              <span>短信验证码</span>
              {SMS_READY ? (
                <StatusPill
                  badge={
                    channels?.sms?.available
                      ? toneBadge('success', '可用')
                      : toneBadge('neutral', '未绑定手机号')
                  }
                />
              ) : (
                <CountChip>{SMS_CLOSED_TAG}</CountChip>
              )}
            </span>
            <span
              style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}
            >
              <span>邮箱验证码</span>
              <StatusPill
                badge={
                  channels?.email?.available
                    ? toneBadge('success', '可用')
                    : toneBadge('neutral', '未绑定邮箱')
                }
              />
            </span>
          </div>
        }
        hint="登录二步验证与敏感操作前的身份验证均使用这些方式。手机号、邮箱在「基本信息」中维护。"
      />
      <FieldRow
        label="会话超时"
        value={fmtDuration(security?.sessionTimeoutSeconds)}
        hint="与「系统管理 → 系统参数」同源，仅超管可改"
        locked
      />
      <FieldRow
        label="登录失败锁定"
        value={
          security?.loginLockMaxAttempts
            ? `${security.loginLockMaxAttempts} 次失败 / ${fmtDuration(security.loginLockWindowSeconds)} 窗口`
            : '—'
        }
        locked
      />

      {pwdOpen ? <ChangePasswordModal onClose={() => setPwdOpen(false)} /> : null}
      {enableOpen ? (
        <OpsMfaEnableModal onClose={() => setEnableOpen(false)} onDone={invalidateSecurity} />
      ) : null}
      {bindOpen ? (
        <TotpBindModal
          rebind={totpBound}
          onClose={() => setBindOpen(false)}
          onDone={invalidateSecurity}
        />
      ) : null}
      {disableOpen ? (
        <Modal
          title="关闭登录二步验证"
          open
          width={520}
          onCancel={() => setDisableOpen(false)}
          footer={null}
          destroyOnHidden
        >
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 12 }}>
            关闭后登录仅凭密码，恢复码将同时作废（验证器绑定保留）。
          </div>
          <StepUpGate title="关闭前请先验证身份" onVerified={(token) => disable.mutate(token)} />
        </Modal>
      ) : null}
    </SectionCard>
  );
}
