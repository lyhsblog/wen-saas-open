import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { DangerConfirm } from './DangerConfirm';

describe('DangerConfirm', () => {
  it('未传 confirmPhrase 时按钮直接可点', () => {
    const onConfirm = vi.fn();
    render(
      <DangerConfirm
        open
        title="确认操作"
        lead="即将执行不可逆操作"
        consequences={['影响 1', '影响 2']}
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    );
    expect(screen.queryByText('即将执行不可逆操作')).not.toBeNull();
    expect(screen.queryByText('影响 1')).not.toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /确\s*认/ }));
    expect(onConfirm).toHaveBeenCalled();
  });

  it('confirmPhrase 不匹配时确认按钮禁用 + 显示提示', () => {
    const onConfirm = vi.fn();
    render(
      <DangerConfirm
        open
        title="删除租户"
        lead="您即将删除租户「示例租户」"
        confirmPhrase="示例租户"
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    );
    const confirmBtn = screen.getByRole('button', { name: /确\s*认/ });
    expect((confirmBtn as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(confirmBtn);
    expect(onConfirm).not.toHaveBeenCalled();

    const input = screen.getByPlaceholderText('示例租户');
    fireEvent.change(input, { target: { value: '错的名字' } });
    expect(screen.queryByText('输入不匹配，请检查')).not.toBeNull();
    expect((confirmBtn as HTMLButtonElement).disabled).toBe(true);
  });

  it('confirmPhrase 输入匹配后启用并触发 onConfirm', () => {
    const onConfirm = vi.fn();
    render(
      <DangerConfirm
        open
        title="删除租户"
        lead="您即将删除租户「示例租户」"
        confirmPhrase="示例租户"
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    );
    const input = screen.getByPlaceholderText('示例租户');
    fireEvent.change(input, { target: { value: '示例租户' } });
    const confirmBtn = screen.getByRole('button', { name: /确\s*认/ });
    expect((confirmBtn as HTMLButtonElement).disabled).toBe(false);
    fireEvent.click(confirmBtn);
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it('confirming=true 时按钮 loading 且禁用', () => {
    render(
      <DangerConfirm
        open
        title="切换"
        lead="切换中"
        confirming
        onCancel={() => undefined}
        onConfirm={() => undefined}
      />,
    );
    const okBtn = screen.getByRole<HTMLButtonElement>('button', { name: /确\s*认/ });
    const cancelBtn = screen.getByRole<HTMLButtonElement>('button', { name: /取\s*消/ });
    expect(okBtn.disabled).toBe(true);
    expect(cancelBtn.disabled).toBe(true);
  });

  it('取消按钮触发 onCancel', () => {
    const onCancel = vi.fn();
    render(
      <DangerConfirm open title="确认" lead="…" onCancel={onCancel} onConfirm={() => undefined} />,
    );
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    expect(onCancel).toHaveBeenCalled();
  });

  // open false → true 时输入清空依赖 Modal `destroyOnHidden` 触发 Body 卸载，
  // 这是浏览器端 CSS transition 完成后的行为；jsdom 不跑 transition，
  // 在测试里难稳定复现，留给真浏览器 / Playwright e2e 验证。
});
