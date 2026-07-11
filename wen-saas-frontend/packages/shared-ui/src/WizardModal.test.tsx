import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { WizardModal, type WizardStep } from './WizardModal';

function Harness({
  onCancel = () => undefined,
  onFinish = () => undefined,
  steps,
}: {
  onCancel?: () => void;
  onFinish?: () => void | Promise<void>;
  steps: WizardStep[];
}) {
  const [current, setCurrent] = useState(0);
  return (
    <WizardModal
      open
      title="开通新租户"
      steps={steps}
      current={current}
      onChange={setCurrent}
      onCancel={onCancel}
      onFinish={onFinish}
    />
  );
}

describe('WizardModal', () => {
  it('渲染所有步骤名 + 默认聚焦第一步', () => {
    const steps: WizardStep[] = [
      { key: 'base', title: '基础信息', content: <div>第一步内容</div> },
      { key: 'plan', title: '套餐选择', content: <div>第二步内容</div> },
      { key: 'done', title: '确认', content: <div>第三步内容</div> },
    ];
    render(<Harness steps={steps} />);
    expect(screen.queryByText('基础信息')).not.toBeNull();
    expect(screen.queryByText('套餐选择')).not.toBeNull();
    expect(screen.queryByText('第一步内容')).not.toBeNull();
    expect(screen.queryByText('第二步内容')).toBeNull();
  });

  it('点击「下一步」推进步骤；首步无「上一步」', async () => {
    const steps: WizardStep[] = [
      { key: 'a', title: '一', content: <div>内容 A</div> },
      { key: 'b', title: '二', content: <div>内容 B</div> },
      { key: 'c', title: '三', content: <div>内容 C</div> },
    ];
    render(<Harness steps={steps} />);
    expect(screen.queryByRole('button', { name: '上一步' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /下一步/ }));
    await waitFor(() => {
      expect(screen.queryByText('内容 B')).not.toBeNull();
    });
    expect(screen.queryByRole('button', { name: '上一步' })).not.toBeNull();
  });

  it('末步显示「完成」并触发 onFinish', async () => {
    const onFinish = vi.fn();
    const steps: WizardStep[] = [
      { key: 'a', title: '一', content: <div /> },
      { key: 'b', title: '二', content: <div /> },
    ];
    function H() {
      const [current, setCurrent] = useState(1); // 直接进入末步
      return (
        <WizardModal
          open
          title="t"
          steps={steps}
          current={current}
          onChange={setCurrent}
          onCancel={() => undefined}
          onFinish={onFinish}
        />
      );
    }
    render(<H />);
    expect(screen.queryByRole('button', { name: /下一步/ })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /完成/ }));
    await waitFor(() => {
      expect(onFinish).toHaveBeenCalledOnce();
    });
  });

  it('onBeforeNext 返回 false 时阻止前进', async () => {
    const validator = vi.fn().mockResolvedValue(false);
    const steps: WizardStep[] = [
      { key: 'a', title: '一', content: <div>内容 A</div>, onBeforeNext: validator },
      { key: 'b', title: '二', content: <div>内容 B</div> },
    ];
    render(<Harness steps={steps} />);
    fireEvent.click(screen.getByRole('button', { name: /下一步/ }));
    await waitFor(() => {
      expect(validator).toHaveBeenCalled();
    });
    // 仍停在第一步
    expect(screen.queryByText('内容 A')).not.toBeNull();
    expect(screen.queryByText('内容 B')).toBeNull();
  });

  it('取消按钮触发 onCancel', () => {
    const onCancel = vi.fn();
    const steps: WizardStep[] = [
      { key: 'a', title: '一', content: <div /> },
      { key: 'b', title: '二', content: <div /> },
    ];
    render(<Harness steps={steps} onCancel={onCancel} />);
    // AntD 在 CJK 文本之间会插空格，用 regex 兜底
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    expect(onCancel).toHaveBeenCalled();
  });
});
