import { TeamOutlined } from '@ant-design/icons';
import type { DeptView } from '@wen-saas/domain';
import { MemberSection } from './MemberSection';

type Props = {
  treeData: DeptView[];
  onSelectMember: (memberId: string) => void;
};

/**
 * 「全部成员」视图：不锚定任何部门，查全租户成员（含未归属部门的成员，如初始管理员）。
 * 头部仿 DeptDetailPane 但无部门操作按钮；树为空的租户也从这里管理成员。
 */
export function AllMembersPane({ treeData, onSelectMember }: Props) {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <div
        style={{
          padding: '14px 24px',
          borderBottom: '1px solid var(--divider)',
          background: 'var(--bg-elev)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <TeamOutlined style={{ fontSize: 20, color: 'var(--brand)' }} />
          <span style={{ fontSize: 18, fontWeight: 600 }}>全部成员</span>
        </div>
        <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 8 }}>
          租户内全部成员，含未归属部门的成员
        </div>
      </div>
      <MemberSection dept={null} treeData={treeData} onSelectMember={onSelectMember} />
    </div>
  );
}
