import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { liveOpsLogin, liveTenantLogin, OPS_BASE_URL } from '../helpers';

/**
 * 工单全链路 live E2E（真后端 5174 租户 + 5173 运营，proxy 打 8080）。
 *
 * 串行单测覆盖完整状态机：
 *   租户提交(PENDING) → 上传附件 → 运营认领(PROCESSING) → 运营回复 →
 *   运营标记已解决(RESOLVED) → 租户确认关闭(CLOSED) → 租户重启(reopen→PROCESSING)
 * 另含：附件签名 URL 下载、base.ticket.view_all 可见性。
 *
 * 选择器约定见 tests/e2e/README.md：AntD6 CJK 按钮文案插空格用 `\s*` 兜底；
 * 侧栏菜单项是自定义 div，用 navigation 区文本定位。雪花 id 全程按字符串处理。
 *
 * 注意：本套件依赖真实后端状态，**不可并行 / 不重试**（见 playwright.live.config.ts）。
 */

// Playwright 将 spec 转译为 CJS 运行，可直接用 __dirname。
const ATTACHMENT = path.join(__dirname, 'fixtures', 'attachment.txt');

/** 每次跑用唯一标题，避免历史数据干扰断言（列表里能精确定位本次工单）。 */
const RUN_TAG = `R4-E2E-${Date.now()}`;
const TICKET_TITLE = `${RUN_TAG} 工单全链路联调`;

/** 进租户「平台工单」页（SPA 内点侧栏菜单，贴近真实流）。 */
async function gotoTenantTickets(page: Page) {
  await page.getByRole('navigation').getByText('平台工单').click();
  await expect(page).toHaveURL(/\/tickets/);
  await expect(page.getByRole('button', { name: /提\s*交\s*工\s*单/ })).toBeVisible();
}

test.describe.serial('工单全链路（真后端 live）', () => {
  // 跨用例共享：租户提交后拿到的工单标题用于两端定位，附件 id 用于下载断言。
  test.setTimeout(60_000);

  test('租户提交工单 + 上传附件', async ({ page }) => {
    await liveTenantLogin(page);
    await gotoTenantTickets(page);

    // —— 打开提交弹窗，填标题/描述（类型默认 BUG、优先级默认 NORMAL，无需改）。
    await page.getByRole('button', { name: /提\s*交\s*工\s*单/ }).click();
    const modal = page.getByRole('dialog');
    await expect(modal.getByText('提交工单')).toBeVisible();
    await modal.getByPlaceholder('一句话描述问题').fill(TICKET_TITLE);
    await modal
      .getByPlaceholder('详细描述问题现象、复现步骤、期望结果等')
      .fill('R4 live E2E 自动化提交，验证工单全链路状态机。');
    await modal.getByRole('button', { name: /提\s*交/ }).click();

    // 提交成功 → toast + 右侧详情面板打开（URL 带 selected）。
    await expect(page.getByText('工单已提交')).toBeVisible();
    await expect(page).toHaveURL(/[?&]selected=\d+/);

    // 详情面板：标题可见；状态「待处理」以列表行 cell 断言（页面另有同名筛选按钮/隐藏 span）。
    const detail = page.locator('text=' + TICKET_TITLE).first();
    await expect(detail).toBeVisible();
    await expect(page.getByRole('cell', { name: '待处理' }).first()).toBeVisible();

    // —— 上传附件（PENDING 状态下回复区可见上传按钮，hidden file input）。
    await page.setInputFiles('input[type="file"]', ATTACHMENT);
    // 上传成功后详情刷新，「工单附件」区出现附件名。
    await expect(page.getByText('工单附件')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('attachment.txt').first()).toBeVisible();
  });

  test('运营认领 → 回复 → 标记已解决', async ({ browser }) => {
    // 运营端独立 context，baseURL 指 5173。
    const ctx = await browser.newContext({ baseURL: OPS_BASE_URL });
    const page = await ctx.newPage();
    try {
      await liveOpsLogin(page);

      // 进工单管理页（运营侧栏项是自定义 div、非 antd menuitem，用 navigation 区文本定位）。
      await page.getByRole('navigation').getByText('工单管理').click();
      await expect(page).toHaveURL(/\/tickets/);

      // 列表里搜本次工单标题，点开详情。
      await page.getByPlaceholder('搜索标题 / 工单号').first().fill(TICKET_TITLE);
      await page.getByPlaceholder('搜索标题 / 工单号').first().press('Enter');
      const row = page.getByText(TICKET_TITLE).first();
      await expect(row).toBeVisible({ timeout: 15_000 });
      await row.click();

      // 详情面板：待处理 → 点「认领」→ 处理中。
      await expect(page.getByText('待处理').first()).toBeVisible();
      await page.getByRole('button', { name: /认\s*领/ }).click();
      await expect(page.getByText('处理中').first()).toBeVisible({ timeout: 15_000 });

      // 回复（PROCESSING 下回复区可见）。
      await page.getByPlaceholder(/输入回复内容/).fill('客服已收到，正在处理你的工单。');
      await page.getByRole('button', { name: /发\s*送\s*回\s*复/ }).click();
      await expect(page.getByText('客服已收到，正在处理你的工单。')).toBeVisible({
        timeout: 15_000,
      });

      // 标记已解决 → RESOLVED。
      await page.getByRole('button', { name: /标\s*记\s*已\s*解\s*决/ }).click();
      await expect(page.getByText('已解决').first()).toBeVisible({ timeout: 15_000 });
    } finally {
      await ctx.close();
    }
  });

  test('租户确认关闭 → 再重启', async ({ page }) => {
    await liveTenantLogin(page);
    await gotoTenantTickets(page);

    // 搜不到时直接点列表里的标题（默认「我的工单」即含本工单）。
    const row = page.getByText(TICKET_TITLE).first();
    await expect(row).toBeVisible({ timeout: 15_000 });
    await row.click();

    // 详情：已解决 → 看到运营回复同步过来 + 「确认已解决」按钮。
    await expect(page.getByRole('cell', { name: '已解决' }).first()).toBeVisible();
    await expect(page.getByText('客服已收到，正在处理你的工单。')).toBeVisible();

    // 确认已解决（Popconfirm）→ CLOSED。
    await page.getByRole('button', { name: /确\s*认\s*已\s*解\s*决/ }).click();
    await page.getByRole('button', { name: /确\s*认\s*解\s*决/ }).click(); // Popconfirm okText
    await expect(page.getByRole('cell', { name: '已关闭' }).first()).toBeVisible({
      timeout: 15_000,
    });

    // —— 重启路径：CLOSED → 点「重新打开」(Popconfirm) → PROCESSING。
    await page
      .getByRole('button', { name: /重\s*新\s*打\s*开/ })
      .first()
      .click();
    // Popconfirm 确认按钮 okText 同为「重新打开」，取浮层里的那个。
    await page
      .locator('.ant-popover, .ant-popconfirm')
      .getByRole('button', { name: /重\s*新\s*打\s*开/ })
      .click();
    await expect(page.getByRole('cell', { name: '处理中' }).first()).toBeVisible({
      timeout: 15_000,
    });
  });

  test('base.ticket.view_all：可拉本租户全部工单', async ({ page }) => {
    // uiadmin 是租户主管理员，含 base.ticket.view_all → 列表出现「全部工单」切换。
    await liveTenantLogin(page);
    await gotoTenantTickets(page);

    const allTab = page.getByRole('button', { name: /全\s*部\s*工\s*单/ });
    await expect(allTab).toBeVisible();

    // 直接断言 /all 接口在「全部工单」视图下被调用且 2xx，并能看到本次工单。
    const allResp = page.waitForResponse(
      (r) => r.url().includes('/api/v1/base/tickets/all') && r.request().method() === 'GET',
    );
    await allTab.click();
    const resp = await allResp;
    expect(resp.ok()).toBeTruthy();
    await expect(page.getByText(TICKET_TITLE).first()).toBeVisible({ timeout: 15_000 });

    // 否定用例（无 view_all 的普通成员看不到全部工单）需另造受限成员账号、
    // 走密码重置激活，成本高且会污染租户成员数据；本次不做。
    // TODO(R4-后续): 如需补否定用例，用 ops 开通向导建一个不含 base.ticket.view_all 的成员，
    // 断言其 TicketListPane 无「全部工单」切换、且直接 GET /all 返回 403。
  });

  test('附件签名 URL 可下载', async ({ page }) => {
    await liveTenantLogin(page);
    await gotoTenantTickets(page);

    const row = page.getByText(TICKET_TITLE).first();
    await expect(row).toBeVisible({ timeout: 15_000 });
    await row.click();

    await expect(page.getByText('工单附件')).toBeVisible({ timeout: 15_000 });
    // 点附件 chip → 前端调 /attachments/{id}/url 拿签名地址再 window.open。
    // 拦截该请求断言签名 URL 拿得到，并直接 GET 验证可下载（2xx）。
    const urlResp = page.waitForResponse(
      (r) =>
        /\/api\/v1\/base\/tickets\/\d+\/attachments\/\d+\/url/.test(r.url()) &&
        r.request().method() === 'GET',
    );
    await page.getByText('attachment.txt').first().click();
    const resp = await urlResp;
    expect(resp.ok()).toBeTruthy();
    const body = (await resp.json()) as { url?: string };
    expect(body.url, '签名下载地址应非空').toBeTruthy();

    // 用浏览器上下文直接 GET 签名地址，验证真能下到内容（2xx）。
    // 签名地址是相对路径且与 host 无关；Node 侧对 *.localhost 的解析不稳定，显式落到 localhost 请求。
    const dl = await page.request.get(
      new URL(body.url as string, 'http://localhost:5174').toString(),
    );
    expect(dl.ok(), `签名 URL 下载应成功，实际 ${dl.status()}`).toBeTruthy();
  });
});
