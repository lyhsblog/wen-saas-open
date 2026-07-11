import type { ReactNode } from 'react';
import { App, Modal, Typography } from 'antd';
import { CopyOutlined } from '@ant-design/icons';

const { Text } = Typography;

export type SecretRevealField = {
  /** React key，缺省用数组下标 */
  key?: string;
  /** 字段说明行（小号次要文字），如「API Key（请求头 X-Wen-API-Key，可随时在列表查看）」 */
  label: ReactNode;
  value: string;
  /** 默认 true；false 时仅展示不提供复制 */
  copyable?: boolean;
  /** 复制成功提示文案，默认「已复制」；建议带字段名如「已复制签名密钥」 */
  copiedTip?: string;
};

export type SecretRevealModalProps = {
  open: boolean;
  title: ReactNode;
  /** 逐字段展示：label + 值块（点击整块即复制，单条成功提示） */
  fields: SecretRevealField[];
  /** 一次性展示警示文案（如「密钥加密落库、之后无法再查看明文…」） */
  warnText?: ReactNode;
  onClose: () => void;
  width?: number;
};

/**
 * 敏感凭证一次性展示弹窗（API Key / Webhook 签名密钥等创建、轮换后弹出）。
 * 只有「我已保存」单按钮出口；值块整体可点击复制，复制成功只出一条提示
 * （消息走 App.useApp()，不用静态 API，随主题上下文）。
 */
export function SecretRevealModal({
  open,
  title,
  fields,
  warnText,
  onClose,
  width = 560,
}: SecretRevealModalProps) {
  const { message } = App.useApp();

  const copy = async (value: string, tip: string) => {
    try {
      await navigator.clipboard.writeText(value);
      message.success(tip);
    } catch {
      message.error('复制失败，请手动复制');
    }
  };

  return (
    <Modal
      open={open}
      title={title}
      okText="我已保存"
      cancelButtonProps={{ style: { display: 'none' } }}
      onOk={onClose}
      onCancel={onClose}
      destroyOnHidden
      width={width}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {fields.map((f, idx) => {
          const copyable = f.copyable !== false;
          return (
            <div key={f.key ?? idx} style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {f.label != null && (
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {f.label}
                </Text>
              )}
              {copyable ? (
                <button
                  type="button"
                  onClick={() => void copy(f.value, f.copiedTip ?? '已复制')}
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 8,
                    width: '100%',
                    textAlign: 'left',
                    border: 'none',
                    cursor: 'pointer',
                    fontFamily: 'var(--font-mono, monospace)',
                    fontSize: 13,
                    background: 'var(--bg-muted, #f5f5f5)',
                    color: 'var(--text)',
                    padding: '8px 12px',
                    borderRadius: 6,
                    wordBreak: 'break-all',
                  }}
                >
                  <span style={{ flex: 1 }}>{f.value}</span>
                  <CopyOutlined
                    aria-hidden
                    style={{ color: 'var(--text-tertiary)', marginTop: 3 }}
                  />
                </button>
              ) : (
                <div
                  style={{
                    fontFamily: 'var(--font-mono, monospace)',
                    fontSize: 13,
                    background: 'var(--bg-muted, #f5f5f5)',
                    padding: '8px 12px',
                    borderRadius: 6,
                    wordBreak: 'break-all',
                  }}
                >
                  {f.value}
                </div>
              )}
            </div>
          );
        })}
        {warnText && (
          <Text type="secondary" style={{ fontSize: 12 }}>
            {warnText}
          </Text>
        )}
      </div>
    </Modal>
  );
}
