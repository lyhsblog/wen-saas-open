import { StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { unstableSetRender } from 'antd-mobile';
import 'antd-mobile/es/global';
import { App } from './App';
import { queryClient } from './lib/query';
import './styles.css';

// antd-mobile v5 的命令式 API（Toast/Dialog/Modal 等）底层用 React 18 的 ReactDOM.render，
// 在 React 19 下会静默失效。按官方 React 19 适配指南注入 createRoot 渲染器。
// https://mobile.ant.design/guide/v5-for-19
type RootContainer = Element & { _reactRoot?: Root };
unstableSetRender((node, container) => {
  const c = container as RootContainer;
  c._reactRoot ||= createRoot(c);
  const root = c._reactRoot;
  root.render(node);
  return async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
    root.unmount();
  };
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
);
