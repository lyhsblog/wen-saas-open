import { Skeleton } from 'antd';
import { CountChip } from '@wen-saas/shared-ui';
import { FieldRow, SectionCard } from '../components/SectionCard';
import { useTeamsRolesQuery } from '../profileQueries';

export function TeamsRolesSection() {
  const { data: team, isLoading } = useTeamsRolesQuery();
  const roles = team?.roles ?? [];
  return (
    <SectionCard
      id="team"
      title="团队与角色"
      hint="由超管在「系统管理 → 团队成员 / 角色权限」中维护。"
    >
      {isLoading ? (
        <Skeleton active paragraph={{ rows: 3 }} />
      ) : (
        <>
          <FieldRow
            label="平台角色"
            value={
              roles.length === 0 ? (
                '—'
              ) : (
                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                  {roles.map((r) => (
                    <CountChip key={r.id ?? r.code}>{r.name ?? r.code}</CountChip>
                  ))}
                </div>
              )
            }
          />
          <FieldRow
            label="数据范围"
            value={team?.dataScope ?? '全部数据'}
            hint="运营后台不做内部数据隔离"
          />
          <FieldRow label="账号状态" value={team?.accountStatus} />
        </>
      )}
    </SectionCard>
  );
}
