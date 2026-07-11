import { useState, type CSSProperties, type ReactNode } from 'react';
import { Button, Tag } from 'antd';
import { DownOutlined, UpOutlined } from '@ant-design/icons';

/** 筛选字段：标签在上、控件在下；控件请给 width:100%，由栅格决定实际宽度。 */
export type FilterBarField = { key: string; label: string; control: ReactNode };

/** 收起时用于回显被折叠区已生效条件的 chip。 */
export type FilterBarChip = { key: string; text: string; onRemove?: () => void };

export type FilterBarProps = {
  /** 常显字段（建议 3~4 个核心维度）。 */
  fields: FilterBarField[];
  /** 折叠在「更多筛选」后的次要字段（如自定义字段），为空则不渲染折叠开关。 */
  moreFields?: FilterBarField[];
  /** 收起状态下回显 moreFields 中已生效的条件；展开后字段本身可见，不再显示。 */
  chips?: FilterBarChip[];
  /** 搜索 / 重置按钮，独立成一格随栅格排布。 */
  actions?: ReactNode;
  style?: CSSProperties;
};

/**
 * 列表页通用筛选条：等宽响应式栅格（auto-fill 200px 起），换行整齐、底边对齐，
 * 替代各页手写 flex-wrap 的参差布局。字段多时默认收起次要字段，避免挤压表格。
 * 纯布局组件：草稿受控与「搜索」提交语义仍由页面负责。
 */
export function FilterBar({ fields, moreFields, chips, actions, style }: FilterBarProps) {
  const [expanded, setExpanded] = useState(false);
  const more = moreFields ?? [];
  const visible = expanded ? [...fields, ...more] : fields;
  const collapsedChips = expanded ? [] : (chips ?? []);

  return (
    <div style={{ padding: '12px 24px', background: 'var(--bg-elev)', ...style }}>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
          gap: 14,
          alignItems: 'end',
        }}
      >
        {visible.map((f) => (
          <div key={f.key} style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0 }}>
            <span
              style={{
                fontSize: 13,
                color: 'var(--text-secondary)',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {f.label}
            </span>
            {f.control}
          </div>
        ))}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {actions}
          {more.length > 0 && (
            <Button
              type="link"
              size="small"
              style={{ padding: 0 }}
              icon={expanded ? <UpOutlined /> : <DownOutlined />}
              onClick={() => setExpanded((v) => !v)}
            >
              {expanded ? '收起' : '更多筛选'}
            </Button>
          )}
        </div>
      </div>
      {collapsedChips.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 10 }}>
          {collapsedChips.map((c) => (
            <Tag
              key={c.key}
              closable={!!c.onRemove}
              onClose={(e) => {
                e.preventDefault();
                c.onRemove?.();
              }}
              style={{ margin: 0 }}
            >
              {c.text}
            </Tag>
          ))}
        </div>
      )}
    </div>
  );
}
