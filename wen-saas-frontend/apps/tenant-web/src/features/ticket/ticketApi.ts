import { tenantTicketApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const ticketApi = tenantTicketApi(http);
