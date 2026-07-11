import { type ReactNode } from 'react';
import { Button, Result } from 'antd';
import type { ResultStatusType } from 'antd/lib/result';

export type ErrorStateProps = {
  /** AntD Result status，决定颜色/插图。 */
  status?: ResultStatusType;
  title: ReactNode;
  description?: ReactNode;
  /** 主操作；不传则不渲染。 */
  primaryAction?: { label: string; onClick: () => void };
  /** 次操作（如"返回首页"）。 */
  secondaryAction?: { label: string; onClick: () => void };
  /** 错误追踪信息（如 X-Request-ID），等宽展示在底部。 */
  requestId?: string;
  /** 额外节点（如 PermissionGate 联系管理员的链接）。 */
  extra?: ReactNode;
};

/**
 * 全屏错误页通用壳，承接 403 / 404 / 409 / 5xx 等场景。
 * 业务方一般用 Forbidden403Page / NotFound404Page 等"已绑文案"的薄壳，
 * 自定义错误（如"操作不被允许"）直接 ErrorState 自行装配。
 */
export function ErrorState({
  status = 'error',
  title,
  description,
  primaryAction,
  secondaryAction,
  requestId,
  extra,
}: ErrorStateProps) {
  const actions: ReactNode[] = [];
  if (primaryAction) {
    actions.push(
      <Button key="primary" type="primary" onClick={primaryAction.onClick}>
        {primaryAction.label}
      </Button>,
    );
  }
  if (secondaryAction) {
    actions.push(
      <Button key="secondary" onClick={secondaryAction.onClick}>
        {secondaryAction.label}
      </Button>,
    );
  }

  return (
    <div
      style={{
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
      }}
    >
      <Result
        status={status}
        title={title}
        subTitle={description}
        extra={
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 12,
            }}
          >
            {actions.length > 0 && (
              <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }}>{actions}</div>
            )}
            {extra}
            {requestId && (
              <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
                请求编号：
                <code style={{ fontFamily: 'var(--font-mono, monospace)' }}>{requestId}</code>
              </div>
            )}
          </div>
        }
      />
    </div>
  );
}
