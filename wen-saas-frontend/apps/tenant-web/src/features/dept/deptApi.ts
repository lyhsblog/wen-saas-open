import { deptAdminApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const deptApi = deptAdminApi(http);
