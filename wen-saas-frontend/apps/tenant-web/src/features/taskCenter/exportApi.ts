import { exportsApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const exportApi = exportsApi(http);
