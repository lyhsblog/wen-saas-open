import { useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { App, Alert, Breadcrumb, Button, Input, Modal, Progress, Segmented, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CloudUploadOutlined,
  DeleteOutlined,
  FileOutlined,
  FolderAddOutlined,
  FolderFilled,
  FolderOutlined,
  ReloadOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import {
  describeApiError,
  type FileCenterItem,
  type FileSpace,
  type FolderView,
  type RecycleItem,
} from '@wen-saas/api-client';
import { ReadonlyButton, useIsReadonly } from '@wen-saas/permissions';
import {
  CountChip,
  fileExt,
  formatBytes,
  formatDateTime,
  NavItem,
  NavRailDrawer,
  NavRailTrigger,
  RowActionsBar,
  useIsNarrow,
} from '@wen-saas/shared-ui';
import { fileCenterApi } from './fileCenterApi';
import {
  useCreateFolder,
  useDeleteFile,
  useDeleteFolder,
  useFileList,
  useMoveFile,
  useMoveFolder,
  usePurge,
  useQuota,
  useRecycle,
  useRenameFile,
  useRenameFolder,
  useRestore,
  fileKeys,
} from './fileCenterQueries';
import { UploadModal } from './UploadModal';
import { ShareModal } from './ShareModal';
import { FolderPickerModal } from './FolderPickerModal';

type NameModalState =
  | { mode: 'newFolder' }
  | { mode: 'renameFolder'; folder: FolderView }
  | { mode: 'renameFile'; file: FileCenterItem }
  | null;

export function FileCenterPage() {
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  // 套餐只读态：行内写操作统一置灰 + 悬浮解释（与 ReadonlyButton 同一套口径）；下载等读操作不受限
  const readonly = useIsReadonly();
  const readonlyGuard = readonly ? { disabled: true, tooltip: '当前套餐已到期，暂不可操作' } : {};
  // 窄屏（<1100px）时左侧空间导航收进 NavRailDrawer，右上按钮展开
  const narrow = useIsNarrow();
  const [railOpen, setRailOpen] = useState(false);
  const [view, setView] = useState<'files' | 'recycle'>('files');
  const [space, setSpace] = useState<FileSpace>('mine');
  const [folderId, setFolderId] = useState<string | null>(null);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);

  const listQuery = useFileList(space, folderId, keyword, page);
  const recycleQuery = useRecycle(space, view === 'recycle');
  const quotaQuery = useQuota();

  const createFolder = useCreateFolder();
  const renameFolder = useRenameFolder();
  const deleteFolder = useDeleteFolder();
  const moveFolder = useMoveFolder();
  const renameFile = useRenameFile();
  const deleteFile = useDeleteFile();
  const moveFile = useMoveFile();
  const restore = useRestore();
  const purge = usePurge();

  const [uploadOpen, setUploadOpen] = useState(false);
  const [shareFile, setShareFile] = useState<FileCenterItem | null>(null);
  const [nameModal, setNameModal] = useState<NameModalState>(null);
  const [nameInput, setNameInput] = useState('');
  const [moveTarget, setMoveTarget] = useState<
    { kind: 'file'; file: FileCenterItem } | { kind: 'folder'; folder: FolderView } | null
  >(null);

  const switchSpace = (s: FileSpace) => {
    setSpace(s);
    setFolderId(null);
    setKeyword('');
    setPage(1);
  };

  const enterFolder = (id: string | null) => {
    setFolderId(id);
    setKeyword('');
    setPage(1);
  };

  const onError = (e: unknown, fallback: string) => message.error(describeApiError(e, fallback));

  // ---------- 操作 ----------
  const submitName = async () => {
    const name = nameInput.trim();
    if (!name || !nameModal) return;
    try {
      if (nameModal.mode === 'newFolder') {
        // 后端重名自动加 (2)(3) 后缀（T5-2）：被改名时给轻提示，不静默。
        const created = await createFolder.mutateAsync({ space, parentId: folderId, name });
        if (created.name && created.name !== name) {
          message.info(`已存在同名文件夹，已重命名为「${created.name}」`);
        } else {
          message.success('文件夹已创建');
        }
      } else if (nameModal.mode === 'renameFolder') {
        await renameFolder.mutateAsync({ folderId: nameModal.folder.id, name });
        message.success('已重命名');
      } else {
        await renameFile.mutateAsync({ fileId: nameModal.file.fileId, name });
        message.success('已重命名');
      }
      setNameModal(null);
      setNameInput('');
    } catch (e) {
      onError(e, '操作失败');
    }
  };

  const doDownload = async (file: FileCenterItem) => {
    try {
      const url = await fileCenterApi.downloadUrl(file.fileId);
      window.open(url, '_blank', 'noopener');
    } catch (e) {
      onError(e, '获取下载链接失败');
    }
  };

  const confirmDeleteFile = (file: FileCenterItem) =>
    modal.confirm({
      title: `删除「${file.fileName}」？`,
      content: '文件将进入回收站，30 天后自动清理。',
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteFile.mutateAsync(file.fileId);
          message.success('已移入回收站');
        } catch (e) {
          onError(e, '删除失败');
        }
      },
    });

  const confirmDeleteFolder = (folder: FolderView) =>
    modal.confirm({
      title: `删除文件夹「${folder.name}」？`,
      content: '文件夹及其下全部内容将一并进入回收站，30 天后自动清理。',
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteFolder.mutateAsync(folder.id);
          message.success('已移入回收站');
        } catch (e) {
          onError(e, '删除失败');
        }
      },
    });

  const doMove = async (targetFolderId: string | null) => {
    if (!moveTarget) return;
    try {
      if (moveTarget.kind === 'file') {
        await moveFile.mutateAsync({
          fileId: moveTarget.file.fileId,
          targetSpace: space,
          targetFolderId,
        });
      } else {
        await moveFolder.mutateAsync({
          folderId: moveTarget.folder.id,
          newParentId: targetFolderId,
        });
      }
      message.success('已移动');
      setMoveTarget(null);
    } catch (e) {
      onError(e, '移动失败');
    }
  };

  const doRestore = async (r: RecycleItem) => {
    try {
      await restore.mutateAsync(r.type === 'FILE' ? { fileIds: [r.id] } : { folderIds: [r.id] });
      message.success('已还原');
    } catch (e) {
      onError(e, '还原失败');
    }
  };

  const doPurge = async (r: RecycleItem) => {
    try {
      await purge.mutateAsync(r.type === 'FILE' ? { fileIds: [r.id] } : { folderIds: [r.id] });
      message.success('已彻底删除');
    } catch (e) {
      onError(e, '删除失败');
    }
  };

  // ---------- 文件列表表格 ----------
  type Row =
    | { kind: 'folder'; key: string; folder: FolderView }
    | { kind: 'file'; key: string; file: FileCenterItem };

  const rows: Row[] = useMemo(() => {
    const data = listQuery.data;
    if (!data) return [];
    return [
      // 后端 list() 每页都返回全量 folders（total 只统计 files）——仅第 1 页展示，翻页不再重复
      ...(page === 1
        ? data.folders.map((f): Row => ({ kind: 'folder', key: `d-${f.id}`, folder: f }))
        : []),
      ...data.files.map((f): Row => ({ kind: 'file', key: `f-${f.fileId}`, file: f })),
    ];
  }, [listQuery.data, page]);

  const fileColumns: ColumnsType<Row> = [
    {
      title: '名称',
      key: 'name',
      render: (_, row) =>
        row.kind === 'folder' ? (
          <a onClick={() => enterFolder(row.folder.id)}>
            <FolderFilled style={{ color: 'var(--gold)', marginRight: 8 }} />
            {row.folder.name}
          </a>
        ) : (
          <span>
            <span style={{ marginRight: 8 }}>
              <CountChip>{fileExt(row.file.fileName)}</CountChip>
            </span>
            {row.file.fileName}
          </span>
        ),
    },
    {
      title: '大小',
      key: 'size',
      width: 110,
      render: (_, row) => (row.kind === 'file' ? formatBytes(row.file.sizeBytes) : '—'),
    },
    {
      title: '上传时间',
      key: 'time',
      width: 180,
      render: (_, row) => (row.kind === 'file' ? formatDateTime(row.file.uploadedAt) : '—'),
    },
    {
      title: '操作',
      key: 'op',
      width: 160,
      render: (_, row) =>
        row.kind === 'folder' ? (
          <RowActionsBar
            primary={[
              {
                key: 'rename',
                label: '重命名',
                onClick: () => {
                  setNameModal({ mode: 'renameFolder', folder: row.folder });
                  setNameInput(row.folder.name);
                },
                ...readonlyGuard,
              },
              {
                key: 'move',
                label: '移动',
                onClick: () => setMoveTarget({ kind: 'folder', folder: row.folder }),
                ...readonlyGuard,
              },
            ]}
            more={[
              {
                key: 'delete',
                label: '删除',
                danger: true,
                onClick: () => void confirmDeleteFolder(row.folder),
                ...readonlyGuard,
              },
            ]}
          />
        ) : (
          <RowActionsBar
            primary={[
              {
                key: 'download',
                label: '下载',
                onClick: () => {
                  void doDownload(row.file);
                },
              },
              {
                key: 'share',
                label: '分享',
                onClick: () => setShareFile(row.file),
                ...readonlyGuard,
              },
            ]}
            more={[
              {
                key: 'rename',
                label: '重命名',
                onClick: () => {
                  setNameModal({ mode: 'renameFile', file: row.file });
                  setNameInput(row.file.fileName);
                },
                ...readonlyGuard,
              },
              {
                key: 'move',
                label: '移动',
                onClick: () => setMoveTarget({ kind: 'file', file: row.file }),
                ...readonlyGuard,
              },
              {
                key: 'delete',
                label: '删除',
                danger: true,
                onClick: () => void confirmDeleteFile(row.file),
                ...readonlyGuard,
              },
            ]}
          />
        ),
    },
  ];

  // ---------- 回收站表格 ----------
  const recycleColumns: ColumnsType<RecycleItem> = [
    {
      title: '名称',
      key: 'name',
      render: (_, r) => (
        <span>
          {r.type === 'FOLDER' ? (
            <FolderFilled style={{ color: 'var(--gold)', marginRight: 8 }} />
          ) : (
            <FileOutlined style={{ marginRight: 8 }} />
          )}
          {r.name}
        </span>
      ),
    },
    {
      title: '大小',
      dataIndex: 'sizeBytes',
      width: 110,
      render: (v: number, r) => (r.type === 'FILE' ? formatBytes(v) : '—'),
    },
    {
      title: '删除时间',
      dataIndex: 'deletedAt',
      width: 180,
      render: (v?: string) => formatDateTime(v),
    },
    {
      title: '清理时间',
      dataIndex: 'purgeAt',
      width: 180,
      render: (v?: string) => formatDateTime(v),
    },
    {
      title: '操作',
      key: 'op',
      width: 120,
      render: (_, r) => (
        <RowActionsBar
          primary={[
            {
              key: 'restore',
              label: '还原',
              onClick: () => {
                void doRestore(r);
              },
              ...readonlyGuard,
            },
          ]}
          more={[
            {
              key: 'purge',
              label: '彻底删除',
              danger: true,
              onClick: () => {
                modal.confirm({
                  title: `彻底删除「${r.name}」？`,
                  content: '此操作不可恢复。',
                  okText: '彻底删除',
                  okButtonProps: { danger: true },
                  onOk: () => {
                    void doPurge(r);
                  },
                });
              },
              ...readonlyGuard,
            },
          ]}
        />
      ),
    },
  ];

  const quota = quotaQuery.data;
  const quotaPercent =
    quota && quota.capBytes
      ? Math.min(100, Math.round((quota.usedBytes / quota.capBytes) * 100))
      : 0;

  const breadcrumb = listQuery.data?.breadcrumb ?? [];

  // 左侧空间导航：宽屏 inline 定宽 248；窄屏放进 NavRailDrawer（fluid 铺满、选中后收起）
  const railNode = (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        padding: 16,
        ...(narrow
          ? { width: '100%', height: '100%' }
          : { width: 248, borderRight: '1px solid var(--border)' }),
      }}
    >
      <div style={{ fontWeight: 600, marginBottom: 12 }}>文件空间</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <NavItem
          active={view === 'files' && space === 'mine'}
          icon={<FolderOutlined />}
          onClick={() => {
            setView('files');
            switchSpace('mine');
            setRailOpen(false);
          }}
        >
          我的文件
        </NavItem>
        <NavItem
          active={view === 'files' && space === 'shared'}
          icon={<TeamOutlined />}
          onClick={() => {
            setView('files');
            switchSpace('shared');
            setRailOpen(false);
          }}
        >
          共享文件
        </NavItem>
        <NavItem
          active={view === 'recycle'}
          icon={<DeleteOutlined />}
          onClick={() => {
            setView('recycle');
            setRailOpen(false);
          }}
        >
          回收站
        </NavItem>
      </div>
      <div style={{ flex: 1 }} />
      <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
        <div style={{ marginBottom: 4 }}>
          存储用量 {formatBytes(quota?.usedBytes)} /{' '}
          {quota?.capBytes ? formatBytes(quota.capBytes) : '不限'}
        </div>
        <Progress percent={quotaPercent} size="small" showInfo={false} />
      </div>
    </div>
  );

  return (
    <div style={{ display: 'flex', height: '100%' }}>
      {narrow ? (
        <NavRailDrawer open={railOpen} onClose={() => setRailOpen(false)}>
          {railNode}
        </NavRailDrawer>
      ) : (
        railNode
      )}

      {/* 右侧内容 */}
      <div style={{ flex: 1, minWidth: 0, padding: 20, overflow: 'auto' }}>
        {narrow && (
          <div style={{ marginBottom: 12 }}>
            <NavRailTrigger label="文件空间" onClick={() => setRailOpen(true)} />
          </div>
        )}
        {view === 'files' ? (
          <>
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: 16, gap: 12 }}>
              <Breadcrumb
                items={[
                  {
                    title: (
                      <a onClick={() => enterFolder(null)}>
                        {space === 'mine' ? '我的文件' : '共享文件'}
                      </a>
                    ),
                  },
                  ...breadcrumb.map((b) => ({
                    title: <a onClick={() => enterFolder(b.id)}>{b.name}</a>,
                  })),
                ]}
              />
              <div style={{ flex: 1 }} />
              <Input.Search
                placeholder="搜索当前目录文件"
                allowClear
                style={{ width: 220 }}
                onSearch={(v) => {
                  setKeyword(v);
                  setPage(1);
                }}
              />
              <Button
                icon={<ReloadOutlined />}
                loading={listQuery.isFetching}
                onClick={() => {
                  void listQuery.refetch();
                  void quotaQuery.refetch();
                }}
              >
                刷新
              </Button>
              <ReadonlyButton
                icon={<FolderAddOutlined />}
                onClick={() => {
                  setNameModal({ mode: 'newFolder' });
                  setNameInput('');
                }}
              >
                新建文件夹
              </ReadonlyButton>
              <ReadonlyButton
                type="primary"
                icon={<CloudUploadOutlined />}
                onClick={() => setUploadOpen(true)}
              >
                上传
              </ReadonlyButton>
            </div>
            <Table<Row>
              rowKey="key"
              columns={fileColumns}
              dataSource={rows}
              loading={listQuery.isLoading}
              pagination={{
                current: page,
                total: listQuery.data?.total ?? 0,
                pageSize: 50,
                onChange: setPage,
                showTotal: (t) => `共 ${t} 个文件`,
              }}
            />
          </>
        ) : (
          <>
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: 16, gap: 12 }}>
              <Segmented
                value={space}
                onChange={(v) => switchSpace(v as FileSpace)}
                options={[
                  { label: '我的回收站', value: 'mine' },
                  { label: '共享回收站', value: 'shared' },
                ]}
              />
              <div style={{ flex: 1 }} />
              <Button
                icon={<ReloadOutlined />}
                loading={recycleQuery.isFetching}
                onClick={() => {
                  void recycleQuery.refetch();
                }}
              >
                刷新
              </Button>
            </div>
            <Alert
              type="warning"
              showIcon
              message="回收站文件保留 30 天，超期自动彻底清理。"
              style={{ marginBottom: 16 }}
            />
            <Table<RecycleItem>
              rowKey={(r) => `${r.type}-${r.id}`}
              columns={recycleColumns}
              dataSource={recycleQuery.data?.records ?? []}
              loading={recycleQuery.isLoading}
              pagination={false}
            />
          </>
        )}
      </div>

      {/* 弹窗 */}
      <UploadModal
        open={uploadOpen}
        space={space}
        folderId={folderId}
        onClose={() => setUploadOpen(false)}
        onUploaded={() => {
          // 上传成功后失效列表 + 配额，关闭弹窗后页面即显示新文件。
          void queryClient.invalidateQueries({ queryKey: fileKeys.all });
        }}
      />
      <ShareModal open={!!shareFile} file={shareFile} onClose={() => setShareFile(null)} />
      <FolderPickerModal
        open={!!moveTarget}
        space={space}
        {...(moveTarget?.kind === 'folder' ? { excludeFolderId: moveTarget.folder.id } : {})}
        onClose={() => setMoveTarget(null)}
        onPick={(id) => {
          void doMove(id);
        }}
      />
      <Modal
        title={nameModal?.mode === 'newFolder' ? '新建文件夹' : '重命名'}
        open={!!nameModal}
        onCancel={() => setNameModal(null)}
        onOk={() => {
          void submitName();
        }}
        okButtonProps={{ disabled: !nameInput.trim() }}
        confirmLoading={createFolder.isPending || renameFolder.isPending || renameFile.isPending}
      >
        <Input
          autoFocus
          value={nameInput}
          onChange={(e) => setNameInput(e.target.value)}
          onPressEnter={() => {
            void submitName();
          }}
          placeholder="请输入名称"
          maxLength={255}
        />
      </Modal>
    </div>
  );
}
