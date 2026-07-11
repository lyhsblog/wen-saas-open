import { bizFileApi, crmCustomerApi, type BizFileOwner } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const crmApi = crmCustomerApi(http);
export const bizApi = bizFileApi(http);

/** objectCode（与后端 @CustomFieldExtension / CustomFieldObjectDef 一致）。 */
export const CRM_CUSTOMER_OBJECT = 'crm_demo.customer';

/** 客户附件的 sys_file owner 三元组（与后端 CrmDemoFilePermissionChecker 一致）。 */
export const customerAttachmentOwner = (customerId: string): BizFileOwner => ({
  ownerModule: 'crm_demo',
  ownerRefType: 'customer',
  ownerRefId: customerId,
});
