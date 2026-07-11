import { fileCenterApi as factory } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const fileCenterApi = factory(http);
