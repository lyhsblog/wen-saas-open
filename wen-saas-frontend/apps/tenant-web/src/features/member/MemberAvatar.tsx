/** 成员姓名头像：取末两字 + 按姓名稳定取色。无第三方依赖，列表 / 详情共用。 */

const PALETTE = [
  '#1677ff',
  '#52c41a',
  '#fa8c16',
  '#eb2f96',
  '#722ed1',
  '#13c2c2',
  '#f5222d',
  '#2f54eb',
];

function colorOf(seed: string): string {
  let h = 0;
  for (let i = 0; i < seed.length; i += 1) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  return PALETTE[h % PALETTE.length] ?? PALETTE[0]!;
}

type Props = {
  name: string;
  size?: number;
};

export function MemberAvatar({ name, size = 28 }: Props) {
  const text = (name || '?').trim();
  const initials = text.length <= 2 ? text : text.slice(-2);
  return (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        background: colorOf(text),
        color: '#fff',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: Math.round(size * 0.42),
        fontWeight: 600,
        flexShrink: 0,
        userSelect: 'none',
      }}
    >
      {initials}
    </span>
  );
}
