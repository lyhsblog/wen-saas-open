import { type ReactElement } from 'react';
import { createBrowserRouter, Navigate } from 'react-router';
import { LoginPage } from './features/auth/LoginPage';
import { RequireAuth } from './features/auth/RequireAuth';
import { WorkspacePage } from './features/workspace/WorkspacePage';
import { TodosPage } from './features/todos/TodosPage';
import { TicketDetailPage } from './features/todos/TicketDetailPage';
import { MessagesPage } from './features/messages/MessagesPage';
import { MessageDetailPage } from './features/messages/MessageDetailPage';
import { ProfilePage } from './features/profile/ProfilePage';
import { ContactsPage } from './features/contacts/ContactsPage';
import { FilesPage } from './features/files/FilesPage';
import { StickyNotePage } from './features/stickyNote/StickyNotePage';
import { H5RootGate } from './components/H5RootGate';
import { moduleRoutes } from './lib/module-routes';

function guarded(node: ReactElement): ReactElement {
  return <RequireAuth>{node}</RequireAuth>;
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  // 全屏二级页（不带底部 TabBar）
  { path: '/tickets/:ticketId', element: guarded(<TicketDetailPage />) },
  { path: '/messages/:messageId', element: guarded(<MessageDetailPage />) },
  { path: '/contacts', element: guarded(<ContactsPage />) },
  { path: '/files', element: guarded(<FilesPage />) },
  { path: '/sticky-notes', element: guarded(<StickyNotePage />) },
  // 业务模块页：由 lib/modules.ts 的 manifest（h5Routes）动态挂载，
  // 每条已包 登录守卫 + 套餐门禁 + 权限门禁（见 lib/module-routes.tsx）
  ...moduleRoutes,
  // 根 + 4-tab 主壳：有租户子域渲染主壳，裸域渲染调试入口
  {
    path: '/',
    element: <H5RootGate />,
    children: [
      { index: true, element: <Navigate to="workspace" replace /> },
      { path: 'workspace', element: <WorkspacePage /> },
      { path: 'todos', element: <TodosPage /> },
      { path: 'messages', element: <MessagesPage /> },
      { path: 'profile', element: <ProfilePage /> },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]);
