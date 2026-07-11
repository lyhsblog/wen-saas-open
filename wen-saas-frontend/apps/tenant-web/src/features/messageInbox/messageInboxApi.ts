import { messageInboxApi as factory } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const messageInboxApi = factory(http);
