import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { FilePickerUploader, type UploadItem } from './FilePickerUploader';

function makeFile(name: string, size = 1024): File {
  const f = new File(['x'.repeat(size)], name, { type: 'text/plain' });
  return f;
}

describe('FilePickerUploader', () => {
  it('input change 触发 onSelect 透传 File[]', () => {
    const onSelect = vi.fn();
    const { container } = render(<FilePickerUploader onSelect={onSelect} />);
    expect(screen.queryByText('选择文件')).not.toBeNull();
    const input = container.querySelector('input[type="file"]');
    if (!(input instanceof HTMLInputElement)) throw new Error('input not found');
    Object.defineProperty(input, 'files', { value: [makeFile('a.txt')] });
    fireEvent.change(input);
    expect(onSelect).toHaveBeenCalledTimes(1);
    const calledWith = onSelect.mock.calls[0]?.[0] as File[] | undefined;
    expect(calledWith?.[0]?.name).toBe('a.txt');
  });

  it('hideQueue=true 不渲染队列与空态', () => {
    const items: UploadItem[] = [{ id: '1', name: 'a.txt', status: 'pending' }];
    render(<FilePickerUploader onSelect={() => undefined} items={items} hideQueue />);
    expect(screen.queryByText(/上传队列/)).toBeNull();
    expect(screen.queryByText('a.txt')).toBeNull();
  });

  it('uploading 状态画进度条 aria-valuenow', () => {
    const items: UploadItem[] = [
      { id: '1', name: 'large.xlsx', status: 'uploading', progress: 72, size: 3.6 * 1024 * 1024 },
    ];
    render(<FilePickerUploader onSelect={() => undefined} items={items} />);
    // 第二个 progressbar（第一个属于其它...实际只有一个：拖拽区不是 progressbar）
    const bars = screen.getAllByRole('progressbar');
    const queueBar = bars.find((b) => b.getAttribute('aria-valuenow') === '72');
    expect(queueBar).toBeDefined();
    expect(screen.queryByText('3.6 MB')).not.toBeNull();
  });

  it('failed 状态展示 error + 重试按钮触发 onRetry', () => {
    const onRetry = vi.fn();
    const items: UploadItem[] = [
      { id: 'f1', name: 'bad.xlsx', status: 'failed', error: '校验失败' },
    ];
    render(<FilePickerUploader onSelect={() => undefined} items={items} onRetry={onRetry} />);
    expect(screen.queryByText('校验失败')).not.toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
    expect(onRetry).toHaveBeenCalledWith('f1');
  });

  it('移除按钮触发 onRemove', () => {
    const onRemove = vi.fn();
    const items: UploadItem[] = [{ id: '9', name: 'done.txt', status: 'done' }];
    render(<FilePickerUploader onSelect={() => undefined} items={items} onRemove={onRemove} />);
    fireEvent.click(screen.getByRole('button', { name: /移\s*除/ }));
    expect(onRemove).toHaveBeenCalledWith('9');
  });

  it('disabled=true 阻止拖拽 onSelect', () => {
    const onSelect = vi.fn();
    const { container } = render(<FilePickerUploader onSelect={onSelect} disabled />);
    const drop = container.querySelector('[role="button"]');
    if (!(drop instanceof HTMLElement)) throw new Error('dropzone not found');
    fireEvent.drop(drop, { dataTransfer: { files: [makeFile('x.txt')] } });
    expect(onSelect).not.toHaveBeenCalled();
  });
});
