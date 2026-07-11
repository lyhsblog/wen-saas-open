import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { TaskCenter, type TaskItem } from './TaskCenter';

const tasks: TaskItem[] = [
  { id: '1', kind: 'import', name: '导入成员', status: 'done', finish: '今天 10:22', who: '我' },
  {
    id: '2',
    kind: 'export',
    name: '导出操作日志',
    status: 'running',
    progress: 45,
    eta: '约 30 秒',
    who: '我',
  },
  {
    id: '3',
    kind: 'import',
    name: '导入字典 · 客户来源',
    status: 'failed',
    result: '文件解析错误',
  },
];

describe('TaskCenter', () => {
  it('渲染全部任务 + running 任务画进度条', () => {
    render(<TaskCenter tasks={tasks} />);
    expect(screen.queryByText('导入成员')).not.toBeNull();
    expect(screen.queryByText('导出操作日志')).not.toBeNull();
    expect(screen.queryByText('导入字典 · 客户来源')).not.toBeNull();
    const bar = screen.getByRole('progressbar');
    expect(bar.getAttribute('aria-valuenow')).toBe('45');
  });

  it('filter=running 只展示进行中任务', () => {
    render(<TaskCenter tasks={tasks} filter="running" onFilterChange={() => undefined} />);
    expect(screen.queryByText('导出操作日志')).not.toBeNull();
    expect(screen.queryByText('导入成员')).toBeNull();
    expect(screen.queryByText('导入字典 · 客户来源')).toBeNull();
  });

  it('filter chip 点击触发 onFilterChange', () => {
    const onFilterChange = vi.fn();
    render(<TaskCenter tasks={tasks} filter="all" onFilterChange={onFilterChange} />);
    fireEvent.click(screen.getByRole('tab', { name: /已失败 1/ }));
    expect(onFilterChange).toHaveBeenCalledWith('failed');
  });

  it('actionsForTask + onAction 联动', () => {
    const onAction = vi.fn();
    render(
      <TaskCenter
        tasks={tasks}
        actionsForTask={(t) =>
          t.status === 'failed' ? [{ key: 'detail', label: '查看详情' }] : []
        }
        onAction={onAction}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /查看详情/ }));
    expect(onAction).toHaveBeenCalledWith(
      expect.objectContaining({ id: '3', status: 'failed' }),
      'detail',
    );
  });

  it('failed 任务 result 用 danger 色', () => {
    render(<TaskCenter tasks={tasks} />);
    const result = screen.getByText('文件解析错误');
    expect(result.getAttribute('style')).toContain('var(--danger)');
  });

  it('pending 计入「进行中」、canceled 计入「已失败」，保证「全部 = 各桶之和」', () => {
    const mixed: TaskItem[] = [
      { id: '1', kind: 'import', name: 'A', status: 'done' },
      { id: '2', kind: 'import', name: 'B', status: 'running', progress: 10 },
      { id: '3', kind: 'import', name: 'C', status: 'pending' },
      { id: '4', kind: 'import', name: 'D', status: 'canceled' },
    ];
    render(<TaskCenter tasks={mixed} filter="all" onFilterChange={() => undefined} />);
    // running(1) + pending(1) = 进行中 2；failed(0) + canceled(1) = 已失败 1；done 1；全部 4 = 2+1+1
    expect(screen.queryByRole('tab', { name: /全部 4/ })).not.toBeNull();
    expect(screen.queryByRole('tab', { name: /进行中 2/ })).not.toBeNull();
    expect(screen.queryByRole('tab', { name: /已完成 1/ })).not.toBeNull();
    expect(screen.queryByRole('tab', { name: /已失败 1/ })).not.toBeNull();
  });

  it('filter=running 含 pending 任务', () => {
    const mixed: TaskItem[] = [
      { id: '2', kind: 'import', name: 'B', status: 'running', progress: 10 },
      { id: '3', kind: 'import', name: 'C 等待中', status: 'pending' },
    ];
    render(<TaskCenter tasks={mixed} filter="running" onFilterChange={() => undefined} />);
    expect(screen.queryByText('C 等待中')).not.toBeNull();
  });

  it('空任务展示 emptyDescription', () => {
    render(<TaskCenter tasks={[]} emptyDescription="所有任务都在 30 天内会保留" />);
    expect(screen.queryByText('所有任务都在 30 天内会保留')).not.toBeNull();
  });
});
