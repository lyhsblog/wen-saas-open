import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import type { ColumnsType } from 'antd/lib/table';
import { DataTable } from './DataTable';

type Row = { id: number; name: string };

const columns: ColumnsType<Row> = [{ title: '名称', dataIndex: 'name', key: 'name' }];

describe('DataTable', () => {
  it('渲染 title + toolbar', () => {
    render(
      <DataTable<Row>
        title="字典分组"
        toolbar={<button type="button">新建</button>}
        columns={columns}
        dataSource={[]}
        pagination={false}
      />,
    );
    expect(screen.queryByText('字典分组')).not.toBeNull();
    expect(screen.queryByRole('button', { name: '新建' })).not.toBeNull();
  });

  it('dataSource 为空时显示自定义空态', () => {
    render(
      <DataTable<Row>
        columns={columns}
        dataSource={[]}
        pagination={false}
        empty={{ description: '还没有数据', action: <button type="button">新增第一条</button> }}
      />,
    );
    expect(screen.queryByText('还没有数据')).not.toBeNull();
    expect(screen.queryByRole('button', { name: '新增第一条' })).not.toBeNull();
  });

  it('点击行触发 onRowClick', () => {
    const handler = vi.fn();
    render(
      <DataTable<Row>
        columns={columns}
        dataSource={[{ id: 1, name: '示例' }]}
        pagination={false}
        onRowClick={handler}
      />,
    );
    fireEvent.click(screen.getByText('示例'));
    expect(handler).toHaveBeenCalledOnce();
    expect(handler.mock.calls[0]?.[0]).toEqual({ id: 1, name: '示例' });
  });

  it('rowKey 默认取 record.id（写入 tr[data-row-key]）', () => {
    const { container } = render(
      <DataTable<Row>
        columns={columns}
        dataSource={[{ id: 42, name: '行一' }]}
        pagination={false}
      />,
    );
    expect(container.querySelector('tr[data-row-key="42"]')).not.toBeNull();
  });

  it('selectedRowKey 命中行套 ant-table-row-selected', () => {
    const { container } = render(
      <DataTable<Row>
        columns={columns}
        dataSource={[
          { id: 1, name: '行一' },
          { id: 2, name: '行二' },
        ]}
        pagination={false}
        selectedRowKey={2}
      />,
    );
    expect(
      container.querySelector('tr[data-row-key="2"]')?.classList.contains('ant-table-row-selected'),
    ).toBe(true);
    expect(
      container.querySelector('tr[data-row-key="1"]')?.classList.contains('ant-table-row-selected'),
    ).toBe(false);
  });

  it('simplePagination 渲染极简底栏并回调 onChange', () => {
    const onChange = vi.fn();
    render(
      <DataTable<Row>
        columns={columns}
        dataSource={[{ id: 1, name: '行一' }]}
        simplePagination={{ total: 41, page: 2, pageSize: 20, onChange }}
      />,
    );
    expect(screen.queryByText('共 41 条 · 第 21-40 条')).not.toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '下一页' }));
    expect(onChange).toHaveBeenCalledWith(3);
  });
});
