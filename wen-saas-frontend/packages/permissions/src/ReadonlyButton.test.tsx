import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { ReadonlyButton } from './ReadonlyButton';
import { emptyCapabilities, useCapabilitiesStore } from './capabilities';

function setReadonly(readonly: boolean) {
  useCapabilitiesStore.getState().setCapabilities({
    ...emptyCapabilities,
    tenantOwner: true,
    packageReadonly: readonly,
  });
}

afterEach(() => {
  useCapabilitiesStore.getState().reset();
});

describe('ReadonlyButton', () => {
  it('非只读态：透传为普通按钮，可点击', () => {
    setReadonly(false);
    const onClick = vi.fn();
    render(<ReadonlyButton onClick={onClick}>保存</ReadonlyButton>);
    const btn = screen.getByRole('button', { name: /保\s*存/ });
    expect(btn.hasAttribute('disabled')).toBe(false);
    fireEvent.click(btn);
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('只读态：按钮禁用，点击不触发 onClick', () => {
    setReadonly(true);
    const onClick = vi.fn();
    render(<ReadonlyButton onClick={onClick}>保存</ReadonlyButton>);
    const btn = screen.getByRole('button', { name: /保\s*存/ });
    expect(btn.hasAttribute('disabled')).toBe(true);
    fireEvent.click(btn);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('调用方自身 disabled 在非只读态依然生效', () => {
    setReadonly(false);
    render(<ReadonlyButton disabled>保存</ReadonlyButton>);
    const btn = screen.getByRole('button', { name: /保\s*存/ });
    expect(btn.hasAttribute('disabled')).toBe(true);
  });
});
