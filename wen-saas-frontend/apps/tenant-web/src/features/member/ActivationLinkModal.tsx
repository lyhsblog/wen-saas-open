import { App, Modal, Typography } from 'antd';
import { CopyOutlined } from '@ant-design/icons';

const { Paragraph, Text } = Typography;

export type ActivationInfo = {
  username: string;
  activationToken: string;
  activationExpiresAt: string;
};

type Props = {
  open: boolean;
  info: ActivationInfo | null;
  onClose: () => void;
};

/** 成员激活 / 重置密码链接结果。复用激活链接流（reset_member，48h）。 */
export function ActivationLinkModal({ open, info, onClose }: Props) {
  const { message } = App.useApp();
  const url = info
    ? `${window.location.origin}/reset-password/${encodeURIComponent(info.activationToken)}`
    : '';

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url);
      message.success('已复制激活链接');
    } catch {
      message.error('复制失败，请手动复制');
    }
  };

  return (
    <Modal
      open={open}
      title="激活链接已生成"
      okText="我已知晓"
      cancelButtonProps={{ style: { display: 'none' } }}
      onOk={onClose}
      onCancel={onClose}
      destroyOnHidden
      width={520}
    >
      {info && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <Text>
            账号 <Text strong>{info.username}</Text> 的激活链接（48 小时内有效）：
          </Text>
          <Paragraph
            copyable={{ text: url, icon: <CopyOutlined />, tooltips: ['复制', '已复制'] }}
            style={{
              background: 'var(--bg-muted, #f5f5f5)',
              padding: '8px 12px',
              borderRadius: 6,
              wordBreak: 'break-all',
              margin: 0,
            }}
            onClick={() => void copy()}
          >
            {url}
          </Paragraph>
          <Text type="secondary" style={{ fontSize: 12 }}>
            复制链接发给成员，或在成员已填手机/邮箱时由系统投递；成员打开后校验账号并自行设置密码。
            到期前可在成员详情重新生成。
          </Text>
        </div>
      )}
    </Modal>
  );
}
