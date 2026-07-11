import type { HttpClient } from './http';

/** 字段类型 code（创建时提交；后端读出为大写枚举名，consumer 用 toLowerCase 归一即等于此 code）。 */
export type CustomFieldTypeCode =
  | 'text'
  | 'long_text'
  | 'number'
  | 'date'
  | 'bool'
  | 'enum'
  | 'ref';

export interface CustomFieldObject {
  code: string;
  name: string;
  tableName: string;
  extraColumnName: string;
  description: string | null;
}

/** REF 字段可关联的目标对象（内建数据实体 成员/部门 + 已注册业务对象；不含角色 / platform-only）。 */
export interface CustomFieldRefObject {
  code: string;
  label: string;
}

export interface CustomFieldDef {
  id: string;
  tenantId: string;
  objectCode: string;
  fieldCode: string;
  fieldName: string;
  /** 后端序列化为枚举名（TEXT/LONG_TEXT/...）；toLowerCase() 即 {@link CustomFieldTypeCode}。 */
  type: string;
  required: boolean;
  indexed: boolean;
  defaultValue: string | null;
  refDictCode: string | null;
  refObjectCode: string | null;
  displayOrder: number;
}

export interface CustomFieldQuota {
  tenantId: string;
  fieldQuota: number;
  fieldUsed: number;
  indexQuota: number;
  indexUsed: number;
}

export interface CreateFieldRequest {
  objectCode: string;
  fieldCode: string;
  fieldName: string;
  type: CustomFieldTypeCode;
  required: boolean;
  indexed: boolean;
  defaultValue?: string | null;
  refDictCode?: string | null;
  refObjectCode?: string | null;
  displayOrder?: number;
}

/** 字段管理 API。读走 CustomFieldService，写走 CustomFieldDefManager。 */
export function customFieldApi(http: HttpClient) {
  const base = '/api/v1/base/custom-fields';
  return {
    async listObjects(): Promise<CustomFieldObject[]> {
      const res = await http.get<CustomFieldObject[]>(`${base}/objects`);
      return res.data;
    },
    async listRefObjects(): Promise<CustomFieldRefObject[]> {
      const res = await http.get<CustomFieldRefObject[]>(`${base}/ref-objects`);
      return res.data;
    },
    async quota(): Promise<CustomFieldQuota> {
      const res = await http.get<CustomFieldQuota>(`${base}/quota`);
      return res.data;
    },
    async listFields(objectCode: string): Promise<CustomFieldDef[]> {
      const res = await http.get<CustomFieldDef[]>(base, { params: { objectCode } });
      return res.data;
    },
    async createField(body: CreateFieldRequest): Promise<{ id: string }> {
      const res = await http.post<{ id: string }>(base, body);
      return res.data;
    },
    async renameField(id: string, name: string): Promise<void> {
      await http.put(`${base}/${encodeURIComponent(id)}/name`, { name });
    },
    async toggleIndex(id: string, indexed: boolean): Promise<void> {
      await http.put(`${base}/${encodeURIComponent(id)}/index`, { indexed });
    },
    async deleteField(id: string): Promise<void> {
      await http.delete(`${base}/${encodeURIComponent(id)}`);
    },
  };
}
