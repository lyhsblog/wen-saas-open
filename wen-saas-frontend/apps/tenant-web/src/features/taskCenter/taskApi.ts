import { tasksApi, importsApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const taskApi = tasksApi(http);
export const importApi = importsApi(http);
