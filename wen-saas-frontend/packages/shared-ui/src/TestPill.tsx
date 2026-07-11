import { CheckOutlined, CloseOutlined, LoadingOutlined } from '@ant-design/icons';

/** 连通性/校验类操作的就地结果条状态；idle 时不渲染。 */
export type TestPillState = 'idle' | 'testing' | 'success' | 'fail';

export type TestPillProps = {
  state: TestPillState;
  /** 结果文案（成功/失败详情），持久展示，替代一闪而过的全局 toast。 */
  msg: string;
};

/**
 * 就地测试结果条：进行中 / 成功 / 失败 三态胶囊，
 * 用于「测试连接」等操作在表单内持久展示结果与详情。
 */
export function TestPill({ state, msg }: TestPillProps) {
  if (state === 'idle') return null;
  const map = {
    testing: { bg: 'var(--bg-muted)', fg: 'var(--text-secondary)', icon: <LoadingOutlined /> },
    success: { bg: 'var(--success-bg)', fg: 'var(--success)', icon: <CheckOutlined /> },
    fail: { bg: 'var(--danger-bg)', fg: 'var(--danger)', icon: <CloseOutlined /> },
  }[state];
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        padding: '6px 12px',
        background: map.bg,
        color: map.fg,
        borderRadius: 999,
        fontSize: 13,
      }}
    >
      {map.icon}
      {msg}
    </span>
  );
}
