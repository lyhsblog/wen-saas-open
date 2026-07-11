import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { TabsBar } from './TabsBar';
import type { TabItem } from './types';

const items: TabItem[] = [
  { key: '/workbench', title: '工作台', closable: false },
  { key: '/system/roles', title: '角色' },
];

describe('TabsBar', () => {
  it('渲染全部页签；不可关闭页签不出关闭按钮', () => {
    render(<TabsBar items={items} activeKey="/workbench" onActivate={vi.fn()} onClose={vi.fn()} />);
    expect(screen.getByText('工作台')).toBeTruthy();
    expect(screen.getByText('角色')).toBeTruthy();
    // 仅可关闭页签渲染关闭按钮，故全表只有 1 个。
    expect(screen.getAllByLabelText('关闭页签')).toHaveLength(1);
  });

  it('点页签触发 onActivate', () => {
    const onActivate = vi.fn();
    render(
      <TabsBar items={items} activeKey="/workbench" onActivate={onActivate} onClose={vi.fn()} />,
    );
    fireEvent.click(screen.getByText('角色'));
    expect(onActivate).toHaveBeenCalledWith('/system/roles');
  });

  it('点关闭按钮触发 onClose 且不冒泡到激活', () => {
    const onActivate = vi.fn();
    const onClose = vi.fn();
    render(
      <TabsBar items={items} activeKey="/workbench" onActivate={onActivate} onClose={onClose} />,
    );
    fireEvent.click(screen.getByLabelText('关闭页签'));
    expect(onClose).toHaveBeenCalledWith('/system/roles');
    expect(onActivate).not.toHaveBeenCalled();
  });

  it('鼠标中键关闭可关闭页签', () => {
    const onClose = vi.fn();
    render(<TabsBar items={items} activeKey="/workbench" onActivate={vi.fn()} onClose={onClose} />);
    fireEvent(screen.getByText('角色'), new MouseEvent('auxclick', { bubbles: true, button: 1 }));
    expect(onClose).toHaveBeenCalledWith('/system/roles');
  });
});
