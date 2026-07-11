import { describe, expect, it, vi } from 'vitest';
import { ApiError } from './http';
import { applyApiErrorToForm } from './applyApiErrorToForm';

describe('applyApiErrorToForm', () => {
  it('422 + fieldErrors → 逐个 setError 并返回 true', () => {
    const setError = vi.fn();
    const err = new ApiError(
      422,
      {
        title: 'validation',
        errors: [
          { field: 'name', message: '不能为空' },
          { field: 'code', message: '格式错误' },
        ],
      },
      undefined,
    );
    expect(applyApiErrorToForm(err, setError)).toBe(true);
    expect(setError).toHaveBeenCalledTimes(2);
    expect(setError).toHaveBeenCalledWith('name', { type: 'server', message: '不能为空' });
    expect(setError).toHaveBeenCalledWith('code', { type: 'server', message: '格式错误' });
  });

  it('422 但无 fieldErrors → 返回 false', () => {
    const setError = vi.fn();
    const err = new ApiError(422, { title: 'validation' }, undefined);
    expect(applyApiErrorToForm(err, setError)).toBe(false);
    expect(setError).not.toHaveBeenCalled();
  });

  it('非 422 → 返回 false', () => {
    const setError = vi.fn();
    const err = new ApiError(500, { title: 'boom' }, undefined);
    expect(applyApiErrorToForm(err, setError)).toBe(false);
  });

  it('非 ApiError → 返回 false', () => {
    expect(applyApiErrorToForm(new Error('x'), () => undefined)).toBe(false);
  });

  it('fieldMap 重写字段名', () => {
    const setError = vi.fn();
    const err = new ApiError(
      422,
      { title: 'v', errors: [{ field: 'support_hierarchy', message: '必须为布尔' }] },
      undefined,
    );
    applyApiErrorToForm(err, setError, { fieldMap: { support_hierarchy: 'supportHierarchy' } });
    expect(setError).toHaveBeenCalledWith('supportHierarchy', {
      type: 'server',
      message: '必须为布尔',
    });
  });
});
