import { tenantWatermarkApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const watermarkApi = tenantWatermarkApi(http);
