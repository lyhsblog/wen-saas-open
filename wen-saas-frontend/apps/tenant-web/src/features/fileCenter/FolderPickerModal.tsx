import { useState } from 'react';
import { Breadcrumb, Empty, List, Modal, Spin } from 'antd';
import { FolderOutlined, RightOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import type { FileSpace } from '@wen-saas/api-client';
import { fileCenterApi } from './fileCenterApi';

type Props = {
  open: boolean;
  space: FileSpace;
  /** 移动文件夹时排除自身（不允许移入自身；子树由后端兜底校验）。 */
  excludeFolderId?: string;
  onClose: () => void;
  onPick: (targetFolderId: string | null) => void;
};

/** 目标目录选择器：在目标空间内逐层浏览，选定「移到此处」。 */
export function FolderPickerModal({ open, space, excludeFolderId, onClose, onPick }: Props) {
  const [folderId, setFolderId] = useState<string | null>(null);

  const q = useQuery({
    queryKey: ['fileCenter', 'picker', space, folderId ?? 'root'],
    queryFn: () => fileCenterApi.list(space, { folderId, size: 200 }),
    enabled: open,
  });

  const folders = (q.data?.folders ?? []).filter((f) => f.id !== excludeFolderId);
  const breadcrumb = q.data?.breadcrumb ?? [];

  const reset = () => setFolderId(null);

  return (
    <Modal
      title="移动到"
      open={open}
      onCancel={() => {
        reset();
        onClose();
      }}
      onOk={() => {
        onPick(folderId);
        reset();
      }}
      okText="移到此处"
      width={460}
    >
      <Breadcrumb
        items={[
          {
            title: (
              <a onClick={() => setFolderId(null)}>{space === 'mine' ? '我的文件' : '共享文件'}</a>
            ),
          },
          ...breadcrumb.map((b) => ({ title: <a onClick={() => setFolderId(b.id)}>{b.name}</a> })),
        ]}
        style={{ marginBottom: 12 }}
      />
      {q.isLoading ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin />
        </div>
      ) : folders.length === 0 ? (
        <Empty description="此目录下无子文件夹" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <List
          size="small"
          dataSource={folders}
          renderItem={(f) => (
            <List.Item
              style={{ cursor: 'pointer' }}
              onClick={() => setFolderId(f.id)}
              actions={[<RightOutlined key="in" style={{ color: 'var(--text-tertiary)' }} />]}
            >
              <List.Item.Meta avatar={<FolderOutlined />} title={f.name} />
            </List.Item>
          )}
        />
      )}
    </Modal>
  );
}
