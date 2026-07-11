import { Tooltip } from 'antd';
import { StatusPill, type StatusBadge } from '@wen-saas/shared-ui';

/**
 * 成员状态徽章单一真源：列表（MemberSection 状态列）、详情信息条与登录 Tab 共用，
 * 保证同一成员的同一状态在各处形态 / 语义色 / tooltip 完全一致。
 *
 * 口径：「停用」是可逆的中性态（neutral）而非错误；「主管理员/主」是身份标记不是状态，
 * 不进本渲染器（按 D1 口径走 CountChip brand）。
 */
type MemberStatusInput = {
  /** 1 在职 / 0 停用 */
  status: number;
  loginLocked?: boolean;
  /** 担任负责人的部门数；>0 且已停用 → 追加「负责人待处置」 */
  leaderDeptCount?: number;
};

function memberStatusBadges(m: MemberStatusInput): { badge: StatusBadge; tip?: string }[] {
  const out: { badge: StatusBadge; tip?: string }[] = [
    m.status === 1
      ? { badge: { kind: 'success', text: '在职', color: '' } }
      : { badge: { kind: 'neutral', text: '停用', color: '' } },
  ];
  if (m.status !== 1 && (m.leaderDeptCount ?? 0) > 0) {
    out.push({
      badge: { kind: 'warning', text: '负责人待处置', color: '' },
      tip: `仍是 ${m.leaderDeptCount} 个部门的负责人，停用未自动卸任，建议另行指派`,
    });
  }
  if (m.loginLocked) {
    out.push({
      badge: { kind: 'warning', text: '已锁定', color: '' },
      tip: '连续登录失败已临时锁定，可在操作区「解除锁定」立即解锁',
    });
  }
  return out;
}

export function MemberStatusPills(props: MemberStatusInput) {
  return (
    <span style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap', alignItems: 'center' }}>
      {memberStatusBadges(props).map((b, i) =>
        b.tip ? (
          <Tooltip key={i} title={b.tip}>
            <span>
              <StatusPill badge={b.badge} />
            </span>
          </Tooltip>
        ) : (
          <StatusPill key={i} badge={b.badge} />
        ),
      )}
    </span>
  );
}
