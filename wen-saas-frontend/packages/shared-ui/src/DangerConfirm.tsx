import { type ReactNode, useState } from 'react';
import { Button, Input, Modal } from 'antd';
import { ExclamationCircleFilled } from '@ant-design/icons';

/**
 * 敏感操作二次确认弹窗，高危操作统一的确认模板：
 *
 * - 顶部 warning box：警告图标 + 加粗主提示 + 影响列表
 * - 可选 type-to-confirm 输入框：必须 trim() 等于 `confirmPhrase` 才放行
 * - 主按钮 danger 风格，confirming 时 loading + 禁用
 *
 * 使用场景：代登录、删除租户、切换存储、清空数据、取消订阅等
 * 后果不可逆 / 影响所有人 的操作。
 */
export type DangerConfirmProps = {
  open: boolean;
  /** 标题，例如 "切换存储后端" / "删除租户 「XX」" */
  title: string;
  /** warning box 顶部加粗主行，例如 "您即将把数据导入到生产环境" */
  lead: ReactNode;
  /** 影响要点列表（渲染为 ul / li） */
  consequences?: ReactNode[];
  /** warning box 之后的额外节点（说明文本、链接、checkbox 等） */
  extra?: ReactNode;
  /**
   * type-to-confirm 短语；非空时启用输入框，必须 trim() 严格相等才放行。
   * 留空则跳过输入验证，仅 danger 按钮即可确认。
   */
  confirmPhrase?: string;
  /** type-to-confirm 输入框 label，默认 `输入「{phrase}」以确认` */
  confirmLabel?: ReactNode;
  okText?: string;
  cancelText?: string;
  /** 确认中：按钮 loading + 整体禁用关闭。 */
  confirming?: boolean;
  onCancel: () => void;
  onConfirm: () => void | Promise<void>;
  width?: number;
};

const WARN_BG = 'var(--warning-bg)';
const WARN_FG = 'var(--warning)';
const WARN_BORDER = '1px solid rgba(217, 119, 6, 0.25)';

/**
 * 把可变 UI（输入框 + 校验态）放在独立子组件里，靠 Modal 的 `destroyOnHidden`
 * 自动卸载——比在父组件用 useEffect 清空 typed 更干净，且不踩
 * `react-hooks/set-state-in-effect` 的禁忌。
 */
type BodyProps = {
  lead: ReactNode;
  consequences: ReactNode[] | undefined;
  extra: ReactNode | undefined;
  confirmPhrase: string | undefined;
  confirmLabel: ReactNode | undefined;
  confirming: boolean;
  onMatchChange: (matched: boolean) => void;
};

function Body({
  lead,
  consequences,
  extra,
  confirmPhrase,
  confirmLabel,
  confirming,
  onMatchChange,
}: BodyProps) {
  const [typed, setTyped] = useState('');
  const requiresPhrase = !!confirmPhrase;
  const matches = !requiresPhrase || typed.trim() === confirmPhrase;
  const mismatchHint = requiresPhrase && typed.length > 0 && !matches;

  const handleChange = (next: string) => {
    setTyped(next);
    onMatchChange(!requiresPhrase || next.trim() === confirmPhrase);
  };

  const defaultLabel = requiresPhrase ? (
    <>
      输入
      <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600, margin: '0 4px' }}>
        「{confirmPhrase}」
      </span>
      以确认
    </>
  ) : null;

  return (
    <>
      <div
        style={{
          padding: 14,
          background: WARN_BG,
          border: WARN_BORDER,
          borderRadius: 'var(--r-md)',
          color: WARN_FG,
          lineHeight: 1.75,
          fontSize: 13,
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            fontWeight: 600,
            marginBottom: 6,
          }}
        >
          <ExclamationCircleFilled style={{ fontSize: 14 }} />
          <span>{lead}</span>
        </div>
        {consequences && consequences.length > 0 && (
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            {consequences.map((c, i) => (
              <li key={i}>{c}</li>
            ))}
          </ul>
        )}
      </div>

      {extra && <div style={{ marginTop: 14 }}>{extra}</div>}

      {requiresPhrase && (
        <div style={{ marginTop: 16 }}>
          <label
            style={{
              display: 'block',
              fontSize: 13,
              color: 'var(--text-secondary)',
              marginBottom: 6,
            }}
          >
            {confirmLabel ?? defaultLabel}
            <span style={{ color: 'var(--danger)', marginLeft: 4 }}>*</span>
          </label>
          <Input
            value={typed}
            placeholder={confirmPhrase}
            onChange={(e) => handleChange(e.target.value)}
            disabled={confirming}
            status={mismatchHint ? 'error' : ''}
          />
          {mismatchHint && (
            <div style={{ fontSize: 12, color: 'var(--danger)', marginTop: 4 }}>
              输入不匹配，请检查
            </div>
          )}
        </div>
      )}
    </>
  );
}

export function DangerConfirm({
  open,
  title,
  lead,
  consequences,
  extra,
  confirmPhrase,
  confirmLabel,
  okText = '确认',
  cancelText = '取消',
  confirming = false,
  onCancel,
  onConfirm,
  width = 520,
}: DangerConfirmProps) {
  // 父级仅追踪 "当前 phrase 是否匹配"，输入字符串本身由 Body 内部 state 持有
  const [matches, setMatches] = useState(!confirmPhrase);
  const okDisabled = !matches || confirming;

  const handleConfirm = () => {
    void onConfirm();
  };

  return (
    <Modal
      open={open}
      title={title}
      onCancel={() => {
        if (!confirming) onCancel();
      }}
      width={width}
      mask={{ closable: false }}
      destroyOnHidden
      afterOpenChange={(opened) => {
        // Modal 关闭 → Body 卸载，下次打开时 setMatches 回到初始（无 phrase 即 true）
        if (!opened) setMatches(!confirmPhrase);
      }}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onCancel} disabled={confirming}>
            {cancelText}
          </Button>
          <Button
            danger
            type="primary"
            loading={confirming}
            disabled={okDisabled}
            onClick={handleConfirm}
          >
            {okText}
          </Button>
        </div>
      }
    >
      <Body
        lead={lead}
        consequences={consequences}
        extra={extra}
        confirmPhrase={confirmPhrase}
        confirmLabel={confirmLabel}
        confirming={confirming}
        onMatchChange={setMatches}
      />
    </Modal>
  );
}
