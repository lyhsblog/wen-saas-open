import { Skeleton } from 'antd';
import { formatDateTime, StatusPill, toneBadge } from '@wen-saas/shared-ui';
import { FieldRow, SectionCard } from '../components/SectionCard';
import { useSsoQuery } from '../profileQueries';

export function SsoSection() {
  const { data: sso, isLoading } = useSsoQuery();
  return (
    <SectionCard
      id="sso"
      title="企业 SSO"
      hint="单点登录由超管在团队成员页统一配置，成员不可自助解绑。"
    >
      {isLoading ? (
        <Skeleton active paragraph={{ rows: 2 }} />
      ) : (
        <>
          <FieldRow label="SSO 提供方" value={sso?.provider ?? '未启用'} locked />
          <FieldRow
            label="状态"
            value={
              <StatusPill
                badge={
                  sso?.enabled ? toneBadge('success', '已启用') : toneBadge('neutral', '未启用')
                }
              />
            }
            locked
          />
          <FieldRow label="上次同步时间" value={formatDateTime(sso?.lastSyncAt)} locked />
        </>
      )}
    </SectionCard>
  );
}
