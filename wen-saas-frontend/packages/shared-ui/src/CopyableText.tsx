import { Typography } from 'antd';
import type { CSSProperties, ReactNode } from 'react';

export type CopyableTextProps = {
  /** 实际复制到剪贴板的明文值；为空（null / ''）时渲染 empty 占位、不显示复制图标。 */
  value: string | null | undefined;
  /** 自定义展示内容（默认展示 value 本身）。用于带装饰的展示，如把 code 显示成 `/code/`。 */
  children?: ReactNode;
  /** 空值占位，默认 —。 */
  empty?: ReactNode;
  /** 透传到文本的样式（如列内字体 / 字号）。 */
  style?: CSSProperties;
};

/**
 * 列表「关键明文标识列」一键复制单元格。
 *
 * 基于 antd `Typography.Text` copyable：hover 所在行 / 单元格时显现复制图标
 * （显隐由 design-system `tokens.css` 的 `.copyable-cell` 规则控制），点击复制
 * `value` 原文。点复制图标只复制、不触发所在行的点击（打开详情），点文本仍按
 * 原行为冒泡。
 *
 * 仅用于明文标识（编码 / 编号 / 工单号等）。脱敏列（手机 / 邮箱掩码）不要用：
 * 复制掩码无意义，明文须走 `/contact` 权限端点。
 */
export function CopyableText({ value, children, empty = '—', style }: CopyableTextProps) {
  if (value == null || value === '') return <>{empty}</>;
  return (
    <span
      onClick={(e) => {
        if ((e.target as HTMLElement).closest('.ant-typography-copy')) e.stopPropagation();
      }}
    >
      <Typography.Text
        className="copyable-cell"
        style={{ fontSize: 'inherit', color: 'inherit', ...style }}
        copyable={{ text: value, tooltips: ['复制', '已复制'] }}
      >
        {children ?? value}
      </Typography.Text>
    </span>
  );
}
