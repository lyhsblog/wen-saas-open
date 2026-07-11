import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { App } from './App';
import { queryClient } from './lib/query';
import './styles.css';

// 日历 / 日期选择器走中文（中文月份、星期、周一为周首）。
dayjs.locale('zh-cn');

async function bootstrap() {
  // 仅在 dev + VITE_API_MODE=mock 时启用 service worker；生产构建不会走这条分支。
  if (import.meta.env.DEV && import.meta.env.VITE_API_MODE === 'mock') {
    const { worker } = await import('./mocks/browser');
    await worker.start({
      onUnhandledRequest: 'bypass',
      serviceWorker: { url: '/mockServiceWorker.js' },
    });
  }

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </StrictMode>,
  );
}

void bootstrap();
