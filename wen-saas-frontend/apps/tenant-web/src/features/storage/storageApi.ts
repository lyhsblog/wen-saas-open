import { tenantStorageApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const storageApi = tenantStorageApi(http);
