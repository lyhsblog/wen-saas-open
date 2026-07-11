import { describe, expect, it } from 'vitest';
import { createApiClient } from './http';
import { dictAdminApi } from './dict';
import { ApiError } from './http';

/**
 * 直接打 MSW handler（见 tests/setup.ts 的 server.listen）。
 * 验证 dictAdminApi → handlers 的端到端串联，同时为后续业务模块 query 测试垫底。
 */
const http = createApiClient({
  baseURL: '',
  getAuth: () => ({ token: 'mock-jwt-token-for-alice', tenantId: '9701' }),
});
const api = dictAdminApi(http);

describe('dictAdminApi · MSW e2e', () => {
  it('listGroups 返回 seed 的两个分组', async () => {
    const groups = await api.listGroups();
    const codes = groups.map((g) => g.code);
    expect(codes).toContain('PROJECT_STAGE');
    expect(codes).toContain('REGION');
  });

  it('createGroup 走通 + 重复 code 抛 ApiError 409', async () => {
    const fresh = await api.createGroup({
      code: 'TEST_GROUP_1',
      name: '测试分组',
      supportHierarchy: false,
    });
    expect(fresh.code).toBe('TEST_GROUP_1');

    await expect(
      api.createGroup({ code: 'TEST_GROUP_1', name: '同 code', supportHierarchy: false }),
    ).rejects.toMatchObject({ status: 409 });
  });

  it('deleteGroup 不存在抛 404', async () => {
    await expect(api.deleteGroup('NONEXIST')).rejects.toBeInstanceOf(ApiError);
    await expect(api.deleteGroup('NONEXIST')).rejects.toMatchObject({ status: 404 });
  });

  it('updateItem 修改 name 持久化', async () => {
    const before = await api.listItems('PROJECT_STAGE');
    const planItem = before.find((it) => it.code === 'PLAN');
    expect(planItem?.id).toBeDefined();
    const updated = await api.updateItem(planItem!.id!, { name: '规划-改' });
    expect(updated.name).toBe('规划-改');
  });
});
