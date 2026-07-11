import { http, HttpResponse } from 'msw';
import type {
  RoleDetail,
  RoleCreateRequest,
  RoleUpdateRequest,
  RolePermissionsUpdateRequest,
  MemberIdsRequest,
  MemberRef,
  ProblemDetail,
} from '@wen-saas/domain';
import { state } from '../data';

const base = '/api/v1/base/roles';

function notFound(detail: string) {
  const problem: ProblemDetail = {
    title: '资源不存在',
    status: 404,
    detail,
    code: 'ROLE_NOT_FOUND',
  };
  return HttpResponse.json(problem, { status: 404 });
}

function conflict(detail: string, code: string) {
  const problem: ProblemDetail = { title: '冲突', status: 409, detail, code };
  return HttpResponse.json(problem, { status: 409 });
}

function badRequest(detail: string, code: string) {
  const problem: ProblemDetail = { title: '请求错误', status: 400, detail, code };
  return HttpResponse.json(problem, { status: 400 });
}

function roleToView(r: RoleDetail) {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { permissions, updatedAt, ...view } = r;
  return view;
}

export const roleHandlers = [
  http.get(base, () => HttpResponse.json(state.roles.map(roleToView))),

  http.post(base, async ({ request }) => {
    const body = (await request.json()) as RoleCreateRequest;
    if (state.roles.some((r) => r.code === body.code)) {
      return conflict(`角色编码 ${body.code} 已存在`, 'ROLE_CODE_DUPLICATE');
    }
    if (state.roles.some((r) => r.name === body.name)) {
      return conflict(`角色名称 ${body.name} 已存在`, 'ROLE_NAME_DUPLICATE');
    }
    const now = new Date().toISOString();
    const role: RoleDetail = {
      id: state.nextRoleId++,
      code: body.code,
      name: body.name,
      description: body.description ?? '',
      dataScope: body.dataScope,
      status: 1,
      memberCount: 0,
      systemPreset: false,
      permissions: [],
      createdAt: now,
      updatedAt: now,
    };
    state.roles.push(role);
    return HttpResponse.json(role, { status: 201 });
  }),

  http.get(`${base}/:roleId`, ({ params }) => {
    const roleId = Number(params.roleId);
    const role = state.roles.find((r) => r.id === roleId);
    if (!role) return notFound(`角色 ${String(params.roleId)} 不存在`);
    return HttpResponse.json(role);
  }),

  http.put(`${base}/:roleId`, async ({ params, request }) => {
    const roleId = Number(params.roleId);
    const role = state.roles.find((r) => r.id === roleId);
    if (!role) return notFound(`角色 ${String(params.roleId)} 不存在`);
    if (role.code === 'SYSTEM_OWNER') {
      return badRequest('主管理员角色不可修改', 'ROLE_SYSTEM_OWNER_READONLY');
    }
    const body = (await request.json()) as RoleUpdateRequest;
    if (body.name != null) role.name = body.name;
    if (body.description !== undefined) role.description = body.description ?? '';
    if (body.dataScope != null) role.dataScope = body.dataScope;
    role.updatedAt = new Date().toISOString();
    return HttpResponse.json(role);
  }),

  http.delete(`${base}/:roleId`, ({ params }) => {
    const roleId = Number(params.roleId);
    const idx = state.roles.findIndex((r) => r.id === roleId);
    if (idx < 0) return notFound(`角色 ${String(params.roleId)} 不存在`);
    const role = state.roles[idx]!;
    if (role.code === 'SYSTEM_OWNER') {
      return badRequest('主管理员角色不可修改', 'ROLE_SYSTEM_OWNER_READONLY');
    }
    if (role.systemPreset) {
      return badRequest('系统预置角色不可删除', 'ROLE_SYSTEM_READONLY_FIELDS');
    }
    const members = state.roleMembers[String(roleId)] ?? [];
    if (members.length > 0) {
      return conflict('请先移除角色下的所有成员', 'ROLE_HAS_MEMBERS');
    }
    state.roles.splice(idx, 1);
    delete state.roleMembers[String(roleId)];
    return new HttpResponse(null, { status: 204 });
  }),

  http.put(`${base}/:roleId/permissions`, async ({ params, request }) => {
    const roleId = Number(params.roleId);
    const role = state.roles.find((r) => r.id === roleId);
    if (!role) return notFound(`角色 ${String(params.roleId)} 不存在`);
    if (role.code === 'SYSTEM_OWNER') {
      return badRequest('主管理员角色不可修改', 'ROLE_SYSTEM_OWNER_READONLY');
    }
    const body = (await request.json()) as RolePermissionsUpdateRequest;
    role.permissions = body.codes ?? [];
    role.updatedAt = new Date().toISOString();
    return HttpResponse.json(role);
  }),

  http.put(`${base}/:roleId/status`, ({ params, request }) => {
    const roleId = Number(params.roleId);
    const role = state.roles.find((r) => r.id === roleId);
    if (!role) return notFound(`角色 ${String(params.roleId)} 不存在`);
    if (role.code === 'SYSTEM_OWNER') {
      return badRequest('主管理员角色不可修改', 'ROLE_SYSTEM_OWNER_READONLY');
    }
    const url = new URL(request.url);
    const enabled = url.searchParams.get('enabled') === 'true';
    role.status = enabled ? 1 : 0;
    role.updatedAt = new Date().toISOString();
    return HttpResponse.json(role);
  }),

  http.get(`${base}/:roleId/members`, ({ params }) => {
    const roleId = Number(params.roleId);
    const role = state.roles.find((r) => r.id === roleId);
    if (!role) return notFound(`角色 ${String(params.roleId)} 不存在`);
    const userIds = state.roleMembers[String(roleId)] ?? [];
    const members: MemberRef[] = userIds.map((userId) => ({ userId }));
    return HttpResponse.json(members);
  }),

  http.post(`${base}/:roleId/members`, async ({ params, request }) => {
    const roleId = Number(params.roleId);
    const role = state.roles.find((r) => r.id === roleId);
    if (!role) return notFound(`角色 ${String(params.roleId)} 不存在`);
    const body = (await request.json()) as MemberIdsRequest;
    const key = String(roleId);
    const existing = new Set(state.roleMembers[key] ?? []);
    for (const uid of body.userIds ?? []) {
      existing.add(uid);
    }
    state.roleMembers[key] = Array.from(existing);
    role.memberCount = state.roleMembers[key].length;
    return HttpResponse.json(role);
  }),

  http.delete(`${base}/:roleId/members`, async ({ params, request }) => {
    const roleId = Number(params.roleId);
    const role = state.roles.find((r) => r.id === roleId);
    if (!role) return notFound(`角色 ${String(params.roleId)} 不存在`);
    const body = (await request.json()) as MemberIdsRequest;
    const key = String(roleId);
    const toRemove = new Set(body.userIds ?? []);
    state.roleMembers[key] = (state.roleMembers[key] ?? []).filter((uid) => !toRemove.has(uid));
    role.memberCount = state.roleMembers[key].length;
    return new HttpResponse(null, { status: 204 });
  }),
];
