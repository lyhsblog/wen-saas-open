import { useState } from 'react';
import {
  Button,
  CenterPopup,
  DotLoading,
  Empty,
  ErrorBlock,
  InfiniteScroll,
  NavBar,
  PullToRefresh,
} from 'antd-mobile';
import { useNavigate } from 'react-router';
import { Card, Tag, type Tone } from '../../components/ui';
import { useCustomerDetail, useMyCustomersInfinite } from './crmQueries';

/** 阶段 → 展示色（statusLabel 由后端下发，前端只管 tone）。 */
const STATUS_TONE: Record<string, Tone> = {
  LEAD: 'neutral',
  FOLLOWING: 'brand',
  WON: 'success',
  LOST: 'danger',
};

const STATUS_CHIPS = [
  { value: undefined, label: '全部' },
  { value: 'LEAD', label: '潜在' },
  { value: 'FOLLOWING', label: '跟进中' },
  { value: 'WON', label: '赢单' },
  { value: 'LOST', label: '输单' },
] as const;

/**
 * 我的客户（crm_demo 业务模块的 H5 页面，经 lib/modules.ts 的 manifest 动态挂载，
 * 路由与后端 h5Pages() 声明的 `crm_demo/my-customers` 对齐）。复用共享包 crmCustomerApi
 * 的 /mine 端点（负责人恒为当前用户），入口在工作台「我的客户」业务卡片。
 */
export function MyCustomersPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [openCustomerId, setOpenCustomerId] = useState<string | undefined>(undefined);

  const listQ = useMyCustomersInfinite(status);
  const customers = listQ.data?.pages.flatMap((p) => p.records) ?? [];

  return (
    <div
      style={{
        minHeight: '100dvh',
        background: 'var(--bg)',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <NavBar onBack={() => void navigate(`/workspace`)} style={{ background: 'var(--bg-elev)' }}>
        我的客户
      </NavBar>

      {/* 阶段 chips */}
      <div
        style={{
          display: 'flex',
          gap: 8,
          padding: '10px 12px',
          overflowX: 'auto',
          background: 'var(--bg-elev)',
          borderTop: '1px solid var(--divider, #f5f5f5)',
        }}
      >
        {STATUS_CHIPS.map((c) => (
          <Chip key={c.label} active={status === c.value} onClick={() => setStatus(c.value)}>
            {c.label}
          </Chip>
        ))}
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: 12 }}>
        <PullToRefresh onRefresh={async () => void (await listQ.refetch())}>
          {listQ.isLoading ? (
            <div style={{ padding: '40px 0', textAlign: 'center', color: 'var(--brand)' }}>
              <DotLoading color="primary" />
            </div>
          ) : listQ.isError ? (
            <div style={{ padding: '40px 0', textAlign: 'center' }}>
              <ErrorBlock status="default" title="加载失败" description="请检查网络后重试" />
              <Button
                color="primary"
                size="small"
                style={{ marginTop: 12 }}
                onClick={() => void listQ.refetch()}
              >
                重试
              </Button>
            </div>
          ) : customers.length === 0 ? (
            <Empty description="暂无客户" style={{ padding: '48px 0' }} />
          ) : (
            <>
              <Card style={{ overflow: 'hidden' }}>
                {customers.map((c, i) => (
                  <div
                    key={c.id}
                    onClick={() => setOpenCustomerId(c.id)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      padding: '12px 16px',
                      borderTop: i === 0 ? 'none' : '1px solid var(--divider, #f5f5f5)',
                    }}
                  >
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div
                        style={{
                          fontSize: 14,
                          fontWeight: 500,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {c.name}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 2 }}>
                        {c.code ?? '无编号'}
                        {c.createdAt ? ` · ${c.createdAt.slice(0, 10)}` : ''}
                      </div>
                    </div>
                    <Tag tone={STATUS_TONE[c.status] ?? 'neutral'}>{c.statusLabel ?? c.status}</Tag>
                    <span style={{ color: 'var(--text-tertiary)', fontSize: 16 }}>›</span>
                  </div>
                ))}
              </Card>
              <InfiniteScroll
                loadMore={async () => {
                  await listQ.fetchNextPage();
                }}
                hasMore={listQ.hasNextPage}
              />
            </>
          )}
        </PullToRefresh>
      </div>

      <CustomerDetailPopup
        customerId={openCustomerId}
        onClose={() => setOpenCustomerId(undefined)}
      />
    </div>
  );
}

/** 简单详情弹层：只读展示，无编辑/流转操作（H5 端保持轻量）。 */
function CustomerDetailPopup({
  customerId,
  onClose,
}: {
  customerId: string | undefined;
  onClose: () => void;
}) {
  const detailQ = useCustomerDetail(customerId);
  const d = detailQ.data;

  return (
    <CenterPopup
      visible={!!customerId}
      onClose={onClose}
      onMaskClick={onClose}
      style={{ '--max-width': '340px' }}
    >
      <div style={{ padding: 20 }}>
        {detailQ.isError ? (
          <div style={{ padding: '12px 0', textAlign: 'center' }}>
            <ErrorBlock
              status="default"
              title="加载失败"
              description="客户详情获取失败，请重试。"
              style={{ '--image-height': '80px' }}
            />
            <Button
              color="primary"
              size="small"
              style={{ marginTop: 12 }}
              onClick={() => void detailQ.refetch()}
            >
              重试
            </Button>
          </div>
        ) : !d ? (
          <div style={{ padding: '24px 0', textAlign: 'center', color: 'var(--brand)' }}>
            <DotLoading color="primary" />
          </div>
        ) : (
          <>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
              <div style={{ flex: 1, fontSize: 17, fontWeight: 600, minWidth: 0 }}>{d.name}</div>
              <Tag tone={STATUS_TONE[d.status] ?? 'neutral'}>{d.statusLabel ?? d.status}</Tag>
            </div>
            <Field label="编号" value={d.code ?? '—'} />
            <Field label="负责人" value={d.ownerName ?? '—'} />
            <Field label="归属部门" value={d.ownerDeptName ?? '—'} />
            <Field label="创建时间" value={d.createdAt ? d.createdAt.slice(0, 10) : '—'} />
            <Button block style={{ marginTop: 16 }} onClick={onClose}>
              关闭
            </Button>
          </>
        )}
      </div>
    </CenterPopup>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{ display: 'flex', padding: '8px 0', borderTop: '1px solid var(--divider, #f5f5f5)' }}
    >
      <span style={{ width: 64, fontSize: 13, color: 'var(--text-tertiary)', flexShrink: 0 }}>
        {label}
      </span>
      <span style={{ flex: 1, fontSize: 13, wordBreak: 'break-all' }}>{value}</span>
    </div>
  );
}

function Chip({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      style={{
        padding: '6px 14px',
        fontSize: 12,
        fontWeight: active ? 600 : 500,
        whiteSpace: 'nowrap',
        border: '1px solid',
        borderColor: active ? 'var(--brand)' : 'var(--border, #ebedf0)',
        background: active ? 'var(--brand-bg)' : 'var(--bg-elev)',
        color: active ? 'var(--brand)' : 'var(--text-secondary)',
        borderRadius: 999,
      }}
    >
      {children}
    </button>
  );
}
