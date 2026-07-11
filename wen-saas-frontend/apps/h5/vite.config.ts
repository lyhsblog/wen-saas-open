import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// 开发态 CSP（宽松：含 Vite HMR 需要的 unsafe-inline/unsafe-eval + antd-mobile CSS-in-JS）。
// 生产严格策略由网关下发。
const DEV_CSP =
  "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'self'; " +
  "img-src 'self' data: blob:; font-src 'self' data:; style-src 'self' 'unsafe-inline'; " +
  "script-src 'self' 'unsafe-inline' 'unsafe-eval'; connect-src 'self' ws:; worker-src 'self' blob:";

export default defineConfig({
  cacheDir: '../../.cache/vite/h5',
  plugins: [react()],
  server: {
    // 允许 {tenantKey}.localhost 子域访问 dev server（租户由子域名区分）。
    allowedHosts: ['.localhost'],
    headers: { 'Content-Security-Policy': DEV_CSP },
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: false,
      },
      '/actuator': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: false,
      },
    },
  },
});
