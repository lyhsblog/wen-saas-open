import { http, HttpResponse } from 'msw';
import type { UnreadSummary } from '../../messageInbox';

/**
 * 消息收件箱。当前仅 mock 顶部铃铛的未读数轮询，
 * 让 AppShell 在 mock 模式下不旁路到真后端（其余收件箱端点按需再补）。
 */
export const messageInboxHandlers = [
  http.get('/api/v1/messages/inbox/unread-count', () =>
    HttpResponse.json({ total: 0, byCategory: [] } as UnreadSummary),
  ),
];
