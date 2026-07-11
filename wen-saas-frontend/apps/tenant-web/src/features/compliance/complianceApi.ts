import { tenantComplianceApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const complianceApi = tenantComplianceApi(http);
