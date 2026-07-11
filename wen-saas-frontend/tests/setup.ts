import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { resetMockState } from '@wen-saas/api-client/mocks';
import { server } from './msw';

// MSW 共享 server：dev 用 service worker，test 用 node interceptor。
// onUnhandledRequest='error' 强制每个网络调用都得有 handler，避免漏 mock 静悄悄走真后端。
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  server.resetHandlers();
  resetMockState();
});
afterAll(() => server.close());

// jsdom 不实现 matchMedia / ResizeObserver；AntD 响应式组件会用到，
// 在 setup 里做最小 stub 让组件测试跑得起来。
if (typeof window !== 'undefined') {
  if (!window.matchMedia) {
    window.matchMedia = (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    });
  }
  if (typeof window.ResizeObserver === 'undefined') {
    window.ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  }
  // jsdom 不实现 Element.scrollIntoView；TabsBar 激活页签滚入可视区会用到。
  if (typeof Element !== 'undefined' && !Element.prototype.scrollIntoView) {
    Element.prototype.scrollIntoView = function scrollIntoView() {};
  }
}
