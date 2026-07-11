import { setupServer } from 'msw/node';
import { handlers } from '@wen-saas/api-client/mocks';

/**
 * vitest 共享 MSW server。与 tenant-web dev 期 service worker 复用同一份 handlers，
 * 单测里就能直接调 `dictApi.listGroups()` 等真实 HTTP 调用，吃到 seeded fixture。
 * setup.ts 在测试 lifecycle 上 listen/reset/close。
 */
export const server = setupServer(...handlers);
