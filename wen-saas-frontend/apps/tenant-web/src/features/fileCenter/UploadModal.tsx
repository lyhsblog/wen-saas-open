import { useState } from 'react';
import { App, Modal } from 'antd';
import { FilePickerUploader, type UploadItem } from '@wen-saas/shared-ui';
import { describeApiError, type FileSpace } from '@wen-saas/api-client';
import { fileCenterApi } from './fileCenterApi';

type Props = {
  open: boolean;
  space: FileSpace;
  folderId: string | null;
  onClose: () => void;
  onUploaded: () => void;
};

const MAX_FILES = 20;
const MAX_SIZE = 100 * 1024 * 1024; // 100MB

/** 上传弹窗：FilePickerUploader + 队列进度，逐个 multipart 上传，重名后缀由后端处理。 */
export function UploadModal({ open, space, folderId, onClose, onUploaded }: Props) {
  const { message } = App.useApp();
  const [items, setItems] = useState<UploadItem[]>([]);
  const [filesById] = useState<Map<string, File>>(() => new Map());
  const [uploading, setUploading] = useState(false);

  const patch = (id: string, p: Partial<UploadItem>) =>
    setItems((prev) => prev.map((it) => (it.id === id ? { ...it, ...p } : it)));

  const uploadOne = async (id: string, file: File) => {
    patch(id, { status: 'uploading', progress: 0 });
    try {
      await fileCenterApi.upload(file, space, folderId, (pct) => patch(id, { progress: pct }));
      patch(id, { status: 'done', progress: 100 });
    } catch (e) {
      patch(id, { status: 'failed', error: describeApiError(e, '上传失败') });
    }
  };

  const handleSelect = async (files: File[]) => {
    const accepted = files.slice(0, MAX_FILES - items.length);
    if (files.length > accepted.length) message.warning(`单次最多 ${MAX_FILES} 个文件`);
    const newItems: UploadItem[] = [];
    for (const f of accepted) {
      if (f.size > MAX_SIZE) {
        message.warning(`「${f.name}」超过 100MB，已跳过`);
        continue;
      }
      const id = crypto.randomUUID();
      filesById.set(id, f);
      newItems.push({ id, name: f.name, size: f.size, status: 'pending', progress: 0 });
    }
    if (newItems.length === 0) return;
    setItems((prev) => [...prev, ...newItems]);
    setUploading(true);
    await Promise.all(newItems.map((it) => uploadOne(it.id, filesById.get(it.id)!)));
    setUploading(false);
    onUploaded();
  };

  const handleRetry = (id: string) => {
    const f = filesById.get(id);
    if (f) void uploadOne(id, f).then(onUploaded);
  };

  const handleClose = () => {
    if (uploading) return;
    setItems([]);
    filesById.clear();
    onClose();
  };

  return (
    <Modal
      title={`上传到「${space === 'mine' ? '我的文件' : '共享文件'}」`}
      open={open}
      onCancel={handleClose}
      footer={null}
      width={560}
      mask={{ closable: !uploading }}
    >
      <FilePickerUploader
        items={items}
        onSelect={(files) => {
          void handleSelect(files);
        }}
        onRetry={handleRetry}
        onRemove={(id) => setItems((prev) => prev.filter((it) => it.id !== id))}
        hint="单个 ≤ 100 MB · 单次最多 20 个 · 重名将自动加 (2)(3) 后缀"
        compact
      />
    </Modal>
  );
}
