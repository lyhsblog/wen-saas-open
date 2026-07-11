import { describe, expect, it } from 'vitest';
import { createApiClient } from './http';
import { roleAdminApi } from './role';

const http = createApiClient({
  baseURL: '',
  getAuth: () => ({ token: 'mock-jwt-token-for-alice', tenantId: '9701' }),
});
const api = roleAdminApi(http);

describe('roleAdminApi', () => {
  it('列出角色返回 seed 数据', async () => {
    const roles = await api.listRoles();
    expect(roles.length).toBeGreaterThanOrEqual(4);
    expect(roles.some((r) => r.code === 'SYSTEM_OWNER')).toBe(true);
    expect(roles.some((r) => r.code === 'SALES_MANAGER')).toBe(true);
  });

  it('获取角色详情含 permissions 字段', async () => {
    const roles = await api.listRoles();
    const owner = roles.find((r) => r.code === 'SYSTEM_OWNER')!;
    const detail = await api.getRole(String(owner.id));
    expect(Array.isArray(detail.permissions)).toBe(true);
    expect((detail.permissions ?? []).length).toBeGreaterThan(0);
  });

  it('新建角色成功', async () => {
    const created = await api.createRole({
      code: 'TEST_ROLE',
      name: '测试角色',
      description: '单测专用',
      dataScope: 'SELF',
    });
    expect(created.code).toBe('TEST_ROLE');
    expect(created.systemPreset).toBe(false);
    const all = await api.listRoles();
    expect(all.some((r) => r.code === 'TEST_ROLE')).toBe(true);
  });

  it('重复编码返回 409 ROLE_CODE_DUPLICATE', async () => {
    await expect(
      api.createRole({ code: 'SYSTEM_OWNER', name: '另一个', description: null, dataScope: 'ALL' }),
    ).rejects.toMatchObject({ status: 409 });
  });

  it('删除系统预置角色返回 400', async () => {
    const roles = await api.listRoles();
    const admin = roles.find((r) => r.code === 'SYSTEM_ADMIN')!;
    await expect(api.deleteRole(String(admin.id))).rejects.toMatchObject({ status: 400 });
  });

  it('删除有成员的自定义角色返回 409 ROLE_HAS_MEMBERS', async () => {
    const created = await api.createRole({
      code: 'ROLE_WITH_MEMBERS',
      name: '有成员的角色',
      description: null,
      dataScope: 'SELF',
    });
    await api.addRoleMembers(String(created.id), { userIds: [100099] });
    await expect(api.deleteRole(String(created.id))).rejects.toMatchObject({ status: 409 });
  });

  it('启停角色更新 status', async () => {
    const roles = await api.listRoles();
    const sales = roles.find((r) => r.code === 'SALES_MANAGER')!;
    const updated = await api.setRoleStatus(String(sales.id), false);
    expect(updated.status).toBe(0);
    const re = await api.setRoleStatus(String(sales.id), true);
    expect(re.status).toBe(1);
  });
});
