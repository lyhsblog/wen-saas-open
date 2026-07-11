import { useRef, useState } from 'react';
import {
  Button,
  DotLoading,
  Empty,
  ErrorBlock,
  InfiniteScroll,
  NavBar,
  ProgressBar,
  Tabs,
  Toast,
} from 'antd-mobile';
import { useNavigate } from 'react-router';
import type { FileSpace, FolderView } from '@wen-saas/api-client';
import { useQueryClient } from '@tanstack/react-query';
import { fileApi } from '../../lib/api';
import { Card } from '../../components/ui';
import { formatBytes, formatRelative } from '../../lib/format';
import { useFileListInfinite, useQuota } from './fileQueries';

export function FilesPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [space, setSpace] = useState<FileSpace>('mine');
  const [folderId, setFolderId] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const listQ = useFileListInfinite(space, folderId);
  const quotaQ = useQuota();
  // 后端语义：folders/breadcrumb 每页全量、files 分页——目录取第一页，文件跨页拼接
  const firstPage = listQ.data?.pages[0];
  const folders = firstPage?.folders ?? [];
  const files = listQ.data?.pages.flatMap((p) => p.files) ?? [];
  const breadcrumb = firstPage?.breadcrumb ?? [];

  const switchSpace = (s: FileSpace) => {
    setSpace(s);
    setFolderId(null);
  };

  const enterFolder = (f: FolderView) => setFolderId(f.id);

  const openFile = async (fileId: string) => {
    try {
      const url = await fileApi.downloadUrl(fileId);
      window.open(url, '_blank', 'noopener');
    } catch {
      Toast.show({ content: '获取下载链接失败' });
    }
  };

  const onPickFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setUploading(true);
    try {
      await fileApi.upload(file, space, folderId);
      Toast.show({ icon: 'success', content: '上传成功' });
      await Promise.all([
        qc.invalidateQueries({ queryKey: ['files', 'list'] }),
        qc.invalidateQueries({ queryKey: ['files', 'quota'] }),
      ]);
    } catch {
      Toast.show({ content: '上传失败' });
    } finally {
      setUploading(false);
    }
  };

  const quota = quotaQ.data;
  const quotaPercent =
    quota && quota.capBytes
      ? Math.min(100, Math.round((quota.usedBytes / quota.capBytes) * 100))
      : 0;

  return (
    <div
      style={{
        minHeight: '100dvh',
        background: 'var(--bg)',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <NavBar onBack={() => void navigate(`/profile`)} style={{ background: 'var(--bg-elev)' }}>
        文件中心
      </NavBar>

      <Tabs
        activeKey={space}
        onChange={(k) => switchSpace(k as FileSpace)}
        style={{ background: 'var(--bg-elev)' }}
      >
        <Tabs.Tab title="我的" key="mine" />
        <Tabs.Tab title="共享" key="shared" />
      </Tabs>

      {/* 存储用量 */}
      {quota && (
        <div
          style={{
            padding: '10px 16px',
            background: 'var(--bg-elev)',
            borderTop: '1px solid var(--divider, #f5f5f5)',
          }}
        >
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              fontSize: 12,
              color: 'var(--text-tertiary)',
              marginBottom: 6,
            }}
          >
            <span>已用 {formatBytes(quota.usedBytes)}</span>
            <span>{quota.capBytes ? `共 ${formatBytes(quota.capBytes)}` : '不限'}</span>
          </div>
          {quota.capBytes != null && <ProgressBar percent={quotaPercent} />}
        </div>
      )}

      {/* 面包屑 */}
      {folderId && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 4,
            padding: '8px 16px',
            fontSize: 13,
            background: 'var(--bg-elev)',
            borderTop: '1px solid var(--divider, #f5f5f5)',
            overflowX: 'auto',
          }}
        >
          <span style={{ color: 'var(--brand)' }} onClick={() => setFolderId(null)}>
            根目录
          </span>
          {breadcrumb.map((b) => (
            <span key={b.id} style={{ color: 'var(--text-tertiary)', whiteSpace: 'nowrap' }}>
              {' / '}
              <span style={{ color: 'var(--text)' }} onClick={() => setFolderId(b.id)}>
                {b.name}
              </span>
            </span>
          ))}
        </div>
      )}

      <div style={{ flex: 1, overflowY: 'auto', padding: 12 }}>
        {listQ.isLoading ? (
          <div style={{ padding: '40px 0', textAlign: 'center', color: 'var(--brand)' }}>
            <DotLoading color="primary" />
          </div>
        ) : listQ.isError ? (
          <div style={{ padding: '40px 0', textAlign: 'center' }}>
            <ErrorBlock status="default" title="加载失败" description="请检查网络后重试" />
            <Button
              color="primary"
              size="small"
              style={{ marginTop: 12 }}
              onClick={() => void listQ.refetch()}
            >
              重试
            </Button>
          </div>
        ) : folders.length === 0 && files.length === 0 ? (
          <Empty description="这里还没有文件" style={{ padding: '48px 0' }} />
        ) : (
          <>
            <Card style={{ overflow: 'hidden' }}>
              {folders.map((f, i) => (
                <Row
                  key={f.id}
                  icon="📁"
                  title={f.name}
                  sub="文件夹"
                  first={i === 0}
                  onClick={() => enterFolder(f)}
                />
              ))}
              {files.map((f, i) => (
                <Row
                  key={f.fileId}
                  icon="📄"
                  title={f.fileName}
                  sub={`${formatBytes(f.sizeBytes)} · ${formatRelative(f.uploadedAt)}`}
                  first={i === 0 && folders.length === 0}
                  onClick={() => void openFile(f.fileId)}
                />
              ))}
            </Card>
            <InfiniteScroll
              loadMore={async () => {
                await listQ.fetchNextPage();
              }}
              hasMore={listQ.hasNextPage}
            />
          </>
        )}
      </div>

      {/* 上传 FAB（仅我的空间可传） */}
      {space === 'mine' && (
        <div
          onClick={() => !uploading && fileInput.current?.click()}
          style={{
            position: 'fixed',
            right: 20,
            bottom: 'calc(24px + env(safe-area-inset-bottom))',
            width: 52,
            height: 52,
            borderRadius: 26,
            background: 'var(--brand)',
            color: '#fff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 28,
            boxShadow: '0 6px 16px rgba(43,95,255,0.4)',
            zIndex: 100,
          }}
        >
          {uploading ? <DotLoading color="white" /> : '+'}
        </div>
      )}
      <input
        ref={fileInput}
        type="file"
        style={{ display: 'none' }}
        onChange={(e) => void onPickFile(e)}
      />
    </div>
  );
}

function Row({
  icon,
  title,
  sub,
  first,
  onClick,
}: {
  icon: string;
  title: string;
  sub: string;
  first: boolean;
  onClick: () => void;
}) {
  return (
    <div
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '12px 16px',
        borderTop: first ? 'none' : '1px solid var(--divider, #f5f5f5)',
      }}
    >
      <span style={{ fontSize: 22 }}>{icon}</span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            fontSize: 14,
            fontWeight: 500,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {title}
        </div>
        <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 2 }}>{sub}</div>
      </div>
      <span style={{ color: 'var(--text-tertiary)', fontSize: 16 }}>›</span>
    </div>
  );
}
