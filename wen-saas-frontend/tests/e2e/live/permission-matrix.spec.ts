import { expect, test, type APIRequestContext } from '@playwright/test';
import { apiLogin, bearer, BACKEND_URL, LIVE_ACCOUNTS } from '../helpers';

/**
 * 权限矩阵 / 数据范围 / 跨端隔离 —— 接口级 live 断言（不走浏览器，直打 8080 真后端）。
 *
 * 固化第二轮精细测试抓到并已修复的命门缺陷，作回归基线：
 *   [A1-PKG]        套餐端点鉴权（PackageOpsController 原零 @RequirePermission）
 *   [A1-PKG-写]     写端点鉴权（越权写在 403 前被拦、不落库）
 *   [A2-1]/[A2-2]   CSM data_scope=OWNED 列表/详情数据范围过滤
 *   [A3-基线/单点]  租户 RBAC 门控（普通成员无权 / 字典只读角色单点）
 *   [A4-1]          运营↔租户跨端隔离
 *
 * 全部只读 GET + 越权写（在 403 前被拦、不落库），不污染数据、确定性高。
 * 账号为本地种子夹具（seed-live-fixtures.mjs 创建，仅本地联调环境有效；ops-csm data_scope=OWNED、名下仅绑 wmtest）。
 *
 * 前置：8080 jar + docker PG/Redis/RocketMQ 在跑（与 ticket-fullchain 同，见
 * playwright.live.config.ts）。脚本：`pnpm test:e2e:live`。
 */

const OPS_PACKAGES = `${BACKEND_URL}/api/v1/ops/packages`;
const OPS_TENANTS = `${BACKEND_URL}/api/v1/ops/tenants`;
const T_DICT_GROUPS = `${BACKEND_URL}/api/v1/base/dict/groups`;
const T_MEMBERS = `${BACKEND_URL}/api/v1/base/members`;
const T_ROLES = `${BACKEND_URL}/api/v1/base/roles`;

/** GET 某端点并返回 HTTP 状态码（带 Bearer）。 */
async function getStatus(request: APIRequestContext, url: string, token: string): Promise<number> {
  return (await request.get(url, { headers: bearer(token) })).status();
}

/** 从某 token 可见的租户列表里按 code 取 id（雪花 id 按字符串处理）。 */
async function tenantIdByCode(
  request: APIRequestContext,
  token: string,
  code: string,
): Promise<string | undefined> {
  const res = await request.get(`${OPS_TENANTS}?page=1&size=200`, { headers: bearer(token) });
  expect(res.ok(), `列租户应 2xx，实际 ${res.status()}`).toBeTruthy();
  const body = (await res.json()) as { items?: Array<{ id: string; code: string }> };
  return (body.items ?? []).find((t) => t.code === code)?.id;
}

test.describe('权限矩阵 API（真后端 live）', () => {
  test('[A1-PKG] 套餐端点鉴权：超管 200 / CSM 403', async ({ request }) => {
    const superT = await apiLogin(request, LIVE_ACCOUNTS.opsSuper);
    const csmT = await apiLogin(request, LIVE_ACCOUNTS.opsCsm);
    expect(await getStatus(request, OPS_PACKAGES, superT)).toBe(200);
    expect(await getStatus(request, OPS_PACKAGES, csmT)).toBe(403);
  });

  test('[A2-1] 租户列表数据范围：CSM 仅见名下 wmtest / 超管见全部', async ({ request }) => {
    const superT = await apiLogin(request, LIVE_ACCOUNTS.opsSuper);
    const csmT = await apiLogin(request, LIVE_ACCOUNTS.opsCsm);

    const csmRes = await request.get(`${OPS_TENANTS}?page=1&size=200`, { headers: bearer(csmT) });
    expect(csmRes.ok()).toBeTruthy();
    const csmCodes = ((await csmRes.json()).items ?? []).map((t: { code: string }) => t.code);
    expect(csmCodes, 'CSM 应见名下 wmtest').toContain('wmtest');
    expect(csmCodes, 'CSM 不应见非名下 uitest').not.toContain('uitest');

    const superRes = await request.get(`${OPS_TENANTS}?page=1&size=200`, {
      headers: bearer(superT),
    });
    const superCodes = ((await superRes.json()).items ?? []).map((t: { code: string }) => t.code);
    expect(superCodes, '超管 ALL 应见 uitest').toContain('uitest');
    expect(superCodes).toContain('wmtest');
  });

  test('[A2-2] 租户详情数据范围：CSM 名下 200 / 非名下 404（不泄露存在性）', async ({
    request,
  }) => {
    const superT = await apiLogin(request, LIVE_ACCOUNTS.opsSuper);
    const csmT = await apiLogin(request, LIVE_ACCOUNTS.opsCsm);
    const wmtestId = await tenantIdByCode(request, csmT, 'wmtest');
    const uitestId = await tenantIdByCode(request, superT, 'uitest');
    expect(wmtestId, 'CSM 应见名下 wmtest').toBeTruthy();
    expect(uitestId, '超管应见 uitest').toBeTruthy();
    expect(await getStatus(request, `${OPS_TENANTS}/${wmtestId}`, csmT)).toBe(200);
    expect(await getStatus(request, `${OPS_TENANTS}/${uitestId}`, csmT)).toBe(404);
  });

  test('[A1-PKG-写] 写端点鉴权：CSM 建套餐 403（不落库）', async ({ request }) => {
    const csmT = await apiLogin(request, LIVE_ACCOUNTS.opsCsm);
    // 命中 @RequirePermission("ops.package.create")；CSM 白名单无 ops.package.* → 403 发生在落库前。
    const res = await request.post(OPS_PACKAGES, {
      headers: bearer(csmT),
      // publishImmediately 是 primitive boolean，缺省会先吃 400，补上让请求走到鉴权层。
      data: { code: 'PERM_IT_NOPE', name: '权限回归用例（应被 403 拦）', publishImmediately: false },
    });
    expect(res.status(), 'CSM 建套餐应 403').toBe(403);
  });

  test('[A3-基线] 普通成员（无权）：字典/成员/角色端点全 403', async ({ request }) => {
    const t = await apiLogin(request, LIVE_ACCOUNTS.tenantMember);
    expect(await getStatus(request, T_DICT_GROUPS, t)).toBe(403);
    expect(await getStatus(request, T_MEMBERS, t)).toBe(403);
    expect(await getStatus(request, T_ROLES, t)).toBe(403);
  });

  test('[A3-单点] 字典只读角色：仅字典 200，成员/角色 403', async ({ request }) => {
    const t = await apiLogin(request, LIVE_ACCOUNTS.tenantDictOnly);
    expect(await getStatus(request, T_DICT_GROUPS, t)).toBe(200);
    expect(await getStatus(request, T_MEMBERS, t)).toBe(403);
    expect(await getStatus(request, T_ROLES, t)).toBe(403);
  });

  test('[A4-1] 跨端隔离：运营 token 打租户 403 / 租户 token 打运营 403', async ({ request }) => {
    const opsT = await apiLogin(request, LIVE_ACCOUNTS.opsSuper);
    const tenT = await apiLogin(request, LIVE_ACCOUNTS.tenantOwner);
    expect(await getStatus(request, T_MEMBERS, opsT), '运营 token 打租户端应 403').toBe(403);
    expect(await getStatus(request, OPS_TENANTS, tenT), '租户 token 打运营端应 403').toBe(403);
  });
});
