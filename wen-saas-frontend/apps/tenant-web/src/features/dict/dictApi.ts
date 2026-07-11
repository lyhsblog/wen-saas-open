import { dictAdminApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const dictApi = dictAdminApi(http);
