import { tenantNotificationTemplateApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const notificationTemplateApi = tenantNotificationTemplateApi(http);
