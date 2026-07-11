import {
  tenantWorkbenchApi,
  tenantTicketApi,
  messageInboxApi,
  tenantProfileApi,
  deptAdminApi,
  memberAdminApi,
  fileCenterApi,
  crmCustomerApi,
  tenantStickyNoteApi,
  stepUpApi,
} from '@wen-saas/api-client';
import { http } from './http';

/** H5 复用 PC 同源的业务 API（纯 axios 工厂，无 UI 依赖），统一在此实例化一次。 */
export const workbenchApi = tenantWorkbenchApi(http);
export const ticketApi = tenantTicketApi(http);
export const inboxApi = messageInboxApi(http);
export const profileApi = tenantProfileApi(http);
export const deptApi = deptAdminApi(http);
export const memberApi = memberAdminApi(http);
export const fileApi = fileCenterApi(http);
export const crmApi = crmCustomerApi(http);
export const stickyNoteApi = tenantStickyNoteApi(http);
export const stepUp = stepUpApi(http);
