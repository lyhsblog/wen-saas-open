import { useEffect, useMemo, useState } from 'react';
import { App, Empty, Spin } from 'antd';
import { NavRailDrawer, NavRailTrigger, useIsNarrow } from '@wen-saas/shared-ui';
import type {
  DictGroup,
  DictGroupCreateRequest,
  DictItem,
  DictItemCreateRequest,
  DictItemUpdateRequest,
} from '@wen-saas/domain';
import { describeApiError } from '@wen-saas/api-client';
import { DictGroupList } from './DictGroupList';
import { DictGroupHeader } from './DictGroupHeader';
import { DictItemList } from './DictItemList';
import { DictGroupFormModal } from './DictGroupFormModal';
import { DictItemFormModal } from './DictItemFormModal';
import {
  useCreateDictGroup,
  useCreateDictItem,
  useDeleteDictGroup,
  useDeleteDictItem,
  useDictGroups,
  useDictItems,
  useSetDictItemStatus,
  useUpdateDictGroup,
  useUpdateDictItem,
} from './dictQueries';

export function DictPage() {
  const { message, modal } = App.useApp();
  // 窄屏（<1100px）时左侧分组栏收进 NavRailDrawer，顶部按钮展开
  const narrow = useIsNarrow();
  const [railOpen, setRailOpen] = useState(false);
  const [requestedCode, setRequestedCode] = useState<string | null>(null);

  const [groupModalOpen, setGroupModalOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<DictGroup | null>(null);
  const [itemModalOpen, setItemModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<DictItem | null>(null);

  const groupsQuery = useDictGroups();
  const groups = useMemo(() => groupsQuery.data ?? [], [groupsQuery.data]);
  // 派生选中：若用户未选 / 选中已不在列表，则回退到第一个；避免在 effect 里反向同步状态
  const selectedCode = useMemo(() => {
    if (groups.length === 0) return null;
    if (requestedCode && groups.some((g) => g.code === requestedCode)) return requestedCode;
    return groups[0]?.code ?? null;
  }, [groups, requestedCode]);
  const selectedGroup = groups.find((g) => g.code === selectedCode) ?? null;

  const itemsQuery = useDictItems(selectedCode);
  const items = itemsQuery.data ?? [];

  useEffect(() => {
    if (groupsQuery.error) message.error(describeApiError(groupsQuery.error, '加载字典分组失败'));
  }, [groupsQuery.error, message]);
  useEffect(() => {
    if (itemsQuery.error) message.error(describeApiError(itemsQuery.error, '加载字典项失败'));
  }, [itemsQuery.error, message]);

  const createGroup = useCreateDictGroup();
  const updateGroup = useUpdateDictGroup();
  const deleteGroup = useDeleteDictGroup();
  const createItem = useCreateDictItem(selectedCode ?? '');
  const updateItem = useUpdateDictItem(selectedCode ?? '');
  const deleteItem = useDeleteDictItem(selectedCode ?? '');
  const setItemStatus = useSetDictItemStatus(selectedCode ?? '');

  // 错误向上抛给 modal 处理：modal 内部用 applyApiErrorToForm 把 422 映射到字段，
  // 非 422 的错误 modal 自行 toast。这里只负责"成功路径"。
  const handleSubmitGroup = async (body: DictGroupCreateRequest) => {
    if (editingGroup?.code) {
      await updateGroup.mutateAsync({
        code: editingGroup.code,
        body: { name: body.name, description: body.description ?? null },
      });
      message.success('已更新分组');
    } else {
      await createGroup.mutateAsync(body);
      message.success('已创建分组');
      setRequestedCode(body.code);
    }
    setGroupModalOpen(false);
    setEditingGroup(null);
  };

  const handleDeleteGroup = () => {
    if (!selectedGroup?.code) return;
    const code = selectedGroup.code;
    modal.confirm({
      title: `确认删除分组「${selectedGroup.name}」？`,
      content: '删除后该分组下的字典项不可恢复',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteGroup.mutateAsync(code);
          message.success('已删除分组');
          setRequestedCode(null);
        } catch (err) {
          message.error(describeApiError(err, '删除失败'));
        }
      },
    });
  };

  // 错误向上抛给 modal 处理（422 → 字段内联，其它 → modal 自己 toast）。
  const handleSubmitItem = async (body: DictItemCreateRequest) => {
    if (!selectedGroup?.code) return;
    if (editingItem?.id != null) {
      // PATCH 语义：只带确有改动的字段，未改字段不发（对「不变」发 null 会依赖后端 null-guard）。
      // remark 发字符串而非 null——""（非 null）才能让后端 setRemark("") 清空生效。
      const patch: DictItemUpdateRequest = {
        ...(body.name !== editingItem.name ? { name: body.name } : {}),
        ...((body.sortOrder ?? 0) !== (editingItem.sortOrder ?? 0)
          ? { sortOrder: body.sortOrder }
          : {}),
        ...((body.remark ?? '') !== (editingItem.remark ?? '')
          ? { remark: body.remark ?? '' }
          : {}),
      };
      await updateItem.mutateAsync({ id: editingItem.id, body: patch });
      message.success('已更新字典项');
    } else {
      await createItem.mutateAsync(body);
      message.success('已新增字典项');
    }
    setItemModalOpen(false);
    setEditingItem(null);
  };

  const handleToggleItemStatus = async (it: DictItem) => {
    if (!selectedGroup?.code || it.id == null) return;
    const nextEnabled = it.status !== 1;
    try {
      await setItemStatus.mutateAsync({ id: it.id, enabled: nextEnabled });
      message.success(nextEnabled ? '已启用' : '已停用');
    } catch (err) {
      message.error(describeApiError(err, '状态切换失败'));
    }
  };

  const handleDeleteItem = async (it: DictItem) => {
    if (!selectedGroup?.code || it.id == null) return;
    try {
      await deleteItem.mutateAsync(it.id);
      message.success('已删除');
    } catch (err) {
      message.error(describeApiError(err, '删除失败'));
    }
  };

  const groupListProps = {
    groups,
    selectedCode,
    onSelect: (code: string) => {
      setRequestedCode(code);
      setRailOpen(false); // 窄屏抽屉里选中后收起（宽屏时本就是 false，无副作用）
    },
    onCreateGroup: () => {
      setEditingGroup(null);
      setGroupModalOpen(true);
    },
  };

  return (
    <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>
      {narrow ? (
        <NavRailDrawer open={railOpen} onClose={() => setRailOpen(false)}>
          <Spin spinning={groupsQuery.isPending} delay={120} wrapperClassName="rail-drawer-spin">
            <DictGroupList {...groupListProps} fluid />
          </Spin>
        </NavRailDrawer>
      ) : (
        <Spin spinning={groupsQuery.isPending} delay={120}>
          <DictGroupList {...groupListProps} />
        </Spin>
      )}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {narrow && (
          <div
            style={{
              padding: '10px 16px',
              borderBottom: '1px solid var(--divider)',
              flexShrink: 0,
            }}
          >
            <NavRailTrigger label="字典分组" onClick={() => setRailOpen(true)} />
          </div>
        )}
        {selectedGroup ? (
          <>
            <DictGroupHeader
              group={selectedGroup}
              onEdit={() => {
                setEditingGroup(selectedGroup);
                setGroupModalOpen(true);
              }}
              onDelete={handleDeleteGroup}
            />
            <Spin
              spinning={itemsQuery.isPending && !!selectedCode}
              delay={120}
              style={{ flex: 1 }}
              wrapperClassName="dict-spin"
            >
              <DictItemList
                group={selectedGroup}
                items={items}
                onCreateItem={() => {
                  setEditingItem(null);
                  setItemModalOpen(true);
                }}
                onEditItem={(it) => {
                  setEditingItem(it);
                  setItemModalOpen(true);
                }}
                onToggleStatus={(it) => {
                  void handleToggleItemStatus(it);
                }}
                onDeleteItem={(it) => {
                  void handleDeleteItem(it);
                }}
                onImported={() => void itemsQuery.refetch()}
              />
            </Spin>
          </>
        ) : (
          <Empty description="请在左侧选择一个分组" style={{ marginTop: 120 }} />
        )}
      </div>
      <DictGroupFormModal
        open={groupModalOpen}
        editing={editingGroup}
        onCancel={() => {
          setGroupModalOpen(false);
          setEditingGroup(null);
        }}
        onSubmit={handleSubmitGroup}
      />
      {selectedGroup && (
        <DictItemFormModal
          open={itemModalOpen}
          group={selectedGroup}
          editing={editingItem}
          itemsTree={items}
          onCancel={() => {
            setItemModalOpen(false);
            setEditingItem(null);
          }}
          onSubmit={handleSubmitItem}
        />
      )}
    </div>
  );
}
