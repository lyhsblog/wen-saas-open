import { defineConfig, devices } from '@playwright/test';

/**
 * live E2E：跑真后端工单全链路（提交→认领/回复→解决→确认关闭 + 附件 + view_all）。
 *
 * 与默认 `playwright.config.ts`（纯 MSW mock、零后端依赖、进 CI）严格隔离：
 * - 默认 config 已加 `testIgnore: tests/e2e/live/**`，故默认 mock 套件不会跑到这里。
 * - 本 config 只跑 `tests/e2e/live/**`，且**不配 webServer** —— 直接复用本机已起的
 *   两个真后端 dev server（tenant 5174 + ops 5173，proxy 打 8080）。这样既避免与
 *   mock 模式（5174 mock）抢端口，又不会误起新 dev server 占端口打扰其他会话。
 *
 * 跑前置：8080 jar + docker PG/Redis/RocketMQ 在跑；5174/5173 真后端 dev server 在跑；
 * 测试账号为本地种子夹具（scripts/seed-live-fixtures.mjs 幂等创建，仅本地联调环境有效）。脚本：`pnpm test:e2e:live`。
 *
 * baseURL 取租户端 5174 的 uitest 子域（租户在子域名；工单全链路主要在租户端发起）；
 * 运营端 5173 在 spec 内用独立 context（baseURL=OPS_BASE_URL）操作。
 */
const TENANT_PORT = 5174;

export default defineConfig({
  testDir: './tests/e2e/live',
  // live 跑真实状态机（PENDING→…→CLOSED 串行依赖），不并行、不重试，保证链路稳定。
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  timeout: 60_000,
  use: {
    baseURL: `http://uitest.localhost:${TENANT_PORT}`,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'live', use: { ...devices['Desktop Chrome'] } }],
  // 刻意不配 webServer：复用已起的真后端 dev server，详见文件头注释。
});
