import { tenantWebhookApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const webhookApi = tenantWebhookApi(http);
