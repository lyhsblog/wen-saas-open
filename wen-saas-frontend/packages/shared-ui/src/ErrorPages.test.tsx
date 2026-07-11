import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import {
  Conflict409Page,
  Forbidden403Page,
  NotFound404Page,
  ServerError5xxPage,
} from './ErrorPages';

describe('Forbidden403Page', () => {
  it('默认文案 + 不传 onBackHome 时不渲染按钮', () => {
    render(<Forbidden403Page />);
    expect(screen.queryByText('无权访问')).not.toBeNull();
    expect(screen.queryByRole('button', { name: '返回首页' })).toBeNull();
  });

  it('onBackHome 渲染主按钮', () => {
    const onBackHome = vi.fn();
    render(<Forbidden403Page onBackHome={onBackHome} />);
    fireEvent.click(screen.getByRole('button', { name: '返回首页' }));
    expect(onBackHome).toHaveBeenCalledOnce();
  });
});

describe('NotFound404Page', () => {
  it('默认标题', () => {
    render(<NotFound404Page />);
    expect(screen.queryByText('页面不存在')).not.toBeNull();
  });
});

describe('Conflict409Page', () => {
  it('onRefresh + onBackHome 都渲染', () => {
    const onRefresh = vi.fn();
    const onBackHome = vi.fn();
    render(<Conflict409Page onRefresh={onRefresh} onBackHome={onBackHome} />);
    fireEvent.click(screen.getByRole('button', { name: '刷新页面' }));
    fireEvent.click(screen.getByRole('button', { name: '返回首页' }));
    expect(onRefresh).toHaveBeenCalledOnce();
    expect(onBackHome).toHaveBeenCalledOnce();
  });
});

describe('ServerError5xxPage', () => {
  it('requestId 展示等宽编号', () => {
    render(<ServerError5xxPage requestId="req-abc-42" />);
    expect(screen.queryByText('req-abc-42')).not.toBeNull();
  });
});
