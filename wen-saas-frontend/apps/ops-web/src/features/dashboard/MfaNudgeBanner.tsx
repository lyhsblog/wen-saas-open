import { useState } from 'react';
import { Alert, Button } from 'antd';
import { useNavigate } from 'react-router';
import { useSecurityQuery } from '../profile/profileQueries';

const DISMISS_KEY = 'wen-ops:mfa-nudge-dismissed';

/** 未开启登录二步验证的引导提示条：可关闭（localStorage 记住），「去开启」深链到安全设置。 */
export function MfaNudgeBanner() {
  const [dismissed, setDismissed] = useState(() => localStorage.getItem(DISMISS_KEY) === '1');
  const navigate = useNavigate();
  const { data: security } = useSecurityQuery();

  if (dismissed || security?.mfaEnabled !== false) return null;
  return (
    <Alert
      type="info"
      showIcon
      closable
      onClose={() => {
        localStorage.setItem(DISMISS_KEY, '1');
        setDismissed(true);
      }}
      message="建议开启登录二步验证"
      description="开启后登录需额外验证一步，可有效防止密码泄露导致的账号盗用。"
      action={
        <Button size="small" type="primary" onClick={() => void navigate('/profile?section=security')}>
          去开启
        </Button>
      }
    />
  );
}
