import { useState } from 'react';
import { App, Button, Skeleton } from 'antd';
import { CountChip } from '@wen-saas/shared-ui';
import { EditOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { FieldRow, SectionCard } from '../components/SectionCard';
import { GENDER_LABEL, SMS_CLOSED_TAG, SMS_READY } from '../constants';
import { EditBasicModal } from '../modals/EditBasicModal';
import { OpsMobileChangeModal } from '../modals/OpsMobileChangeModal';
import { profileKeys, useBasicQuery } from '../profileQueries';

// 本人资料页联系方式不脱敏（自己的信息无需对自己脱敏）；他人视角（成员列表等）仍走通用掩码。
export function BasicSection() {
  const { modal } = App.useApp();
  const qc = useQueryClient();
  const { data: basic, isLoading } = useBasicQuery();
  const [editing, setEditing] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const hasMobile = !!basic?.mobile;

  const onMobileAction = () => {
    if (!SMS_READY) {
      modal.info({
        title: hasMobile ? '暂不支持修改手机号' : '暂不支持绑定手机号',
        content:
          '更换或绑定手机号需要向新号码发送短信验证码确认归属，平台暂未接入短信服务，接入后将自动开放。',
        okText: '我知道了',
      });
      return;
    }
    setMobileOpen(true);
  };

  return (
    <SectionCard
      id="basic"
      title="基本信息"
      extra={
        <Button
          size="small"
          icon={<EditOutlined />}
          onClick={() => setEditing(true)}
          disabled={isLoading}
        >
          编辑
        </Button>
      }
    >
      {isLoading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : (
        <>
          <FieldRow label="姓名" value={basic?.realName} />
          <FieldRow
            label="账号"
            value={basic?.username}
            mono
            locked
            hint="由超管开通时设定，不可自助修改"
          />
          <FieldRow label="员工编号" value={basic?.empNo} mono locked />
          <FieldRow
            label="企业邮箱"
            value={basic?.email}
            mono
            hint="用于登录验证与安全通知，修改请联系超管"
          />
          <FieldRow
            label="手机号"
            value={
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontFamily: 'var(--font-mono, ui-monospace, monospace)' }}>
                  {basic?.mobile ?? '未绑定'}
                </span>
                {!SMS_READY && <CountChip>{SMS_CLOSED_TAG}</CountChip>}
              </span>
            }
            hint="用于登录验证与安全通知"
            action={
              <Button size="small" onClick={onMobileAction}>
                {hasMobile ? '修改' : '绑定'}
              </Button>
            }
          />
          <FieldRow label="性别" value={GENDER_LABEL[basic?.gender ?? 0] ?? '未知'} />
          <FieldRow label="生日" value={basic?.birthday} />
        </>
      )}

      {editing ? <EditBasicModal basic={basic} onClose={() => setEditing(false)} /> : null}
      {mobileOpen ? (
        <OpsMobileChangeModal
          onClose={() => setMobileOpen(false)}
          onDone={() => void qc.invalidateQueries({ queryKey: profileKeys.basic() })}
        />
      ) : null}
    </SectionCard>
  );
}
