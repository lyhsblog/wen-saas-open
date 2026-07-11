import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
import { version } from './package.json';

// 打包日期（构建机本地时区），登录页 footer 展示用
const buildDate = new Date()
  .toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
  .replaceAll('/', '.');

// 开发态 CSP（宽松：含 Vite HMR 需要的 unsafe-inline/unsafe-eval + antd CSS-in-JS）。
// 生产严格策略由网关下发。
const DEV_CSP =
  "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'self'; " +
  "img-src 'self' data: blob:; font-src 'self' data:; style-src 'self' 'unsafe-inline'; " +
  "script-src 'self' 'unsafe-inline' 'unsafe-eval'; connect-src 'self' ws:; worker-src 'self' blob:";

export default defineConfig({
  cacheDir: '../../.cache/vite/ops-web',
  plugins: [react()],
  define: {
    __APP_VERSION__: JSON.stringify(version),
    __BUILD_DATE__: JSON.stringify(buildDate),
  },
  server: {
    // 本地运营端走 ops.localhost:5173，与线上 ops. 子域一致；放行 .localhost 子域。
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
