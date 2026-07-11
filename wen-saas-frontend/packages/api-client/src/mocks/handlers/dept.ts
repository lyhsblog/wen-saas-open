import { http, HttpResponse } from 'msw';
import type {
  DeptView,
  DeptCreateRequest,
  DeptUpdateRequest,
  DeptMoveRequest,
  ProblemDetail,
} from '@wen-saas/domain';
import { state } from '../data';

const base = '/api/v1/base/depts';

/** 公司根节点 ID（种子数据固定为 1） */
const ROOT_DEPT_ID = 1;

function notFound(detail: string) {
  const problem: ProblemDetail = {
    title: '资源不存在',
    status: 404,
    detail,
    code: 'DEPT_NOT_FOUND',
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

/** 从扁平列表构建嵌套树 */
function buildTree(flatDepts: DeptView[]): DeptView[] {
  const map = new Map<number, DeptView>();
  const roots: DeptView[] = [];

  for (const d of flatDepts) {
    map.set(d.id ?? -1, { ...d, children: [] });
  }

  for (const d of flatDepts) {
    const node = map.get(d.id ?? -1)!;
    const pid = d.parentId ?? 0;
    if (pid === 0) {
      roots.push(node);
    } else {
      const parent = map.get(pid);
      if (parent) {
        parent.children = [...(parent.children ?? []), node];
      } else {
        roots.push(node);
      }
    }
  }

  return roots;
}

function findDept(deptId: number): DeptView | undefined {
  return state.depts.find((d) => d.id === deptId);
}

function hasChildren(deptId: number): boolean {
  return state.depts.some((d) => d.parentId === deptId);
}

function isDescendant(ancestorId: number, targetId: number): boolean {
  const ancestor = findDept(ancestorId);
  if (!ancestor) return false;
  const target = findDept(targetId);
  if (!target) return false;
  return (target.ancestors ?? '').includes(`/${ancestorId}/`);
}

function computeDepth(deptId: number): number {
  const d = findDept(deptId);
  if (!d) return 0;
  return (d.ancestors ?? '/').split('/').filter(Boolean).length;
}

function updateAncestors(deptId: number, newAncestors: string): void {
  const dept = findDept(deptId);
  if (!dept) return;
  const oldAncestors = dept.ancestors ?? '';
  dept.ancestors = newAncestors;
  // 更新所有后代
  for (const child of state.depts) {
    if ((child.ancestors ?? '').startsWith(oldAncestors) && child.id !== deptId) {
      child.ancestors = child.ancestors!.replace(oldAncestors, newAncestors);
    }
  }
}

export const deptHandlers = [
  // 列出完整树
  http.get(base, () => {
    return HttpResponse.json(buildTree(state.depts));
  }),

  // 新建部门
  http.post(base, async ({ request }) => {
    const body = (await request.json()) as DeptCreateRequest;
    const parentId = body.parentId != null ? Number(body.parentId) : 0;

    // 校验上级存在
    if (parentId !== 0) {
      const parent = findDept(parentId);
      if (!parent) {
        return badRequest(`上级部门 ${String(body.parentId)} 不存在`, 'DEPT_PARENT_INVALID');
      }
      // 深度检查（最多 5 层）
      if (computeDepth(parentId) >= 5) {
        return badRequest('部门层级不能超过 5 层', 'DEPT_DEPTH_EXCEEDED');
      }
    }

    // 同级重名检查
    const duplicate = state.depts.find((d) => d.parentId === parentId && d.name === body.name);
    if (duplicate) {
      return conflict(`同级下已存在部门「${body.name}」`, 'DEPT_NAME_DUPLICATE');
    }

    const id = state.nextDeptId++;
    const parentAncestors = parentId === 0 ? '/' : (findDept(parentId)?.ancestors ?? '/');
    const ancestors = `${parentAncestors}${id}/`;

    const dept: DeptView = {
      id,
      parentId,
      name: body.name,
      ancestors,
      sortOrder: body.sortOrder ?? 0,
      status: 1,
      memberCount: 0,
      children: [],
    };
    state.depts.push(dept);
    return HttpResponse.json(dept, { status: 201 });
  }),

  // 取部门详情
  http.get(`${base}/:deptId`, ({ params }) => {
    const deptId = Number(params.deptId);
    const dept = findDept(deptId);
    if (!dept) return notFound(`部门 ${String(params.deptId)} 不存在`);
    return HttpResponse.json(dept);
  }),

  // 编辑部门
  http.put(`${base}/:deptId`, async ({ params, request }) => {
    const deptId = Number(params.deptId);
    const dept = findDept(deptId);
    if (!dept) return notFound(`部门 ${String(params.deptId)} 不存在`);
    if (deptId === ROOT_DEPT_ID) {
      return badRequest(
        '公司根节点不可改名，请通过「系统管理 → 公司信息」修改',
        'ROOT_DEPT_PROTECTED',
      );
    }

    const body = (await request.json()) as DeptUpdateRequest;

    // 同级重名检查
    if (body.name != null && body.name !== dept.name) {
      const duplicate = state.depts.find(
        (d) => d.parentId === dept.parentId && d.name === body.name && d.id !== deptId,
      );
      if (duplicate) {
        return conflict(`同级下已存在部门「${body.name}」`, 'DEPT_NAME_DUPLICATE');
      }
      dept.name = body.name;
    }
    if (body.sortOrder !== undefined) {
      dept.sortOrder = body.sortOrder ?? dept.sortOrder ?? 0;
    }
    return HttpResponse.json(dept);
  }),

  // 删除部门
  http.delete(`${base}/:deptId`, ({ params }) => {
    const deptId = Number(params.deptId);
    const idx = state.depts.findIndex((d) => d.id === deptId);
    if (idx < 0) return notFound(`部门 ${String(params.deptId)} 不存在`);
    if (deptId === ROOT_DEPT_ID) {
      return badRequest('公司根节点不可删除', 'ROOT_DEPT_PROTECTED');
    }
    if (hasChildren(deptId)) {
      return conflict('请先删除所有子部门', 'DEPT_HAS_CHILDREN');
    }
    const dept = state.depts[idx]!;
    if ((dept.memberCount ?? 0) > 0) {
      return conflict('请先将成员从此部门移除', 'DEPT_HAS_MEMBERS');
    }
    state.depts.splice(idx, 1);
    return new HttpResponse(null, { status: 204 });
  }),

  // 移动部门
  http.put(`${base}/:deptId/parent`, async ({ params, request }) => {
    const deptId = Number(params.deptId);
    const dept = findDept(deptId);
    if (!dept) return notFound(`部门 ${String(params.deptId)} 不存在`);
    if (deptId === ROOT_DEPT_ID) {
      return badRequest('公司根节点不可移动', 'ROOT_DEPT_PROTECTED');
    }

    const body = (await request.json()) as DeptMoveRequest;
    const newParentId = Number(body.newParentId);

    // 不能移到自己或后代
    if (newParentId === deptId) {
      return badRequest('不能将部门移到自身下', 'DEPT_PARENT_IS_DESCENDANT');
    }
    if (isDescendant(deptId, newParentId)) {
      return badRequest('不能将部门移到其下级部门中', 'DEPT_PARENT_IS_DESCENDANT');
    }

    // 校验新上级存在
    if (newParentId !== 0) {
      const newParent = findDept(newParentId);
      if (!newParent) {
        return badRequest(`目标上级部门不存在`, 'DEPT_PARENT_INVALID');
      }
      // 深度检查
      const newParentDepth = computeDepth(newParentId);
      const deptSubtreeDepth = computeDepth(deptId) - 1; // 从 dept 开始算
      if (newParentDepth + deptSubtreeDepth + 1 > 5) {
        return badRequest('移动后部门层级不能超过 5 层', 'DEPT_DEPTH_EXCEEDED');
      }
    }

    // 同级重名检查
    const duplicate = state.depts.find(
      (d) => d.parentId === newParentId && d.name === dept.name && d.id !== deptId,
    );
    if (duplicate) {
      return conflict(`目标上级下已存在同名部门「${dept.name}」`, 'DEPT_NAME_DUPLICATE');
    }

    const oldParentId = dept.parentId ?? 0;
    dept.parentId = newParentId;
    const newParentAncestors = newParentId === 0 ? '/' : (findDept(newParentId)?.ancestors ?? '/');
    updateAncestors(deptId, `${newParentAncestors}${deptId}/`);

    // 不影响旧/新上级的 memberCount（后端处理）
    void oldParentId;

    return HttpResponse.json(dept);
  }),

  // 启停部门
  http.put(`${base}/:deptId/status`, ({ params, request }) => {
    const deptId = Number(params.deptId);
    const dept = findDept(deptId);
    if (!dept) return notFound(`部门 ${String(params.deptId)} 不存在`);

    const url = new URL(request.url);
    const enabled = url.searchParams.get('enabled') === 'true';
    dept.status = enabled ? 1 : 0;
    return HttpResponse.json(dept);
  }),
];
