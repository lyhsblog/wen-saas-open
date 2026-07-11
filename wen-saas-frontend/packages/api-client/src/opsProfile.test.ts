import { describe, expect, it } from 'vitest';
import { ApiError, createApiClient } from './http';
import { opsProfileApi } from './opsProfile';

const http = createApiClient({
  baseURL: '',
  getAuth: () => ({ token: 'mock-jwt-token-for-ops', tenantId: null }),
});
const api = opsProfileApi(http);

describe('opsProfileApi · MSW e2e', () => {
  it('按 tab 拆分的四个只读接口各自返回该段', async () => {
    const basic = await api.getBasic();
    expect(basic.username).toBe('admin@ops');
    expect(basic.realName).toBe('李梓萱');

    const teamsRoles = await api.getTeamsRoles();
    expect(teamsRoles.roles?.length).toBeGreaterThan(0);
    expect(teamsRoles.dataScope).toBe('全部数据');

    const security = await api.getSecurity();
    expect(security.mfaRequired).toBe(false);
    expect(security.mfaEnabled).toBe(true);

    const sso = await api.getSso();
    expect(sso.provider).toBeDefined();
  });

  it('updateProfile 写入 realName 后 getBasic 回显', async () => {
    await api.updateProfile({ realName: '李梓萱-改' });
    const basic = await api.getBasic();
    expect(basic.realName).toBe('李梓萱-改');
  });

  it('updateProfile realName 为空抛 422 fieldErrors', async () => {
    await expect(api.updateProfile({ realName: '' })).rejects.toMatchObject({
      status: 422,
    });
    try {
      await api.updateProfile({ realName: '' });
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError);
      expect((err as ApiError).fieldErrors.realName).toContain('姓名');
    }
  });

  it('changePassword step-up 票据无效 → 401 TICKET_INVALID', async () => {
    await expect(
      api.changePassword({ stepUpToken: 'wrong', newPassword: 'Strong#Pass1' }),
    ).rejects.toMatchObject({ status: 401 });
  });

  it('changePassword 弱密 → 422 fieldErrors.newPassword', async () => {
    try {
      await api.changePassword({ stepUpToken: 'mock-stepup-token', newPassword: '123' });
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError);
      expect((err as ApiError).status).toBe(422);
      expect((err as ApiError).fieldErrors.newPassword).toBeDefined();
    }
  });

  it('listSessions 包含 current=true 项', async () => {
    const list = await api.listSessions();
    expect(list.some((s) => s.current === true)).toBe(true);
  });

  it('revokeSession 当前会话 → 409', async () => {
    const list = await api.listSessions();
    const current = list.find((s) => s.current === true);
    expect(current?.sessionId).toBeDefined();
    await expect(api.revokeSession(current!.sessionId!)).rejects.toMatchObject({ status: 409 });
  });

  it('revokeSession 非当前会话 → 204 + 列表减一', async () => {
    const before = await api.listSessions();
    const other = before.find((s) => s.current !== true);
    expect(other?.sessionId).toBeDefined();
    await api.revokeSession(other!.sessionId!);
    const after = await api.listSessions();
    expect(after.length).toBe(before.length - 1);
  });

  it('revokeOtherSessions 后只剩 current', async () => {
    await api.revokeOtherSessions();
    const after = await api.listSessions();
    expect(after.every((s) => s.current === true)).toBe(true);
  });

  it('listLoginLogs 默认 size 20 + total>0', async () => {
    const page = await api.listLoginLogs();
    expect(page.records?.length).toBeLessThanOrEqual(20);
    expect((page.total ?? 0) > 0).toBe(true);
  });

  it('listLoginLogs page=2 翻页', async () => {
    const p1 = await api.listLoginLogs({ page: 1, pageSize: 5 });
    const p2 = await api.listLoginLogs({ page: 2, pageSize: 5 });
    expect(p1.records?.[0]?.id).not.toBe(p2.records?.[0]?.id);
  });

  it('getNotificationPreferences 暴露 6 类 × STATION/EMAIL/SMS', async () => {
    const view = await api.getNotificationPreferences();
    expect(view.categories?.length).toBe(6);
    const cat = view.categories?.[0];
    expect(cat?.channels?.map((c) => c.channel)).toEqual(['STATION', 'EMAIL', 'SMS']);
    expect(cat?.channels?.find((c) => c.channel === 'STATION')?.locked).toBe(true);
  });

  it('updateNotificationPreferences 写 EMAIL=false 后回读为 false', async () => {
    await api.updateNotificationPreferences({
      preferences: [{ category: 'TODO_TASK', channel: 'EMAIL', enabled: false }],
    });
    const view = await api.getNotificationPreferences();
    const cat = view.categories?.find((c) => c.category === 'TODO_TASK');
    expect(cat?.channels?.find((c) => c.channel === 'EMAIL')?.enabled).toBe(false);
  });

  it('updateNotificationPreferences STATION=false 被服务端忽略（强制 true）', async () => {
    await api.updateNotificationPreferences({
      preferences: [{ category: 'TODO_TASK', channel: 'STATION', enabled: false }],
    });
    const view = await api.getNotificationPreferences();
    const cat = view.categories?.find((c) => c.category === 'TODO_TASK');
    expect(cat?.channels?.find((c) => c.channel === 'STATION')?.enabled).toBe(true);
  });
});
