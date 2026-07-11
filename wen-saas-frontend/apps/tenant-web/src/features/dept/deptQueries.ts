import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { DeptCreateRequest, DeptUpdateRequest, DeptMoveRequest } from '@wen-saas/domain';
import { deptApi } from './deptApi';

/**
 * 「全部成员」虚拟入口的选中哨兵值：不注入部门树数据（避免污染 DnD/TreeSelect 等消费方），
 * 只走 DeptPage 的 selectedId 通道；与真实部门 id（数字串）天然互斥。
 */
export const ALL_MEMBERS_KEY = '__all__';

export const deptKeys = {
  all: ['dept'] as const,
  tree: () => [...deptKeys.all, 'tree'] as const,
  detail: (deptId: string) => [...deptKeys.all, 'detail', deptId] as const,
};

export function useDeptTree() {
  return useQuery({
    queryKey: deptKeys.tree(),
    queryFn: () => deptApi.listTree(),
  });
}

export function useDeptDetail(deptId: string | null) {
  return useQuery({
    queryKey: deptId ? deptKeys.detail(deptId) : deptKeys.detail('__none__'),
    queryFn: () => deptApi.getDept(deptId!),
    enabled: !!deptId,
  });
}

export function useCreateDept() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: DeptCreateRequest) => deptApi.createDept(body),
    // 用 void 不阻塞 mutateAsync 兑现：弹窗成功即关、按钮不再持续 loading，列表后台重拉（T2-03）
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: deptKeys.tree() });
    },
  });
}

export function useUpdateDept(deptId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: DeptUpdateRequest) => deptApi.updateDept(deptId, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: deptKeys.tree() });
      void qc.invalidateQueries({ queryKey: deptKeys.detail(deptId) });
    },
  });
}

export function useDeleteDept() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (deptId: string) => deptApi.deleteDept(deptId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: deptKeys.tree() });
    },
  });
}

export function useMoveDept() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ deptId, body }: { deptId: string; body: DeptMoveRequest }) =>
      deptApi.moveDept(deptId, body),
    // 成功失败都重拉：失败时让 DeptTreePane 的本地受控顺序随新 treeData 引用清空、回弹到服务端真相
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: deptKeys.tree() });
    },
  });
}

/** 同级拖拽排序：调后端批量重排接口，单事务整体重写 sort_order（一个请求、原子）。 */
export function useReorderDepts() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ parentId, orderedIds }: { parentId: string; orderedIds: string[] }) =>
      deptApi.reorderDepts(parentId, orderedIds),
    // 成功失败都重拉：失败时让本地受控顺序随新 treeData 引用清空、回弹到服务端真相
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: deptKeys.tree() });
    },
  });
}

export function useSetDeptStatus(deptId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (enabled: boolean) => deptApi.setDeptStatus(deptId, enabled),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: deptKeys.tree() });
      void qc.invalidateQueries({ queryKey: deptKeys.detail(deptId) });
    },
  });
}
