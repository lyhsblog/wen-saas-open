import fs from 'node:fs';
import path from 'node:path';
import { expect, test, type APIRequestContext } from '@playwright/test';
import {
  activateByToken,
  apiLogin,
  BACKEND_URL,
  bearer,
  createOneTimeTenant,
  LIVE_ACCOUNTS,
  tenantLoginAs,
  type OneTimeTenant,
} from '../helpers';

/**
 * 新租户全生命周期（D1）+ 数据导入全链路（D3）—— 真后端 live，共用一个一次性租户。
 *
 * 一次性租户（code 带时间戳唯一）在 beforeAll 经 ops API 开通，跑后留存（无删租户端点，
 * 与 ticket-fullchain 每跑建工单同口径，不污染 uitest）。D1 主要走 API（快/稳），
 * [D1-激活链接] 这条**前端**缺陷用一个浏览器步回归（成员激活链接须 path 格式、能渲染）。
 *
 * 覆盖第二轮精细测试 D 维度抓到并修复的缺陷回归：
 *   [D1-套餐]      开通向导勾选的业务套餐落库（plans 含 BIZ_TEST_PKG）
 *   [D1-激活链接]  成员激活链接 path 格式（/reset-password/<token>，非 ?token=）→ 不 404
 *   D3            导入预览校验/错误明细/confirm 门控/异步任务/落库
 *
 * 前置：8080 jar + docker PG/Redis/RocketMQ + 5174 真后端 dev server（见 playwright.live.config.ts）。
 */

const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const fixture = (name: string) => path.join(__dirname, 'fixtures', name);

const RUN = Date.now();
const SHORT = String(RUN).slice(-7); // 短后缀：成员账号受 UI「字母开头 4-16 位」约束，不能用全时间戳
// 租户 code 即子域名 label，后端限长 5-16 位，用短后缀。
const TENANT_CODE = `d1auto${SHORT}`;
const ADMIN = `d1admin${RUN}`;
const DEPT_NAME = '自动化部门';
const PASSWORD = 'Test@2026!';

let tenant: OneTimeTenant;
let adminToken = '';
let mainDeptId = '';

async function pollTask(request: APIRequestContext, token: string, taskId: string) {
  // 首发 MQ 消息偶发丢失时由 PendingTaskReaper 兜底重投（卡 PENDING 阈值 2m + 调度间隔 2m，
  // 最坏 ~4m 才执行），等待窗口须覆盖兜底周期；期间 dev access-ttl(2m) 会过期，401 时重取。
  let auth = token;
  for (let i = 0; i < 90; i++) {
    const r = await request.get(`${BACKEND_URL}/api/v1/tasks/${taskId}`, {
      headers: bearer(auth),
    });
    if (r.status() === 401) {
      await reAuth(request);
      auth = adminToken;
    } else {
      const b = (await r.json()) as {
        status: string;
        successCount?: number;
        failedCount?: number;
      };
      if (b.status === 'completed' || b.status === 'failed') return b;
    }
    await new Promise((res) => setTimeout(res, 3000));
  }
  throw new Error(`任务 ${taskId} 超时未完成`);
}

/** 重取主管 token（浏览器步 / 多用例后防 dev access-ttl(2m) 过期）。 */
async function reAuth(request: APIRequestContext): Promise<void> {
  adminToken = await apiLogin(request, {
    username: tenant.adminUsername,
    password: PASSWORD,
    tenantCode: tenant.tenantCode,
  });
}

/** 上传某实体导入文件做预览校验，返回 parse 结果。 */
async function parseImport(request: APIRequestContext, entity: string, file: string) {
  const res = await request.post(`${BACKEND_URL}/api/v1/imports/${entity}/parse`, {
    headers: bearer(adminToken),
    multipart: {
      file: { name: file, mimeType: XLSX_MIME, buffer: fs.readFileSync(fixture(file)) },
    },
  });
  expect(res.ok(), `parse ${file} 应 2xx，实际 ${res.status()}`).toBeTruthy();
  return (await res.json()) as { sessionId: string; total: number; passed: number; failed: number };
}

/** confirm 某 session 并轮询其异步任务到终态。 */
async function confirmImport(request: APIRequestContext, sessionId: string) {
  const cf = await request.post(`${BACKEND_URL}/api/v1/imports/sessions/${sessionId}/confirm`, {
    headers: bearer(adminToken),
  });
  expect(cf.ok(), `confirm 应成功，实际 ${cf.status()}`).toBeTruthy();
  return pollTask(request, adminToken, (await cf.json()).taskId as string);
}

test.describe.serial('新租户全生命周期 + 数据导入（真后端 live）', () => {
  // 导入任务可能落在 PendingTaskReaper 兜底重投窗口（最坏 ~4m），见 pollTask。
  test.setTimeout(300_000);

  test.beforeAll(async ({ request }) => {
    const opsToken = await apiLogin(request, LIVE_ACCOUNTS.opsSuper);
    tenant = await createOneTimeTenant(request, opsToken, {
      code: TENANT_CODE,
      name: `D1自动化租户-${RUN}`,
      adminUsername: ADMIN,
      packageCodes: ['BIZ_TEST_PKG'], // 验 [D1-套餐] 落库
    });
  });

  test('[D1] 开通 → 主管激活首登 → 套餐落库（BIZ_TEST_PKG）', async ({ request }) => {
    // 主管理员凭 resetToken 设密码 + 激活。
    await activateByToken(request, tenant.resetToken, tenant.adminUsername, PASSWORD);
    adminToken = await apiLogin(request, {
      username: tenant.adminUsername,
      password: PASSWORD,
      tenantCode: tenant.tenantCode,
    });
    expect(adminToken, '主管激活后应能登录拿 token').toBeTruthy();

    // [D1-套餐] 回归：开通时勾选的业务套餐应落库，租户「套餐与账号」能看到。
    const ov = await request.get(`${BACKEND_URL}/api/v1/base/account/overview`, {
      headers: bearer(adminToken),
    });
    expect(ov.ok()).toBeTruthy();
    const plans = ((await ov.json()).plans ?? []) as Array<{ packageCode: string }>;
    const codes = plans.map((p) => p.packageCode);
    expect(codes, '应含系统基础包 base').toContain('base');
    expect(codes, '[D1-套餐] 开通勾选的 BIZ_TEST_PKG 应落库').toContain('BIZ_TEST_PKG');
  });

  test('[D1] 主管建部门 + 建成员 → 成员激活可登录', async ({ request }) => {
    // 建部门。
    const dRes = await request.post(`${BACKEND_URL}/api/v1/base/depts`, {
      headers: bearer(adminToken),
      data: { name: DEPT_NAME },
    });
    expect(dRes.ok(), `建部门应成功，实际 ${dRes.status()}`).toBeTruthy();
    mainDeptId = (await dRes.json()).id as string;
    expect(mainDeptId).toBeTruthy();

    // 建成员（API 返回 activationToken）。username 限 16 位内用短后缀；手机号必填、取时间戳后 8 位防撞。
    const memberUsername = `d1mem${SHORT}`;
    const mRes = await request.post(`${BACKEND_URL}/api/v1/base/members`, {
      headers: bearer(adminToken),
      data: {
        username: memberUsername,
        realName: '自动化成员',
        mainDeptId,
        mobile: `138${String(Date.now()).slice(-8)}`,
      },
    });
    expect(mRes.ok(), `建成员应成功，实际 ${mRes.status()} ${await mRes.text()}`).toBeTruthy();
    const activationToken = (await mRes.json()).activationToken as string;
    expect(activationToken, '建成员应返回 activationToken').toBeTruthy();

    // 成员凭 activationToken 激活 → 登录成功（证明新租户成员链路打通）。
    await activateByToken(request, activationToken, memberUsername, PASSWORD);
    const memberToken = await apiLogin(request, {
      username: memberUsername,
      password: PASSWORD,
      tenantCode: tenant.tenantCode,
    });
    expect(memberToken, '成员激活后应能登录').toBeTruthy();
  });

  test('[D1-激活链接] 成员激活链接为 path 格式且不 404（浏览器）', async ({ page }) => {
    await tenantLoginAs(page, tenant.tenantCode, tenant.adminUsername, PASSWORD);
    await page.getByRole('navigation').getByText('通讯录').click();
    await expect(page).toHaveURL(new RegExp(`${tenant.tenantCode}\\.localhost:5174/contacts`));

    // 选中部门（前一用例 API 建的 DEPT_NAME）→ 添加成员。
    await page.getByText(DEPT_NAME).first().click();
    await page.getByRole('button', { name: /添\s*加\s*成\s*员/ }).click();
    const modal = page.getByRole('dialog');
    await expect(modal.getByText('添加成员')).toBeVisible();
    await modal.getByPlaceholder('如 zhangsan').fill(`d1ui${SHORT}`);
    await modal.getByPlaceholder('如 张三').fill('UI建成员');
    // 手机号必填；取时间戳后 8 位防撞。
    await modal.getByPlaceholder('请输入 11 位手机号').fill(`137${String(Date.now()).slice(-8)}`);
    await modal.getByRole('button', { name: /保存并生成激活链接/ }).click();

    // 「激活链接已生成」弹窗里读链接 —— 回归 [D1-激活链接]：须租户子域 + path 格式
    // `http://{code}.localhost:5174/reset-password/<token>`，不得是旧的 query 格式 `?token=`。
    await expect(page.getByText('激活链接已生成')).toBeVisible({ timeout: 15_000 });
    const linkText = await page
      .getByText(/reset-password/)
      .first()
      .innerText();
    expect(linkText).toMatch(
      new RegExp(`//${tenant.tenantCode}\\.localhost:5174/reset-password/[^?\\s]+$`),
    );
    expect(linkText, '不应是旧的 ?token= query 格式').not.toContain('?token=');

    // 真打开该链接（完整 URL，含租户子域）→ 渲染设密码页，而非「页面不存在」。
    await page.goto(linkText.trim());
    await expect(page.getByText('页面不存在')).toHaveCount(0);
    await expect(page.getByText(/设置初始密码|设置.*密码/).first()).toBeVisible();
  });

  test('[D3] 部门导入：错误预览 + 错误明细 + confirm 门控', async ({ request }) => {
    adminToken = await apiLogin(request, {
      username: tenant.adminUsername,
      password: PASSWORD,
      tenantCode: tenant.tenantCode,
    }); // 浏览器步后重取 token，防 dev access-ttl(2m) 过期
    const buf = fs.readFileSync(fixture('dept-import-errors.xlsx'));
    const parseRes = await request.post(`${BACKEND_URL}/api/v1/imports/sys.dept/parse`, {
      headers: bearer(adminToken),
      multipart: {
        file: { name: 'dept-import-errors.xlsx', mimeType: XLSX_MIME, buffer: buf },
      },
    });
    expect(parseRes.ok()).toBeTruthy();
    const parsed = (await parseRes.json()) as {
      sessionId: string;
      total: number;
      passed: number;
      failed: number;
    };
    expect(parsed.total).toBe(4);
    expect(parsed.passed).toBe(1);
    expect(parsed.failed).toBe(3);

    // 错误明细可下载（xlsx）。
    const errRes = await request.get(
      `${BACKEND_URL}/api/v1/imports/sessions/${parsed.sessionId}/errors`,
      { headers: bearer(adminToken) },
    );
    expect(errRes.ok()).toBeTruthy();
    expect(errRes.headers()['content-type']).toContain('spreadsheetml');

    // confirm 门控：有错误行 → 409 HAS_ERRORS，不导入。
    const cf = await request.post(
      `${BACKEND_URL}/api/v1/imports/sessions/${parsed.sessionId}/confirm`,
      { headers: bearer(adminToken) },
    );
    expect(cf.status(), '有错误行 confirm 应 409').toBe(409);
  });

  test('[D3] 部门导入：全正常 → 异步任务 → 落库', async ({ request }) => {
    adminToken = await apiLogin(request, {
      username: tenant.adminUsername,
      password: PASSWORD,
      tenantCode: tenant.tenantCode,
    });
    const buf = fs.readFileSync(fixture('dept-import-ok.xlsx'));
    const parseRes = await request.post(`${BACKEND_URL}/api/v1/imports/sys.dept/parse`, {
      headers: bearer(adminToken),
      multipart: { file: { name: 'dept-import-ok.xlsx', mimeType: XLSX_MIME, buffer: buf } },
    });
    expect(parseRes.ok()).toBeTruthy();
    const parsed = (await parseRes.json()) as { sessionId: string; passed: number; failed: number };
    expect(parsed.passed).toBe(2);
    expect(parsed.failed).toBe(0);

    const cf = await request.post(
      `${BACKEND_URL}/api/v1/imports/sessions/${parsed.sessionId}/confirm`,
      { headers: bearer(adminToken) },
    );
    expect(cf.ok(), `全正常 confirm 应成功，实际 ${cf.status()}`).toBeTruthy();
    const taskId = (await cf.json()).taskId as string;

    const task = await pollTask(request, adminToken, taskId);
    expect(task.status).toBe('completed');
    expect(task.successCount).toBe(2);

    // 落库核对：部门树出现导入的两个部门（不查库、走列表 API）。
    const tree = await request.get(`${BACKEND_URL}/api/v1/base/depts`, {
      headers: bearer(adminToken),
    });
    expect(tree.ok()).toBeTruthy();
    const names = JSON.stringify(await tree.json());
    expect(names).toContain('D1自动导入甲');
    expect(names).toContain('D1自动导入乙');
  });

  test('[D3] 成员导入：错误校验（手机/邮箱/部门/必填）+ 全正常落库', async ({ request }) => {
    await reAuth(request);
    // 错误预览：手机123 / 邮箱bad / 部门不存在 / 账号空 → 1 通过 3 错误。
    const err = await parseImport(request, 'sys.member', 'member-import-errors.xlsx');
    expect(err.total).toBe(4);
    expect(err.passed).toBe(1);
    expect(err.failed).toBe(3);
    // 全正常 → 任务 → 落库（成员列表含导入成员）。
    const ok = await parseImport(request, 'sys.member', 'member-import-ok.xlsx');
    expect(ok.passed).toBe(1);
    expect(ok.failed).toBe(0);
    const task = await confirmImport(request, ok.sessionId);
    expect(task.status).toBe('completed');
    expect(task.successCount).toBe(1);
    const list = await request.get(`${BACKEND_URL}/api/v1/base/members?page=1&size=50`, {
      headers: bearer(adminToken),
    });
    expect(JSON.stringify(await list.json())).toContain('导入成员九');
  });

  test('[D3] 字典导入：错误校验（分组不存在/必填）+ 全正常落库', async ({ request }) => {
    await reAuth(request);
    const err = await parseImport(request, 'sys.dict', 'dict-import-errors.xlsx');
    expect(err.total).toBe(2);
    expect(err.failed).toBe(2);
    // 往全局预置分组 POSITION 追加两项 → 任务 → 落库。
    const ok = await parseImport(request, 'sys.dict', 'dict-import-ok.xlsx');
    expect(ok.passed).toBe(2);
    const task = await confirmImport(request, ok.sessionId);
    expect(task.status).toBe('completed');
    expect(task.successCount).toBe(2);
    const items = await request.get(`${BACKEND_URL}/api/v1/base/dict/groups/POSITION/items`, {
      headers: bearer(adminToken),
    });
    expect(JSON.stringify(await items.json())).toContain('D3_AUTO_A');
  });
});
