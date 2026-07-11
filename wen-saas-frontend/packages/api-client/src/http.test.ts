import { describe, expect, it } from 'vitest';
import { ApiError, describeApiError } from './http';

describe('ApiError', () => {
  it('exposes status guards', () => {
    const err = new ApiError(403, { title: '禁止访问' }, 'req-1');
    expect(err.isForbidden()).toBe(true);
    expect(err.isServerError()).toBe(false);
    expect(err.message).toBe('禁止访问');
  });

  it('parses fieldErrors from ProblemDetail.errors[]', () => {
    const err = new ApiError(
      422,
      {
        title: 'Validation failed',
        errors: [
          { field: 'name', message: '不能为空' },
          { field: 'name', message: '重复忽略' },
          { field: 'code', message: '编码格式错误' },
          { message: 'orphan ignored' },
        ],
      },
      undefined,
    );
    expect(err.isValidation()).toBe(true);
    expect(err.fieldErrors).toEqual({ name: '不能为空', code: '编码格式错误' });
  });

  it('flags network failure when status=0', () => {
    const err = new ApiError(0, { title: 'Network Error' }, undefined);
    expect(err.isNetwork()).toBe(true);
  });
});

describe('describeApiError', () => {
  it('returns fallback for non-ApiError', () => {
    expect(describeApiError(new Error('boom'), '默认')).toBe('默认');
  });

  it('hints "请检查表单" on 422', () => {
    const err = new ApiError(422, { title: '校验失败' }, undefined);
    expect(describeApiError(err)).toBe('校验失败 · 请检查表单');
  });

  it('appends request id on 5xx', () => {
    const err = new ApiError(500, { title: '内部错误' }, 'req-42');
    expect(describeApiError(err)).toBe('内部错误 · 请求编号 req-42');
  });

  it('appends request id on 422 when present', () => {
    const err = new ApiError(422, { title: '校验失败' }, 'req-422');
    expect(describeApiError(err)).toBe('校验失败 · 请检查表单 · 请求编号 req-422');
  });

  it('appends request id on 409 when present', () => {
    const err = new ApiError(409, { title: '编码已存在' }, 'req-409');
    expect(describeApiError(err)).toBe('编码已存在 · 请求编号 req-409');
  });

  it('appends request id on 429 when present', () => {
    const err = new ApiError(429, { title: '请求过于频繁' }, 'req-429');
    expect(describeApiError(err)).toBe('请求过于频繁 · 请稍后重试 · 请求编号 req-429');
  });

  it('omits request id when absent', () => {
    const err = new ApiError(409, { title: '编码已存在' }, undefined);
    expect(describeApiError(err)).toBe('编码已存在');
  });

  it('uses fixed copy for network failure', () => {
    const err = new ApiError(0, { title: 'Network Error' }, undefined);
    expect(describeApiError(err)).toBe('网络异常，请检查连接后重试');
  });

  it('appends request id on network failure when present', () => {
    const err = new ApiError(0, { title: 'Network Error' }, 'req-net');
    expect(describeApiError(err)).toBe('网络异常，请检查连接后重试 · 请求编号 req-net');
  });
});
