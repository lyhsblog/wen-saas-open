import { http, HttpResponse } from 'msw';

/**
 * 真后端契约未 freeze（架构 §二十 候选 B：`GET /api/v1/me/capabilities`）。
 * mock 暂返回 tenantOwner=true 让 PermissionGate 全放行，便于 dev 期跑通流程；
 * 后端落地后这里改成对真实 capabilities 的反序列化。
 */
export const capabilitiesHandlers = [
  http.get('/api/v1/me/capabilities', () =>
    HttpResponse.json({
      permissions: [],
      enabledModules: ['base', 'dict'],
      activePackages: ['BASIC'],
      packageReadonly: false,
      tenantOwner: true,
    }),
  ),
];
