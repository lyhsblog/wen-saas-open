import type {
  DictGroup,
  DictItem,
  DictGroupCreateRequest,
  DictGroupUpdateRequest,
  DictItemCreateRequest,
  DictItemUpdateRequest,
} from '@wen-saas/domain';
import type { HttpClient } from './http';

export function dictAdminApi(http: HttpClient) {
  const base = '/api/v1/base/dict';
  return {
    async listGroups(): Promise<DictGroup[]> {
      const res = await http.get<DictGroup[]>(`${base}/groups`);
      return res.data;
    },
    async createGroup(body: DictGroupCreateRequest): Promise<DictGroup> {
      const res = await http.post<DictGroup>(`${base}/groups`, body);
      return res.data;
    },
    async updateGroup(groupCode: string, body: DictGroupUpdateRequest): Promise<DictGroup> {
      const res = await http.put<DictGroup>(
        `${base}/groups/${encodeURIComponent(groupCode)}`,
        body,
      );
      return res.data;
    },
    async deleteGroup(groupCode: string): Promise<void> {
      await http.delete(`${base}/groups/${encodeURIComponent(groupCode)}`);
    },
    async setGroupStatus(groupCode: string, enabled: boolean): Promise<DictGroup> {
      const res = await http.put<DictGroup>(
        `${base}/groups/${encodeURIComponent(groupCode)}/status`,
        null,
        { params: { enabled } },
      );
      return res.data;
    },
    async listItems(groupCode: string, includeDisabled = true): Promise<DictItem[]> {
      const res = await http.get<DictItem[]>(
        `${base}/groups/${encodeURIComponent(groupCode)}/items`,
        {
          params: { includeDisabled },
        },
      );
      return res.data;
    },
    async createItem(groupCode: string, body: DictItemCreateRequest): Promise<DictItem> {
      const res = await http.post<DictItem>(
        `${base}/groups/${encodeURIComponent(groupCode)}/items`,
        body,
      );
      return res.data;
    },
    async updateItem(itemId: number, body: DictItemUpdateRequest): Promise<DictItem> {
      const res = await http.put<DictItem>(`${base}/items/${itemId}`, body);
      return res.data;
    },
    async deleteItem(itemId: number): Promise<void> {
      await http.delete(`${base}/items/${itemId}`);
    },
    async setItemStatus(itemId: number, enabled: boolean): Promise<DictItem> {
      const res = await http.put<DictItem>(`${base}/items/${itemId}/status`, null, {
        params: { enabled },
      });
      return res.data;
    },
  };
}
