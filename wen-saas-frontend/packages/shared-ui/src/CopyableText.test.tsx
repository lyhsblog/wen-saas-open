import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { CopyableText } from './CopyableText';

describe('CopyableText', () => {
  it('value 为空时渲染占位、不显示复制图标', () => {
    const { container } = render(<CopyableText value={null} />);
    expect(container.textContent).toBe('—');
    expect(container.querySelector('.ant-typography-copy')).toBeNull();
  });

  it('有 value 时渲染文本 + 复制图标', () => {
    const { container } = render(<CopyableText value="TENANT-001" />);
    expect(screen.queryByText('TENANT-001')).not.toBeNull();
    expect(container.querySelector('.ant-typography-copy')).not.toBeNull();
  });

  it('children 作为自定义展示内容（展示带装饰、复制原文）', () => {
    render(
      <CopyableText value="acme">
        <code>/acme/</code>
      </CopyableText>,
    );
    expect(screen.queryByText('/acme/')).not.toBeNull();
  });

  it('点复制图标不冒泡到父行点击；点文本仍冒泡', () => {
    const onRow = vi.fn();
    const { container } = render(
      <div onClick={onRow}>
        <CopyableText value="X-1" />
      </div>,
    );
    fireEvent.click(container.querySelector('.ant-typography-copy') as HTMLElement);
    expect(onRow).not.toHaveBeenCalled();

    fireEvent.click(screen.getByText('X-1'));
    expect(onRow).toHaveBeenCalledOnce();
  });
});
