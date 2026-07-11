import { customFieldApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const cfApi = customFieldApi(http);
