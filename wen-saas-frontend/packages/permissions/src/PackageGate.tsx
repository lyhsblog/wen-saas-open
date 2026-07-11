import { type ReactNode } from 'react';
import { Alert } from 'antd';
import { useHasModule, useIsReadonly } from './capabilities';

export type PackageGateProps = {
  /** 业务模块编码，与后端 `BusinessModuleDescriptor.code` 对齐。 */
  module: string;
  /**
   * `true` 表示套餐到期 / 只读时仍渲染子树（但调用方应配合 `useIsReadonly()` 灰掉写入按钮）；
   * 默认 `false`：到期后整体隐藏并由父级展示空态。
   */
  readonlyFallback?: boolean;
  /** 未启用模块时的兜底，默认渲染一条信息条。 */
  fallback?: ReactNode;
  children: ReactNode;
};

/**
 * 业务模块开关：
 * - 模块未启用 → 渲染 fallback（默认 Alert 提示）
 * - 套餐到期且 readonlyFallback=false → 隐藏并提示
 * - 套餐到期且 readonlyFallback=true → 继续渲染（业务方自行结合 useIsReadonly 灰按钮）
 */
export function PackageGate({
  module,
  readonlyFallback = false,
  fallback,
  children,
}: PackageGateProps) {
  const enabled = useHasModule(module);
  const readonly = useIsReadonly();
  if (!enabled) {
    return (
      <>
        {fallback ?? (
          <Alert
            type="warning"
            showIcon
            message={`模块 ${module} 未启用`}
            description="该模块需在套餐中开通后访问，请联系管理员。"
          />
        )}
      </>
    );
  }
  if (readonly && !readonlyFallback) {
    return (
      <Alert
        type="info"
        showIcon
        message="当前套餐已到期，功能进入只读模式"
        description="请续费后继续使用写入功能；如需查看历史数据，可联系管理员调整套餐。"
      />
    );
  }
  return <>{children}</>;
}
