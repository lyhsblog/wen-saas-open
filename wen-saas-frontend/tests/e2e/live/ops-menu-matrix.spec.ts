import { expect, test } from '@playwright/test';
import { LIVE_ACCOUNTS, liveOpsLoginAs, OPS_BASE_URL } from '../helpers';

/**
 * 运营端侧栏菜单按权限收敛（[A1-菜单]）—— 浏览器 live（ops-web 5173）。
 * 回归 OpsAppShell 按 capabilities 过滤菜单的修复（原零过滤：CSM 侧栏与超管完全一致）。
 *
 * baseURL 默认 5174（见 playwright.live.config.ts），故每个角色用指向 5173 的独立 context。
 * 账号为本地种子夹具（seed-live-fixtures.mjs 创建，仅本地联调环境有效；ops-csm=OPS_CSM，本机 MFA 关闭）。
 *
 * 注：越权「直达 URL」的数据安全由后端 403 兜底，已在 permission-matrix.spec.ts 接口级覆盖；
 * 此处只断言纯前端的菜单收敛（无 anyOf 权限即不渲染该项）。
 */

/** 工作台/租户/工单/健康度：CSM 应可见（dashboard/tenant.list/ticket.view/health.view）。 */
const VISIBLE_FOR_CSM = ['工作台', '租户管理', '工单管理', '客户健康度'];
/** 套餐/推荐角色/通知模板/合规：CSM 无权 → 应隐藏。 */
const HIDDEN_FOR_CSM = ['套餐包管理', '推荐角色', '通知模板', '数据隐私与合规'];

async function assertMenu(
  browser: import('@playwright/test').Browser,
  creds: { username: string; password: string },
  hidden: string[],
) {
  const ctx = await browser.newContext({ baseURL: OPS_BASE_URL });
  const page = await ctx.newPage();
  try {
    await liveOpsLoginAs(page, creds.username, creds.password);
    const nav = page.getByRole('navigation');
    await expect(nav).toBeVisible();
    for (const label of VISIBLE_FOR_CSM) {
      await expect(nav.getByText(label, { exact: true }), `${label} 应可见`).toBeVisible();
    }
    for (const label of hidden) {
      await expect(nav.getByText(label, { exact: true }), `${label} 应隐藏`).toHaveCount(0);
    }
  } finally {
    await ctx.close();
  }
}

test.describe('运营菜单收敛（真后端 live · ops-web）', () => {
  test('[A1-菜单] CSM：隐藏套餐/推荐角色/通知模板/合规，保留工作台/租户/工单/健康度', async ({
    browser,
  }) => {
    await assertMenu(browser, LIVE_ACCOUNTS.opsCsm, HIDDEN_FOR_CSM);
  });
});
