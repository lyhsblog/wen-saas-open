import { Alert, Typography } from 'antd';
import { DangerConfirm } from '@wen-saas/shared-ui';
import { tenantWebUrl } from '../../../lib/tenantWebUrl';
import { useImpersonate } from '../hooks/useTenantMutations';

type Props = {
  open: boolean;
  tenantId: string;
  tenantCode: string;
  tenantName: string;
  mainAdminUserId: string;
  mainAdminUsername: string;
  onClose: () => void;
};

export function ImpersonateModal({
  open,
  tenantId,
  tenantCode,
  tenantName,
  mainAdminUserId,
  mainAdminUsername,
  onClose,
}: Props) {
  const { mutateAsync, isPending } = useImpersonate(tenantId);

  const handleConfirm = async () => {
    const result = await mutateAsync({ asUserId: mainAdminUserId });
    const url = tenantWebUrl(
      result.targetTenantCode,
      `/impersonate-redeem?nonce=${result.redeemNonce}`,
    );
    window.open(url, '_blank');
    onClose();
  };

  return (
    <DangerConfirm
      open={open}
      title={`代登录：${tenantName}`}
      lead={`您将以主管理员「${mainAdminUsername}」的身份代登录该租户`}
      consequences={[
        '本次代登录将记录在双方审计日志中',
        '登录后租户端顶部将显示红色"运营代登录中"横幅',
        'nonce 有效期 5 分钟，超期需重新发起',
      ]}
      confirmPhrase={tenantCode}
      okText="确认代登录"
      confirming={isPending}
      onCancel={onClose}
      onConfirm={() => void handleConfirm()}
      extra={
        <Alert
          type="info"
          showIcon
          message={
            <Typography.Text style={{ fontSize: 12 }}>
              代登录仅用于协助排查问题，请遵守数据合规规定，不得查阅租户私密业务数据。
            </Typography.Text>
          }
        />
      }
    />
  );
}
