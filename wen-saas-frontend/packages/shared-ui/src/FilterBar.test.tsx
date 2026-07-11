import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { FilterBar } from './FilterBar';

const fields = [
  { key: 'status', label: '阶段', control: <input aria-label="阶段输入" /> },
  { key: 'keyword', label: '关键字', control: <input aria-label="关键字输入" /> },
];
const moreFields = [{ key: 'cf1', label: '客户等级', control: <input aria-label="客户等级输入" /> }];

describe('FilterBar', () => {
  it('默认收起 moreFields，展开后可见、chips 隐藏', () => {
    render(
      <FilterBar
        fields={fields}
        moreFields={moreFields}
        chips={[{ key: 'cf1', text: '客户等级：A' }]}
        actions={<button>搜索</button>}
      />,
    );
    // 常显字段可见，折叠字段不可见，已选条件以 chip 回显
    expect(screen.queryByText('阶段')).not.toBeNull();
    expect(screen.queryByLabelText('客户等级输入')).toBeNull();
    expect(screen.queryByText('客户等级：A')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /更多筛选/ }));
    expect(screen.queryByLabelText('客户等级输入')).not.toBeNull();
    // 展开后字段本身可见，不再重复回显 chip
    expect(screen.queryByText('客户等级：A')).toBeNull();
    expect(screen.queryByRole('button', { name: /收起/ })).not.toBeNull();
  });

  it('无 moreFields 时不渲染折叠开关', () => {
    render(<FilterBar fields={fields} actions={<button>搜索</button>} />);
    expect(screen.queryByRole('button', { name: /更多筛选/ })).toBeNull();
  });

  it('chip 可移除：点 × 触发 onRemove', () => {
    const onRemove = vi.fn();
    render(
      <FilterBar
        fields={fields}
        moreFields={moreFields}
        chips={[{ key: 'cf1', text: '客户等级：A', onRemove }]}
      />,
    );
    const chip = screen.getByText('客户等级：A').closest('.ant-tag')!;
    fireEvent.click(chip.querySelector('.ant-tag-close-icon')!);
    expect(onRemove).toHaveBeenCalled();
  });
});
