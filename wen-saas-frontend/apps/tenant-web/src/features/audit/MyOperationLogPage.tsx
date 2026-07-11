import { OperationLogView } from './OperationLogView';

/** 个人 · 我的操作记录（任意成员看自己；入口在右上角用户卡，不挂侧栏菜单）。 */
export function MyOperationLogPage() {
  return <OperationLogView scope="mine" title="我的操作记录" hint="我在本租户的操作记录" />;
}
