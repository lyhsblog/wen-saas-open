import { type ReactNode } from 'react';
import { ErrorState } from './ErrorState';

type Action = { label: string; onClick: () => void };

export type Forbidden403PageProps = {
  description?: ReactNode;
  /** 默认"返回首页"操作；不传则按钮不显示。 */
  onBackHome?: () => void;
  /** 联系管理员等附加说明节点。 */
  extra?: ReactNode;
};

export function Forbidden403Page({ description, onBackHome, extra }: Forbidden403PageProps) {
  const primary: Action | undefined = onBackHome
    ? { label: '返回首页', onClick: onBackHome }
    : undefined;
  return (
    <ErrorState
      status="403"
      title="无权访问"
      description={description ?? '你没有访问该页面所需的权限点。请联系管理员开通。'}
      {...(primary ? { primaryAction: primary } : {})}
      {...(extra !== undefined ? { extra } : {})}
    />
  );
}

export type NotFound404PageProps = {
  description?: ReactNode;
  onBackHome?: () => void;
};

export function NotFound404Page({ description, onBackHome }: NotFound404PageProps) {
  const primary: Action | undefined = onBackHome
    ? { label: '返回首页', onClick: onBackHome }
    : undefined;
  return (
    <ErrorState
      status="404"
      title="页面不存在"
      description={description ?? '链接可能已失效，或资源已被删除。'}
      {...(primary ? { primaryAction: primary } : {})}
    />
  );
}

export type Conflict409PageProps = {
  description?: ReactNode;
  /** 重新加载当前页 / 取最新数据；通常等价于 location.reload()。 */
  onRefresh?: () => void;
  onBackHome?: () => void;
};

export function Conflict409Page({ description, onRefresh, onBackHome }: Conflict409PageProps) {
  const primary: Action | undefined = onRefresh
    ? { label: '刷新页面', onClick: onRefresh }
    : undefined;
  const secondary: Action | undefined = onBackHome
    ? { label: '返回首页', onClick: onBackHome }
    : undefined;
  return (
    <ErrorState
      status="warning"
      title="数据已被其他人更新"
      description={description ?? '当前操作与服务器最新状态冲突，请刷新后重试。'}
      {...(primary ? { primaryAction: primary } : {})}
      {...(secondary ? { secondaryAction: secondary } : {})}
    />
  );
}

export type ServerError5xxPageProps = {
  description?: ReactNode;
  requestId?: string;
  onRetry?: () => void;
  onBackHome?: () => void;
};

export function ServerError5xxPage({
  description,
  requestId,
  onRetry,
  onBackHome,
}: ServerError5xxPageProps) {
  const primary: Action | undefined = onRetry ? { label: '重试', onClick: onRetry } : undefined;
  const secondary: Action | undefined = onBackHome
    ? { label: '返回首页', onClick: onBackHome }
    : undefined;
  return (
    <ErrorState
      status="500"
      title="服务暂不可用"
      description={
        description ?? '系统遇到了点问题。可以稍后重试，问题持续请把请求编号反馈给管理员。'
      }
      {...(primary ? { primaryAction: primary } : {})}
      {...(secondary ? { secondaryAction: secondary } : {})}
      {...(requestId !== undefined ? { requestId } : {})}
    />
  );
}
