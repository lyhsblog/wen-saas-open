import { Alert } from 'antd';
import { useIsReadonly } from './capabilities';

export type ReadonlyBannerProps = {
  message?: string;
  description?: string;
};

/**
 * 全局只读模式提示条。建议放在 AppShell 顶部；非只读态返回 null。
 */
export function ReadonlyBanner({
  message = '当前套餐已到期，业务功能进入只读模式',
  description = '续费后将自动恢复写入；如有疑问请联系账户管理员。',
}: ReadonlyBannerProps) {
  const readonly = useIsReadonly();
  if (!readonly) return null;
  return (
    <Alert
      banner
      type="warning"
      showIcon
      message={message}
      description={description}
      style={{ borderRadius: 0 }}
    />
  );
}
