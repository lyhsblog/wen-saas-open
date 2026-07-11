import { configDefaults, defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./tests/setup.ts'],
    // e2e 用例由 Playwright 跑（@playwright/test），不能被 vitest 收集，否则 import 失败。
    exclude: [...configDefaults.exclude, 'tests/e2e/**'],
  },
});
