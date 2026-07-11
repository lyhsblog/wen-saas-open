import { type ReactNode, useState } from 'react';
import { Button, Modal } from 'antd';
import { CheckOutlined } from '@ant-design/icons';

/**
 * 单步定义。`onBeforeNext` 返回 false / Promise<false> 阻止前进，
 * 业务方据此实现表单校验、字段冲突检查等。
 */
export type WizardStep = {
  key: string;
  title: string;
  content: ReactNode;
  onBeforeNext?: () => boolean | Promise<boolean>;
};

/** footer 自定义渲染时的上下文。 */
export type WizardFooterContext = {
  current: number;
  total: number;
  isFirst: boolean;
  isLast: boolean;
  goPrev: () => void;
  goNext: () => Promise<void>;
  cancel: () => void;
  finish: () => Promise<void>;
  finishing: boolean;
};

export type WizardModalProps = {
  open: boolean;
  title: string;
  steps: WizardStep[];
  current: number;
  onChange: (next: number) => void;
  onCancel: () => void;
  onFinish: () => void | Promise<void>;
  /** 完成步主按钮文案，默认 "完成"。 */
  finishText?: string;
  /** 完成中（mutation pending）；按钮 loading + 禁用。 */
  finishing?: boolean;
  width?: number;
  /** 自定义 footer，覆盖默认按钮组。 */
  footer?: (ctx: WizardFooterContext) => ReactNode;
};

/* ============ Stepper ============ */

type StepperItem = { title: string; index: number };

function Stepper({ steps, current }: { steps: StepperItem[]; current: number }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '4px 8px 24px',
        borderBottom: '1px solid var(--divider)',
        marginBottom: 24,
      }}
    >
      {steps.map((s, i) => {
        const active = i === current;
        const done = i < current;
        const numberColor = active || done ? '#fff' : 'var(--text-tertiary)';
        const numberBg = active ? 'var(--brand)' : done ? 'var(--success)' : 'var(--bg-muted)';
        const labelColor = active ? 'var(--brand)' : done ? 'var(--text)' : 'var(--text-tertiary)';
        return (
          <span key={s.title} style={{ display: 'contents' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, color: labelColor }}>
              <div
                style={{
                  width: 28,
                  height: 28,
                  borderRadius: 14,
                  background: numberBg,
                  color: numberColor,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 13,
                  fontWeight: 600,
                  flexShrink: 0,
                }}
              >
                {done ? <CheckOutlined style={{ fontSize: 14 }} /> : s.index + 1}
              </div>
              <span style={{ fontSize: 13, fontWeight: active ? 600 : 400, whiteSpace: 'nowrap' }}>
                {s.title}
              </span>
            </div>
            {i < steps.length - 1 && (
              <div
                style={{
                  flex: 1,
                  height: 1,
                  background: done ? 'var(--success)' : 'var(--divider)',
                  margin: '0 12px',
                }}
              />
            )}
          </span>
        );
      })}
    </div>
  );
}

/* ============ WizardModal ============ */

export function WizardModal({
  open,
  title,
  steps,
  current,
  onChange,
  onCancel,
  onFinish,
  finishText = '完成',
  finishing = false,
  width = 880,
  footer,
}: WizardModalProps) {
  const [stepBusy, setStepBusy] = useState(false);
  const total = steps.length;
  const isFirst = current === 0;
  const isLast = current === total - 1;
  const activeStep = steps[current];

  const goPrev = () => {
    if (!isFirst) onChange(current - 1);
  };

  const goNext = async () => {
    if (isLast) return;
    if (activeStep?.onBeforeNext) {
      setStepBusy(true);
      try {
        const ok = await activeStep.onBeforeNext();
        if (!ok) return;
      } finally {
        setStepBusy(false);
      }
    }
    onChange(current + 1);
  };

  const finish = async () => {
    if (activeStep?.onBeforeNext) {
      setStepBusy(true);
      try {
        const ok = await activeStep.onBeforeNext();
        if (!ok) return;
      } finally {
        setStepBusy(false);
      }
    }
    await onFinish();
  };

  const ctx: WizardFooterContext = {
    current,
    total,
    isFirst,
    isLast,
    goPrev,
    goNext,
    cancel: onCancel,
    finish,
    finishing,
  };

  const defaultFooter = (
    <>
      <Button onClick={onCancel} disabled={finishing}>
        取消
      </Button>
      <div style={{ flex: 1 }} />
      {!isFirst && (
        <Button onClick={goPrev} disabled={stepBusy || finishing}>
          上一步
        </Button>
      )}
      {!isLast && (
        <Button
          type="primary"
          loading={stepBusy}
          disabled={finishing}
          onClick={() => {
            void goNext();
          }}
        >
          下一步
        </Button>
      )}
      {isLast && (
        <Button
          type="primary"
          icon={<CheckOutlined />}
          loading={finishing || stepBusy}
          onClick={() => {
            void finish();
          }}
        >
          {finishText}
        </Button>
      )}
    </>
  );

  const stepperItems: StepperItem[] = steps.map((s, i) => ({ title: s.title, index: i }));

  return (
    <Modal
      open={open}
      title={title}
      onCancel={onCancel}
      width={width}
      destroyOnHidden
      mask={{ closable: false }}
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {footer ? footer(ctx) : defaultFooter}
        </div>
      }
    >
      <Stepper steps={stepperItems} current={current} />
      <div style={{ minHeight: 380 }}>{activeStep?.content}</div>
    </Modal>
  );
}
