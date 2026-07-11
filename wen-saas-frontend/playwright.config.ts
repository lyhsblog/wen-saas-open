import { defineConfig, devices } from '@playwright/test';

/**
 * E2E 首套：tenant-web 跑在 MSW mock 模式（`VITE_API_MODE=mock`），
 * 完全自洽、确定性、零后端依赖 —— CI 不必起 jar/docker 即可全绿。
 *
 * 真后端链路（工单 提交→认领→解决→确认 等，见 tests/e2e/README.md「真后端分层」）
 * 重在需 jar+PG/Redis/RocketMQ，作为后续 nightly / 本地分层补，不进默认 CI。
 */
const PORT = 5174;
const isCI = !!process.env.CI;

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  workers: isCI ? 1 : undefined,
  reporter: isCI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    // 租户在子域名：mock 套件固定用 uitest 子域（Chromium 把 *.localhost 原生解析到回环）。
    baseURL: `http://uitest.localhost:${PORT}`,
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      // 真后端 live 套件（工单全链路）走独立 playwright.live.config.ts（复用已起的
      // 真后端 dev server），默认 mock project 排除，保证默认 CI 仍纯 mock、零后端依赖。
      testIgnore: 'live/**',
    },
  ],
  webServer: {
    command: 'corepack pnpm dev:tenant',
    env: { VITE_API_MODE: 'mock' },
    // 探活走裸域（Node 侧 fetch 不保证解析 *.localhost；vite 起来后裸域同样 200）。
    url: `http://localhost:${PORT}/login`,
    reuseExistingServer: !isCI,
    timeout: 120_000,
  },
});
