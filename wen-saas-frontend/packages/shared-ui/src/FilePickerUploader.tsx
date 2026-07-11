import { type ReactNode, useId, useRef, useState } from 'react';
import { Badge, Button, Empty } from 'antd';

export type UploadItemStatus = 'pending' | 'uploading' | 'done' | 'failed';

export type UploadItem = {
  id: string;
  name: string;
  /** 字节数；展示用，可不填。 */
  size?: number;
  /** 0-100；uploading 时画进度条。 */
  progress?: number;
  status: UploadItemStatus;
  /** failed 时的错误描述。 */
  error?: string;
};

export type FilePickerUploaderProps = {
  /**
   * 上传队列。组件不持有队列状态，调用方在 onSelect 后自行落到 state，
   * 上传过程通过更新 items[].progress / status 推动。
   */
  items?: UploadItem[];
  /**
   * 用户拖入 / 点击选择新文件时触发。组件不做去重，调用方自行决定是否合并 / 替换。
   */
  onSelect: (files: File[]) => void;
  /** 队列项点击"移除"。 */
  onRemove?: (id: string) => void;
  /** 队列项 failed 时点击"重试"。 */
  onRetry?: (id: string) => void;
  /** 透传给 <input>；如 ".xlsx,.xls" 或 "image/*"。 */
  accept?: string;
  /** 是否多文件，默认 true。 */
  multiple?: boolean;
  /** 拖拽区主标题，默认"拖拽文件到此处，或点击选择文件"。 */
  title?: ReactNode;
  /** 拖拽区副标题（"单个 ≤ 10 MB · 支持 xlsx"等）。 */
  hint?: ReactNode;
  /** "选择文件"按钮文案，默认"选择文件"。 */
  pickButtonLabel?: ReactNode;
  /** 禁用整个 picker（包括拖拽和按钮）。 */
  disabled?: boolean;
  /** 不渲染队列区（picker only）。 */
  hideQueue?: boolean;
  /** 高度紧凑模式：减小 padding，给 modal 内嵌用。 */
  compact?: boolean;
};

const STATUS_META: Record<
  UploadItemStatus,
  { label: string; color: 'green' | 'blue' | 'red' | 'default' }
> = {
  pending: { label: '等待', color: 'default' },
  uploading: { label: '上传中', color: 'blue' },
  done: { label: '完成', color: 'green' },
  failed: { label: '失败', color: 'red' },
};

function formatSize(bytes?: number): string {
  if (bytes == null || !Number.isFinite(bytes)) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

/**
 * 文件选择 / 上传 UI 壳。组件不绑 axios / fetch，真正的上传逻辑由调用方注入：
 * 1. 用户拖入或点击选择 → `onSelect(files)`
 * 2. 调用方把 files 落到 items 队列（status='pending'）并触发上传
 * 3. 上传过程中持续更新 items[].progress / status / error
 *
 * 这样可以兼容 axios.post(multipart) / OSS direct upload / 分片上传 等任意实现。
 */
export function FilePickerUploader({
  items = [],
  onSelect,
  onRemove,
  onRetry,
  accept,
  multiple = true,
  title = '拖拽文件到此处，或点击选择文件',
  hint,
  pickButtonLabel = '选择文件',
  disabled = false,
  hideQueue = false,
  compact = false,
}: FilePickerUploaderProps) {
  const inputId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);

  const openPicker = () => {
    if (disabled) return;
    inputRef.current?.click();
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(false);
    if (disabled) return;
    const files = Array.from(e.dataTransfer.files);
    if (files.length > 0) onSelect(files);
  };

  const handleInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    if (files.length > 0) onSelect(files);
    // 允许连续选同名文件
    e.target.value = '';
  };

  const dropPad = compact ? '32px 16px' : '60px 20px';

  return (
    <div>
      <div
        role="button"
        tabIndex={disabled ? -1 : 0}
        aria-disabled={disabled}
        aria-label={typeof title === 'string' ? title : '上传文件'}
        onClick={openPicker}
        onKeyDown={(e) => {
          if (disabled) return;
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            openPicker();
          }
        }}
        onDragOver={(e) => {
          if (disabled) return;
          e.preventDefault();
          if (!dragOver) setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        style={{
          padding: dropPad,
          border: `2px dashed ${dragOver ? 'var(--brand)' : 'var(--border)'}`,
          borderRadius: 'var(--r-md)',
          textAlign: 'center',
          background: dragOver ? 'var(--brand-bg, var(--bg-muted))' : 'var(--bg-muted)',
          cursor: disabled ? 'not-allowed' : 'pointer',
          opacity: disabled ? 0.6 : 1,
          transition: 'background .15s, border-color .15s',
        }}
      >
        <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text)' }}>{title}</div>
        <div style={{ marginTop: 12 }}>
          <Button
            type="primary"
            disabled={disabled}
            onClick={(e) => {
              e.stopPropagation();
              openPicker();
            }}
          >
            {pickButtonLabel}
          </Button>
        </div>
        {hint != null && (
          <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 12 }}>{hint}</div>
        )}
        <input
          id={inputId}
          ref={inputRef}
          type="file"
          {...(accept ? { accept } : {})}
          multiple={multiple}
          onChange={handleInput}
          style={{ display: 'none' }}
        />
      </div>

      {!hideQueue && items.length > 0 && (
        <div style={{ marginTop: 14 }}>
          <div
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: 'var(--text-secondary)',
              marginBottom: 8,
            }}
          >
            上传队列 ({items.length})
          </div>
          <div
            style={{
              border: '1px solid var(--border)',
              borderRadius: 'var(--r-md)',
              maxHeight: 280,
              overflowY: 'auto',
            }}
          >
            {items.map((it, i) => (
              <QueueRow
                key={it.id}
                item={it}
                first={i === 0}
                {...(onRemove ? { onRemove } : {})}
                {...(onRetry ? { onRetry } : {})}
              />
            ))}
          </div>
        </div>
      )}

      {!hideQueue && items.length === 0 && (
        <div style={{ marginTop: 14 }}>
          <Empty description="还未选择文件" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        </div>
      )}
    </div>
  );
}

function QueueRow({
  item,
  first,
  onRemove,
  onRetry,
}: {
  item: UploadItem;
  first: boolean;
  onRemove?: (id: string) => void;
  onRetry?: (id: string) => void;
}) {
  const meta = STATUS_META[item.status];
  const progress = Math.max(0, Math.min(100, item.progress ?? 0));
  const isUploading = item.status === 'uploading';
  const isFailed = item.status === 'failed';
  const isDone = item.status === 'done';

  return (
    <div
      style={{
        padding: '10px 14px',
        borderTop: first ? 'none' : '1px solid var(--divider)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
        <span
          style={{
            fontSize: 13,
            fontWeight: 500,
            flex: 1,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            color: 'var(--text)',
          }}
          title={item.name}
        >
          {item.name}
        </span>
        {item.size != null && (
          <span style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
            {formatSize(item.size)}
          </span>
        )}
        {isUploading ? (
          <Badge color={meta.color} text={`${progress}%`} />
        ) : (
          <Badge color={meta.color} text={meta.label} />
        )}
        {isFailed && onRetry && (
          <Button size="small" onClick={() => onRetry(item.id)}>
            重试
          </Button>
        )}
        {onRemove && !isUploading && (
          <Button size="small" type="text" onClick={() => onRemove(item.id)}>
            移除
          </Button>
        )}
      </div>
      <div
        role="progressbar"
        aria-valuenow={progress}
        aria-valuemin={0}
        aria-valuemax={100}
        style={{
          height: 4,
          background: 'var(--bg-muted)',
          borderRadius: 2,
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            width: `${isDone ? 100 : progress}%`,
            height: '100%',
            background: isDone
              ? 'var(--success, #047857)'
              : isFailed
                ? 'var(--danger, #DC2626)'
                : 'var(--brand)',
            transition: 'width .2s',
          }}
        />
      </div>
      {isFailed && item.error != null && (
        <div style={{ fontSize: 12, color: 'var(--danger)', marginTop: 4 }}>{item.error}</div>
      )}
    </div>
  );
}
