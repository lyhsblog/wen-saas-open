import { useCallback, useEffect, useMemo, useState } from 'react';
import { App, Spin } from 'antd';
import { NavRailDrawer, NavRailTrigger, useIsNarrow } from '@wen-saas/shared-ui';
import type { DeptCreateRequest, DeptUpdateRequest, DeptView } from '@wen-saas/domain';
import { describeApiError } from '@wen-saas/api-client';
import { DeptTreePane } from './DeptTreePane';
import { DeptDetailPane } from './DeptDetailPane';
import { DeptFormModal } from './DeptFormModal';
import { MemberDetailPane } from '../member/MemberDetailPane';
import { AllMembersPane } from '../member/AllMembersPane';
import {
  ALL_MEMBERS_KEY,
  useCreateDept,
  useDeleteDept,
  useDeptDetail,
  useDeptTree,
  useMoveDept,
  useReorderDepts,
  useSetDeptStatus,
  useUpdateDept,
} from './deptQueries';

/** 在树中查找节点 */
function findInTree(nodes: DeptView[], id: string): DeptView | null {
  for (const n of nodes) {
    if (String(n.id) === id) return n;
    if (n.children?.length) {
      const found = findInTree(n.children, id);
      if (found) return found;
    }
  }
  return null;
}

/** 查找节点的父节点名 */
function findParentName(nodes: DeptView[], parentId: number): string | null {
  for (const n of nodes) {
    if (n.id === parentId) return n.name ?? null;
    if (n.children?.length) {
      const found = findParentName(n.children, parentId);
      if (found) return found;
    }
  }
  return null;
}

export function DeptPage() {
  const { message, modal } = App.useApp();
  // 窄屏（<1100px）时左侧部门树收进 NavRailDrawer，顶部按钮展开
  const narrow = useIsNarrow();
  const [railOpen, setRailOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [formModalOpen, setFormModalOpen] = useState(false);
  const [editingDept, setEditingDept] = useState<DeptView | null>(null);
  const [defaultParentId, setDefaultParentId] = useState<string | null>(null);
  // 右侧 master-detail：选中成员后从部门详情切换为成员详情
  const [selectedMemberId, setSelectedMemberId] = useState<string | null>(null);

  const treeQuery = useDeptTree();
  const treeData = useMemo(() => treeQuery.data ?? [], [treeQuery.data]);

  // 派生选中：全部成员哨兵 > 用户选中的部门 > 首个根节点；树为空时落到「全部成员」视图
  const effectiveId = useMemo<string>(() => {
    if (selectedId === ALL_MEMBERS_KEY) return ALL_MEMBERS_KEY;
    if (selectedId && findInTree(treeData, selectedId)) return selectedId;
    return treeData.length > 0 ? String(treeData[0]?.id ?? ALL_MEMBERS_KEY) : ALL_MEMBERS_KEY;
  }, [treeData, selectedId]);
  const isAllMode = effectiveId === ALL_MEMBERS_KEY;
  const effectiveDeptId = isAllMode ? null : effectiveId;

  const detailQuery = useDeptDetail(effectiveDeptId);
  const detail = detailQuery.data ?? null;

  useEffect(() => {
    if (treeQuery.error) message.error(describeApiError(treeQuery.error, '加载部门树失败'));
  }, [treeQuery.error, message]);
  useEffect(() => {
    if (detailQuery.error) message.error(describeApiError(detailQuery.error, '加载部门详情失败'));
  }, [detailQuery.error, message]);

  // mutations
  const createDept = useCreateDept();
  const updateDept = useUpdateDept(effectiveDeptId ?? '');
  const deleteDept = useDeleteDept();
  const setDeptStatus = useSetDeptStatus(effectiveDeptId ?? '');
  const moveDeptMutation = useMoveDept();
  const reorderDeptsMutation = useReorderDepts();

  // ── 新建 / 编辑弹窗 ──────────────────────────────────
  const openCreateModal = useCallback((parentId: string | null) => {
    setEditingDept(null);
    setDefaultParentId(parentId);
    setFormModalOpen(true);
  }, []);

  const openEditModal = useCallback((dept: DeptView) => {
    setEditingDept(dept);
    setDefaultParentId(dept.parentId ? String(dept.parentId) : null);
    setFormModalOpen(true);
  }, []);

  // 编辑保存：名称/排序走 update；若上级变了即「移动」（移动=编辑上级字段或拖拽，二次确认）
  const handleFormSubmit = async (
    body: DeptCreateRequest | DeptUpdateRequest,
    parentId: string | null,
  ) => {
    if (editingDept) {
      const oldParent = editingDept.parentId ? String(editingDept.parentId) : '0';
      const newParent = parentId ?? '0';
      if (newParent !== oldParent) {
        const targetName =
          newParent === '0' ? '一级部门' : (findInTree(treeData, newParent)?.name ?? '目标部门');
        const ok = await new Promise<boolean>((resolve) => {
          modal.confirm({
            title: '确认调整部门层级？',
            content: `将「${editingDept.name ?? ''}」移动到「${targetName}」下，其子部门一并移动。`,
            okText: '确认移动',
            cancelText: '取消',
            onOk: () => resolve(true),
            onCancel: () => resolve(false),
          });
        });
        if (!ok) return; // 用户取消移动：保留弹窗，待重选上级
        // 先移动后更新：移动限制更多（层级/环检测），失败在前不留「名称已改、层级未动」的半提交
        try {
          await moveDeptMutation.mutateAsync({
            deptId: String(editingDept.id),
            body: { newParentId: newParent },
          });
        } catch (err) {
          message.error(describeApiError(err, '移动失败，部门信息未修改'));
          return; // 保留弹窗，可重选上级重试
        }
        await updateDept.mutateAsync(body);
        message.success('已更新并移动部门');
      } else {
        await updateDept.mutateAsync(body);
        message.success('已更新部门');
      }
    } else {
      const created = await createDept.mutateAsync(body as DeptCreateRequest);
      message.success('已创建部门');
      if (created.id != null) setSelectedId(String(created.id));
    }
    setFormModalOpen(false);
    setEditingDept(null);
  };

  // ── 删除 ──────────────────────────────────────────
  const handleDelete = async () => {
    if (!effectiveDeptId) return;
    try {
      await deleteDept.mutateAsync(effectiveDeptId);
      message.success('已删除部门');
      setSelectedId(null);
    } catch (err) {
      message.error(describeApiError(err, '删除失败'));
    }
  };

  // ── 启停 ──────────────────────────────────────────
  const handleToggleStatus = async (enabled: boolean) => {
    if (!effectiveDeptId) return;
    try {
      await setDeptStatus.mutateAsync(enabled);
      message.success(enabled ? '已启用' : '已停用');
    } catch (err) {
      message.error(describeApiError(err, '状态切换失败'));
    }
  };

  // ── 拖拽移动 ──────────────────────────────────────
  const handleDrop = useCallback(
    (draggedId: string, targetParentId: string) => {
      moveDeptMutation
        .mutateAsync({
          deptId: draggedId,
          body: { newParentId: targetParentId },
        })
        .then(() => {
          message.success('已移动部门');
        })
        .catch((err: unknown) => {
          message.error(describeApiError(err, '移动失败'));
        });
    },
    [moveDeptMutation, message],
  );

  // 同级拖拽排序：把该父级下重排后的 id 顺序提交给后端批量重排接口
  const handleReorder = useCallback(
    (parentId: string, orderedSiblingIds: string[]) => {
      reorderDeptsMutation
        .mutateAsync({ parentId, orderedIds: orderedSiblingIds })
        .then(() => message.success('已调整排序'))
        .catch((err: unknown) => message.error(describeApiError(err, '排序失败')));
    },
    [reorderDeptsMutation, message],
  );

  const parentName = detail?.parentId ? findParentName(treeData, detail.parentId) : null;

  const isRoot = detail?.parentId === 0 || detail?.parentId === undefined;

  const treePaneProps = {
    treeData,
    selectedId: effectiveId,
    onSelect: (id: string) => {
      setSelectedMemberId(null); // 切部门时退出成员详情
      setSelectedId(id);
      setRailOpen(false); // 窄屏抽屉里选中后收起（宽屏时本就是 false，无副作用）
    },
    onCreateDept: openCreateModal,
    onDrop: handleDrop,
    onReorder: handleReorder,
    onImported: () => void treeQuery.refetch(),
  };

  const rightContent = selectedMemberId ? (
    <MemberDetailPane
      memberId={selectedMemberId}
      treeData={treeData}
      onBack={() => setSelectedMemberId(null)}
    />
  ) : isAllMode ? (
    <AllMembersPane treeData={treeData} onSelectMember={setSelectedMemberId} />
  ) : detailQuery.isPending ? (
    <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Spin />
    </div>
  ) : detail ? (
    <DeptDetailPane
      dept={detail}
      treeData={treeData}
      isRoot={isRoot}
      parentName={parentName}
      onEdit={() => openEditModal(detail)}
      onCreateChild={() => openCreateModal(String(detail.id))}
      onDelete={() => {
        void handleDelete();
      }}
      onToggleStatus={(enabled) => {
        void handleToggleStatus(enabled);
      }}
      onSelectMember={setSelectedMemberId}
    />
  ) : null;

  return (
    <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>
      {narrow ? (
        <>
          <NavRailDrawer open={railOpen} onClose={() => setRailOpen(false)}>
            <Spin spinning={treeQuery.isPending} delay={120} wrapperClassName="rail-drawer-spin">
              <DeptTreePane {...treePaneProps} fluid />
            </Spin>
          </NavRailDrawer>
          <div
            style={{
              flex: 1,
              minWidth: 0,
              display: 'flex',
              flexDirection: 'column',
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                padding: '10px 16px',
                borderBottom: '1px solid var(--divider)',
                flexShrink: 0,
              }}
            >
              <NavRailTrigger label="组织架构" onClick={() => setRailOpen(true)} />
            </div>
            <div style={{ flex: 1, minHeight: 0, display: 'flex', overflow: 'hidden' }}>
              {rightContent}
            </div>
          </div>
        </>
      ) : (
        <>
          <Spin spinning={treeQuery.isPending} delay={120}>
            <DeptTreePane {...treePaneProps} />
          </Spin>
          {rightContent}
        </>
      )}
      <DeptFormModal
        open={formModalOpen}
        editing={editingDept}
        treeData={treeData}
        defaultParentId={defaultParentId}
        onCancel={() => {
          setFormModalOpen(false);
          setEditingDept(null);
        }}
        onSubmit={handleFormSubmit}
      />
    </div>
  );
}
