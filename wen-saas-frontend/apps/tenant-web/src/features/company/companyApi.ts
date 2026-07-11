import { companyProfileApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const companyApi = companyProfileApi(http);

/** 行业预置项（P0 内置，后续可接字典 INDUSTRY 分组）。 */
export const INDUSTRY_OPTIONS = [
  '互联网',
  '制造业',
  '金融',
  '教育',
  '医疗',
  '零售',
  '物流',
  '建筑',
  '房地产',
  '农业',
  '其他',
];

/** 公司规模预置项。 */
export const SCALE_OPTIONS = [
  '1-10 人',
  '11-50 人',
  '51-200 人',
  '201-500 人',
  '501-1000 人',
  '1000+ 人',
];
