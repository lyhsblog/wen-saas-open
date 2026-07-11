import { expect, type APIRequestContext, type Page } from '@playwright/test';

/** 租户在子域名，已固定在各 config 的 baseURL（uitest.localhost）；mock auth handler 不校验 tenantCode。 */
export const TENANT = 'uitest';

/** MSW mock 种子凭据，见 packages/api-client/src/mocks/data.ts。 */
export const MOCK_CREDS = { username: 'alice', password: 'P@ssw0rd' };

/** 走真实登录表单提交（不旁路 UI），成功后落在工作台。 */
export async function login(
  page: Page,
  creds: { username: string; password: string } = MOCK_CREDS,
): Promise<void> {
  await page.goto('/login');
  await page.getByPlaceholder('账号 / 手机号').fill(creds.username);
  await page.getByPlaceholder('密码').fill(creds.password);
  // AntD 6 对 CJK 按钮文案会插空格，用 \s* 兜底。
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await expect(page).toHaveURL(/\/workbench/);
}

/* ─────────────────────────────────────────────────────────────────────────
 * 真后端（live）登录辅助。仅供 tests/e2e/live/** 使用，凭据为本地种子夹具
 * （scripts/seed-live-fixtures.mjs 幂等创建，仅本地联调环境有效）。默认 mock 套件不引用这些导出，互不影响。
 * ──────────────────────────────────────────────────────────────────────── */

/** 运营端 dev server（真后端模式）地址。live spec 内开 context 时显式用它。 */
export const OPS_BASE_URL = 'http://localhost:5173';

/** 专用 UI 测试租户主管理员（绕过权限门禁，含 base.ticket.view_all）。 */
export const LIVE_TENANT_CREDS = { username: 'uiadmin', password: 'Test@2026!' };

/** 平台运营账号（含 ops.ticket.* 处理权限）。本机 mfaRequired=false，可直登。 */
export const LIVE_OPS_CREDS = { username: 'admin@ops', password: 'admin123' };

/**
 * 租户端真后端登录（5174 真后端 dev server）。复用 mock login 的表单提交逻辑，
 * 但用真实租户凭据 `uiadmin/Test@2026!`（tenantCode=uitest）。
 */
export async function liveTenantLogin(page: Page): Promise<void> {
  await login(page, LIVE_TENANT_CREDS);
}

/**
 * 运营端真后端登录（ops-web，baseURL 须指向 5173）。ops 登录页是独立深色页，
 * 用原生 input（placeholder「企业邮箱」/「密码」）+ submit 按钮。本机 MFA 关闭
 * （`mfaRequired:false`）→ 登录后端直返 token，前端跳过二步验证落工作台。
 *
 * 用法：在 spec 里用 `browser.newContext({ baseURL: OPS_BASE_URL })` 开一个指向
 * 5173 的 context，再调本函数。
 */
export async function liveOpsLogin(page: Page): Promise<void> {
  await liveOpsLoginAs(page, LIVE_OPS_CREDS.username, LIVE_OPS_CREDS.password);
}

/**
 * 参数化运营端登录（ops-web，baseURL 须指向 5173），供分角色（CSM）菜单收敛断言。
 * 本机所有运营夹具账号 MFA 关闭，登录后端直返 token、前端跳过二步验证落工作台。
 */
export async function liveOpsLoginAs(
  page: Page,
  username: string,
  password: string,
): Promise<void> {
  await page.goto('/login');
  await page.getByPlaceholder('企业邮箱').fill(username);
  await page.getByPlaceholder('密码').fill(password);
  await page.getByRole('button', { name: /下一步：二步验证|登录中/ }).click();
  await expect(page).toHaveURL(/\/workbench/, { timeout: 15_000 });
}

/* ─────────────────────────────────────────────────────────────────────────
 * 接口级 live 断言辅助：不走浏览器，直打 8080 真后端、拿 Bearer token 断状态码。
 * 接口级 403 用例比浏览器路径更快更稳。
 * 账号为本地种子夹具（seed-live-fixtures.mjs 创建，仅本地联调环境有效）：统一密码 Test@2026!，运营超管 admin@ops/admin123。
 * 仅供 tests/e2e/live/** 使用，默认 mock 套件不引用。
 * ──────────────────────────────────────────────────────────────────────── */

/** 本机真后端直连地址（绕过 dev proxy，接口断言直打 8080）。 */
export const BACKEND_URL = 'http://localhost:8080';

/** 精细测试夹具账号（租户账号须带 tenantCode；运营账号不传）。 */
export const LIVE_ACCOUNTS = {
  opsSuper: { username: 'admin@ops', password: 'admin123' },
  opsCsm: { username: 'ops-csm', password: 'Test@2026!' },
  tenantOwner: { username: 'uiadmin', password: 'Test@2026!', tenantCode: 'uitest' },
  tenantMember: { username: 'member1', password: 'Test@2026!', tenantCode: 'uitest' },
  tenantDictOnly: { username: 'dictonly', password: 'Test@2026!', tenantCode: 'uitest' },
} as const;

/** 直打 8080 登录拿 access token；租户账号传 tenantCode，运营账号不传。 */
export async function apiLogin(
  request: APIRequestContext,
  creds: { username: string; password: string; tenantCode?: string },
): Promise<string> {
  const res = await request.post(`${BACKEND_URL}/api/auth/login`, { data: creds });
  expect(res.ok(), `登录 ${creds.username} 应成功，实际 ${res.status()}`).toBeTruthy();
  const body = (await res.json()) as { token?: string };
  expect(body.token, `登录 ${creds.username} 应返回 token`).toBeTruthy();
  return body.token as string;
}

/** Bearer 鉴权头。 */
export function bearer(token: string): { Authorization: string } {
  return { Authorization: `Bearer ${token}` };
}

/** 一次性租户开通结果。 */
export type OneTimeTenant = {
  tenantId: string;
  tenantCode: string;
  adminUsername: string;
  resetToken: string;
};

/**
 * ops API 开通一次性租户（含主管理员 + 可选业务套餐）。
 * 无删租户端点，故 code 带时间戳唯一、跑后留存（与 ticket-fullchain 每跑建工单同口径）。
 */
export async function createOneTimeTenant(
  request: APIRequestContext,
  opsToken: string,
  opts: { code: string; name: string; adminUsername: string; packageCodes?: string[] },
): Promise<OneTimeTenant> {
  const res = await request.post(`${BACKEND_URL}/api/v1/ops/tenants`, {
    headers: bearer(opsToken),
    data: {
      tenantName: opts.name,
      tenantCode: opts.code,
      adminUsername: opts.adminUsername,
      adminRealName: `${opts.adminUsername}-主管`,
      // 手机/邮箱必填；手机取时间戳后 8 位保证跑间不撞。
      adminMobile: `139${String(Date.now()).slice(-8)}`,
      adminEmail: `${opts.adminUsername}@e2e.test`,
      packageCodes: opts.packageCodes ?? [],
    },
  });
  expect(
    res.ok(),
    `开通租户 ${opts.code} 应成功，实际 ${res.status()} ${await res.text()}`,
  ).toBeTruthy();
  const b = (await res.json()) as {
    tenantId: string;
    tenantCode: string;
    adminUsername: string;
    resetToken: string;
  };
  return b;
}

/** 凭 reset/activation token 设密码 + 激活账号（公开端点，无鉴权）。 */
export async function activateByToken(
  request: APIRequestContext,
  token: string,
  username: string,
  password = 'Test@2026!',
): Promise<void> {
  const res = await request.post(`${BACKEND_URL}/api/v1/public/reset-password/by-token/${token}`, {
    data: { usernameInput: username, newPassword: password },
  });
  expect(
    res.ok(),
    `激活 ${username} 应成功，实际 ${res.status()} ${await res.text()}`,
  ).toBeTruthy();
}

/** 租户端表单登录（参数化 tenantCode，供一次性租户用；租户在子域名，整页跳对应子域）。成功后落工作台。 */
export async function tenantLoginAs(
  page: Page,
  tenantCode: string,
  username: string,
  password = 'Test@2026!',
): Promise<void> {
  await page.goto(`http://${tenantCode}.localhost:5174/login`);
  await page.getByPlaceholder('账号 / 手机号').fill(username);
  await page.getByPlaceholder('密码').fill(password);
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await expect(page).toHaveURL(new RegExp(`${tenantCode}\\.localhost:5174/workbench`));
}
