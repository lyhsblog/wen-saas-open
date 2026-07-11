import { http, HttpResponse } from 'msw';
import type {
  DictGroup,
  DictGroupCreateRequest,
  DictGroupUpdateRequest,
  DictItem,
  DictItemCreateRequest,
  DictItemUpdateRequest,
  ProblemDetail,
} from '@wen-saas/domain';
import { state } from '../data';

const base = '/api/v1/base/dict';

function notFound(detail: string) {
  const problem: ProblemDetail = { title: '资源不存在', status: 404, detail };
  return HttpResponse.json(problem, { status: 404 });
}

function conflict(detail: string) {
  const problem: ProblemDetail = { title: '冲突', status: 409, detail };
  return HttpResponse.json(problem, { status: 409 });
}

function findItem(list: DictItem[], id: number): { item: DictItem; parent: DictItem[] } | null {
  for (const it of list) {
    if (it.id === id) return { item: it, parent: list };
    if (it.children) {
      const sub = findItem(it.children, id);
      if (sub) return sub;
    }
  }
  return null;
}

function setStatusDeep(items: DictItem[], id: number, status: number): DictItem | null {
  for (const it of items) {
    if (it.id === id) {
      it.status = status;
      return it;
    }
    if (it.children) {
      const sub = setStatusDeep(it.children, id, status);
      if (sub) return sub;
    }
  }
  return null;
}

export const dictHandlers = [
  http.get(`${base}/groups`, () => HttpResponse.json(state.groups)),

  http.post(`${base}/groups`, async ({ request }) => {
    const body = (await request.json()) as DictGroupCreateRequest;
    if (state.groups.some((g) => g.code === body.code)) {
      return conflict(`分组编码 ${body.code} 已存在`);
    }
    const group: DictGroup = {
      code: body.code,
      name: body.name,
      supportHierarchy: body.supportHierarchy ?? false,
      source: 'CUSTOM',
    };
    if (body.description != null) group.description = body.description;
    state.groups.push(group);
    state.items[group.code!] = [];
    return HttpResponse.json(group, { status: 201 });
  }),

  http.put(`${base}/groups/:code`, async ({ params, request }) => {
    const code = decodeURIComponent(params.code as string);
    const group = state.groups.find((g) => g.code === code);
    if (!group) return notFound(`分组 ${code} 不存在`);
    const body = (await request.json()) as DictGroupUpdateRequest;
    if (body.name != null) group.name = body.name;
    if (body.description !== undefined) {
      if (body.description == null) delete group.description;
      else group.description = body.description;
    }
    return HttpResponse.json(group);
  }),

  http.delete(`${base}/groups/:code`, ({ params }) => {
    const code = decodeURIComponent(params.code as string);
    const idx = state.groups.findIndex((g) => g.code === code);
    if (idx < 0) return notFound(`分组 ${code} 不存在`);
    if (state.groups[idx]?.source === 'SYSTEM') {
      return conflict('系统预置分组不可删除');
    }
    state.groups.splice(idx, 1);
    delete state.items[code];
    return new HttpResponse(null, { status: 204 });
  }),

  http.put(`${base}/groups/:code/status`, ({ params }) => {
    // mock 后端没有 group.status 字段（schema 未暴露）；仅校验分组存在
    const code = decodeURIComponent(params.code as string);
    const group = state.groups.find((g) => g.code === code);
    if (!group) return notFound(`分组 ${code} 不存在`);
    return HttpResponse.json(group);
  }),

  http.get(`${base}/groups/:code/items`, ({ params, request }) => {
    const code = decodeURIComponent(params.code as string);
    const list = state.items[code];
    if (!list) return notFound(`分组 ${code} 不存在`);
    const url = new URL(request.url);
    const includeDisabled = url.searchParams.get('includeDisabled') !== 'false';
    if (includeDisabled) return HttpResponse.json(list);
    const filter = (items: DictItem[]): DictItem[] =>
      items
        .filter((it) => it.status === 1)
        .map(({ children, ...rest }) =>
          children ? { ...rest, children: filter(children) } : rest,
        );
    return HttpResponse.json(filter(list));
  }),

  http.post(`${base}/groups/:code/items`, async ({ params, request }) => {
    const code = decodeURIComponent(params.code as string);
    const list = state.items[code];
    if (!list) return notFound(`分组 ${code} 不存在`);
    const body = (await request.json()) as DictItemCreateRequest;
    const flat = (items: DictItem[]): DictItem[] =>
      items.flatMap((it) => [it, ...(it.children ? flat(it.children) : [])]);
    if (flat(list).some((it) => it.code === body.code)) {
      return conflict(`字典项编码 ${body.code} 已存在`);
    }
    const item: DictItem = {
      id: state.nextItemId++,
      code: body.code,
      name: body.name,
      sortOrder: body.sortOrder ?? 0,
      status: 1,
      ...(body.remark != null ? { remark: body.remark } : {}),
      source: 'CUSTOM',
    };
    if (body.parentId != null) {
      const parentId = Number(body.parentId);
      const found = findItem(list, parentId);
      if (!found) return notFound(`上级 ${body.parentId} 不存在`);
      found.item.children = [...(found.item.children ?? []), item];
    } else {
      list.push(item);
    }
    return HttpResponse.json(item, { status: 201 });
  }),

  http.put(`${base}/items/:id`, async ({ params, request }) => {
    const id = Number(params.id);
    for (const groupCode of Object.keys(state.items)) {
      const list = state.items[groupCode]!;
      const found = findItem(list, id);
      if (found) {
        const body = (await request.json()) as DictItemUpdateRequest;
        if (body.name != null) found.item.name = body.name;
        if (body.sortOrder != null) found.item.sortOrder = body.sortOrder;
        // 与后端 PATCH 语义一致：null 不改，""（非 null）落值即清空
        if (body.remark != null) found.item.remark = body.remark;
        return HttpResponse.json(found.item);
      }
    }
    return notFound(`字典项 ${id} 不存在`);
  }),

  http.delete(`${base}/items/:id`, ({ params }) => {
    const id = Number(params.id);
    for (const groupCode of Object.keys(state.items)) {
      const list = state.items[groupCode]!;
      const found = findItem(list, id);
      if (found) {
        if (found.item.source === 'SYSTEM') {
          return conflict('系统预置字典项不可删除');
        }
        const idx = found.parent.indexOf(found.item);
        found.parent.splice(idx, 1);
        return new HttpResponse(null, { status: 204 });
      }
    }
    return notFound(`字典项 ${id} 不存在`);
  }),

  http.put(`${base}/items/:id/status`, ({ params, request }) => {
    const id = Number(params.id);
    const url = new URL(request.url);
    const enabled = url.searchParams.get('enabled') === 'true';
    for (const groupCode of Object.keys(state.items)) {
      const list = state.items[groupCode]!;
      const updated = setStatusDeep(list, id, enabled ? 1 : 0);
      if (updated) return HttpResponse.json(updated);
    }
    return notFound(`字典项 ${id} 不存在`);
  }),
];
