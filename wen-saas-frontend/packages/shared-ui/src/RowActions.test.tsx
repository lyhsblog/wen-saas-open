import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { RowActionsBar } from './RowActions';
import type { RowActionDef } from './RowActions';

const noop = () => undefined;

function openMore() {
  fireEvent.click(screen.getByRole('button', { name: '更多操作' }));
}

describe('RowActionsBar', () => {
  it('primary 平铺渲染并回调 onClick', () => {
    const onClick = vi.fn();
    render(<RowActionsBar primary={[{ key: 'edit', label: '编辑', onClick }]} />);
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    expect(onClick).toHaveBeenCalledOnce();
    // 无 more 时不渲染「⋯」触发器
    expect(screen.queryByRole('button', { name: '更多操作' })).toBeNull();
  });

  it('more 里 danger 项自动置底并在上方加分隔线', () => {
    const more: RowActionDef[] = [
      { key: 'del', label: '删除', onClick: noop, danger: true },
      { key: 'rotate', label: '轮换密钥', onClick: noop },
      { key: 'logs', label: '日志', onClick: noop },
    ];
    const { baseElement } = render(<RowActionsBar primary={[]} more={more} />);
    openMore();
    const items = screen.getAllByRole('menuitem');
    expect(items.map((el) => el.textContent)).toEqual(['轮换密钥', '日志', '删除']);
    expect(items[2]?.className).toContain('ant-dropdown-menu-item-danger');
    expect(baseElement.querySelector('.ant-dropdown-menu-item-divider')).not.toBeNull();
  });

  it('more 全为 danger 时不出分隔线', () => {
    const { baseElement } = render(
      <RowActionsBar
        primary={[]}
        more={[{ key: 'del', label: '删除', onClick: noop, danger: true }]}
      />,
    );
    openMore();
    expect(baseElement.querySelector('.ant-dropdown-menu-item-divider')).toBeNull();
  });

  it('more 里 pending / disabled 项置灰且点击不回调', () => {
    const onClick = vi.fn();
    render(
      <RowActionsBar
        primary={[]}
        more={[
          { key: 'a', label: '启用', onClick, pending: true },
          { key: 'b', label: '停用', onClick, disabled: true },
        ]}
      />,
    );
    openMore();
    const items = screen.getAllByRole('menuitem');
    for (const el of items) {
      expect(el.className).toContain('ant-dropdown-menu-item-disabled');
      fireEvent.click(el);
    }
    expect(onClick).not.toHaveBeenCalled();
  });

  it('primary pending 项 loading + 禁用', () => {
    const onClick = vi.fn();
    render(<RowActionsBar primary={[{ key: 'save', label: '保存', onClick, pending: true }]} />);
    const btn = screen.getByRole('button', { name: /保存/ });
    expect(btn.hasAttribute('disabled')).toBe(true);
    expect(btn.className).toContain('ant-btn-loading');
    fireEvent.click(btn);
    expect(onClick).not.toHaveBeenCalled();
  });
});
