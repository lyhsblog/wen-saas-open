import { roleAdminApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const roleApi = roleAdminApi(http);
