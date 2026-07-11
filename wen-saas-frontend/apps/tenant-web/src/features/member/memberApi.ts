import { memberAdminApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const memberApi = memberAdminApi(http);
