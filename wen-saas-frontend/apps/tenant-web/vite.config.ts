import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// 开发态 CSP（宽松：含 Vite HMR 需要的 unsafe-inline/unsafe-eval + antd CSS-in-JS）。
// 生产严格策略由网关下发。
const DEV_CSP =
  "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'self'; " +
  "img-src 'self' data: blob:; font-src 'self' data:; style-src 'self' 'unsafe-inline'; " +
  // 允许 page-agent demo CDN 脚本加载，及其调用的外部 LLM 接口
  "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://registry.npmmirror.com https://cdn.jsdelivr.net; " +
  "connect-src 'self' ws: https:; worker-src 'self' blob:";

export default defineConfig({
  cacheDir: '../../.cache/vite/tenant-web',
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
