import { CountChip } from '@wen-saas/shared-ui';
import {
  EditOutlined,
  DeleteOutlined,
  FolderOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import type { DictGroup } from '@wen-saas/domain';
import { PermissionGate, ReadonlyButton } from '@wen-saas/permissions';
import { SOURCE_META } from './DictGroupList';

type Source = keyof typeof SOURCE_META;

type Props = {
  group: DictGroup;
  onEdit: () => void;
  onDelete: () => void;
};

export function DictGroupHeader({ group, onEdit, onDelete }: Props) {
  const source: Source = group.source ?? 'CUSTOM';
  const meta = SOURCE_META[source];
  return (
    <div
      style={{
        padding: '18px 24px 12px',
        background: 'var(--bg-elev)',
        borderBottom: '1px solid var(--divider)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
        {group.supportHierarchy ? (
          <FolderOutlined style={{ fontSize: 18, color: 'var(--brand)' }} />
        ) : (
          <UnorderedListOutlined style={{ fontSize: 18, color: 'var(--brand)' }} />
        )}
        <span style={{ fontSize: 18, fontWeight: 600 }}>{group.name}</span>
        <code
          style={{
            fontSize: 12,
            color: 'var(--text-tertiary)',
            padding: '2px 8px',
            background: 'var(--bg-muted)',
            borderRadius: 'var(--r-sm)',
          }}
        >
          {group.code}
        </code>
        <CountChip>{meta.label}</CountChip>
        {group.supportHierarchy && <CountChip tone="brand">支持层级 · 最大 5 层</CountChip>}
        <div style={{ flex: 1 }} />
        <PermissionGate permission="dict.group.update">
          <ReadonlyButton
            size="small"
            icon={<EditOutlined />}
            disabled={source === 'SYSTEM'}
            onClick={onEdit}
          >
            编辑分组
          </ReadonlyButton>
        </PermissionGate>
        <PermissionGate permission="dict.group.delete">
          <ReadonlyButton
            size="small"
            icon={<DeleteOutlined />}
            danger
            disabled={source !== 'CUSTOM'}
            onClick={onDelete}
          >
            删除分组
          </ReadonlyButton>
        </PermissionGate>
      </div>
      {group.description && (
        <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{group.description}</div>
      )}
    </div>
  );
}
