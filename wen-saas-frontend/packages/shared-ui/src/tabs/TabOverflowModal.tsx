import { useState } from 'react';
import { Button, Modal } from 'antd';
import { CountChip } from '../CountChip';
import { StatusPill } from '../StatusPill';
import { closableByLru } from './useTabsController';
import { formatRelativeFromNow } from './relativeTime';
import type { TabItem } from './types';

export type TabOverflowModalProps = {
  open: boolean;
  /** 当前已打开页签数。 */
  count: number;
  /** 建议上限。 */
  max: number;
  /** 全部页签；组件内部自行筛可关闭项并按 LRU 排序。 */
  tabs: TabItem[];
  /** 不希望在候选里出现的 key（通常是当前活跃 / 刚打开的页签）。 */
  excludeKey?: string;
  /** 关闭某页签（脏页签的二次确认由调用方在此回调内处理）。 */
  onCloseTab: (key: string) => void;
  /** 关闭弹窗（暂不处理）。 */
  onDismiss: () => void;
  now?: number;
};

/**
 * 页签达上限提示弹窗。不自动关闭任何页签，而是列出可关闭页签（标题 / 未保存标记 /「上次访问多久前」/
 * 关闭按钮），默认按 LRU（最久未访问优先）排序、首项标「推荐」，由用户决定关哪些。
 */
export function TabOverflowModal({
  open,
  count,
  max,
  tabs,
  excludeKey,
  onCloseTab,
  onDismiss,
  now: nowProp,
}: TabOverflowModalProps) {
  return (
    <Modal
      open={open}
      onCancel={onDismiss}
      title="标签页较多"
      footer={<Button onClick={onDismiss}>稍后处理</Button>}
      width={460}
      destroyOnHidden
    >
      <OverflowBody
        count={count}
        max={max}
        tabs={tabs}
        {...(excludeKey !== undefined ? { excludeKey } : {})}
        onCloseTab={onCloseTab}
        {...(nowProp !== undefined ? { now: nowProp } : {})}
      />
    </Modal>
  );
}

/**
 * 弹窗内容体。时间快照必须取在这里而非外层组件：外层随 AppShell 常驻（open 只切显隐），
 * 其 state 一经初始化便不再重置，快照会停留在首挂载时刻，导致所有「上次访问」相对于过去
 * 的时间点计算、一律显示「刚刚」。body 受 destroyOnHidden 管理，每次打开重建，惰性
 * useState 即「本次打开时刻」；也避免渲染期反复调用 Date.now。
 */
function OverflowBody({
  count,
  max,
  tabs,
  excludeKey,
  onCloseTab,
  now: nowProp,
}: Omit<TabOverflowModalProps, 'open' | 'onDismiss'>) {
  const [snapshot] = useState(() => Date.now());
  const now = nowProp ?? snapshot;
  const candidates = closableByLru(tabs).filter((t) => t.key !== excludeKey);

  return (
    <>
      <p style={{ marginTop: 0, color: 'var(--text-secondary)', fontSize: 13 }}>
        已打开 <b>{count}</b> 个标签（建议不超过 {max} 个）。关闭一些不常用的可保持流畅，下面按
        最久未访问排序：
      </p>
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 6,
          maxHeight: 360,
          overflowY: 'auto',
        }}
      >
        {candidates.map((t, i) => (
          <div
            key={t.key}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 10px',
              borderRadius: 'var(--r-md)',
              border: '1px solid var(--border)',
              background: 'var(--bg-muted)',
            }}
          >
            <div style={{ flex: 1, minWidth: 0 }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 13,
                  color: 'var(--text)',
                  fontWeight: 500,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }}
              >
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{t.title}</span>
                {i === 0 && <CountChip tone="brand">推荐</CountChip>}
                {t.dirty && <StatusPill badge={{ kind: 'warning', text: '未保存', color: '' }} />}
              </div>
              <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 2 }}>
                上次访问 {formatRelativeFromNow(t.lastActiveAt, now)}
              </div>
            </div>
            <Button size="small" danger={!!t.dirty} onClick={() => onCloseTab(t.key)}>
              关闭
            </Button>
          </div>
        ))}
        {candidates.length === 0 && (
          <div style={{ fontSize: 13, color: 'var(--text-tertiary)', padding: '8px 2px' }}>
            没有可关闭的标签。
          </div>
        )}
      </div>
    </>
  );
}
