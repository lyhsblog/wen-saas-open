import { tenantAccountApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const accountApi = tenantAccountApi(http);
