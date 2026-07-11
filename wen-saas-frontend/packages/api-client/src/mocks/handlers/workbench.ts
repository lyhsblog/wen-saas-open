import { http, HttpResponse } from 'msw';
import type { TenantWorkbenchBusinessCard, TenantWorkbenchOverview } from '../../tenantWorkbench';

/**
 * 租户工作台首页。mock 返回空态三卡 + 空业务卡片，
 * 让登录落地页在 mock 模式下完整渲染（否则这两个端点未处理会旁路到真后端、拿 401 触发登出）。
 */
export const workbenchHandlers = [
  http.get('/api/v1/base/workbench/overview', () => {
    const overview: TenantWorkbenchOverview = {
      greeting: { userName: 'Alice', roleName: '成员' },
      planExpiry: null,
      todo: { total: 0, items: [] },
      message: { unreadTotal: 0, recent: [] },
      announcement: { total: 0, latest: null },
    };
    return HttpResponse.json(overview);
  }),
  http.get('/api/v1/base/workbench/cards', () =>
    HttpResponse.json([] as TenantWorkbenchBusinessCard[]),
  ),
];
