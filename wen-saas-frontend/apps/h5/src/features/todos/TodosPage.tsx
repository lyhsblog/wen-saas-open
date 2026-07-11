import { useEffect, useState } from 'react';
import {
  Button,
  CapsuleTabs,
  Dialog,
  DotLoading,
  Empty,
  ErrorBlock,
  Form,
  InfiniteScroll,
  Input,
  NavBar,
  Popup,
  PullToRefresh,
  Selector,
  TextArea,
  Toast,
} from 'antd-mobile';
import { useNavigate, useSearchParams } from 'react-router';
import type { SubmitTicketRequest, TicketPriority, TicketType } from '@wen-saas/api-client';
import { Card, Tag } from '../../components/ui';
import { formatRelative } from '../../lib/format';
import { useMyTicketsInfinite, useSubmitTicket } from './ticketQueries';
import {
  PRIORITY_LABEL,
  PRIORITY_TONE,
  STATUS_LABEL,
  STATUS_TABS,
  STATUS_TONE,
  TYPE_LABEL,
} from './ticketMeta';

export function TodosPage() {
  const navigate = useNavigate();
  // tab 放 URL query：进详情返回后保留当前筛选
  const [searchParams, setSearchParams] = useSearchParams();
  const rawTab = searchParams.get('tab') ?? 'all';
  const tab = STATUS_TABS.some((t) => t.key === rawTab) ? rawTab : 'all';
  const [submitOpen, setSubmitOpen] = useState(false);

  const status = STATUS_TABS.find((t) => t.key === tab)?.value;
  const listQ = useMyTicketsInfinite(status);
  const records = listQ.data?.pages.flatMap((p) => p.records) ?? [];

  return (
    <div
      style={{
        minHeight: '100%',
        background: 'var(--bg)',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <NavBar backArrow={false} style={{ background: 'var(--bg-elev)' }}>
        待办
      </NavBar>

      <CapsuleTabs
        activeKey={tab}
        onChange={(k) => setSearchParams(k === 'all' ? {} : { tab: k }, { replace: true })}
        style={{ background: 'var(--bg-elev)' }}
      >
        {STATUS_TABS.map((t) => (
          <CapsuleTabs.Tab title={t.label} key={t.key} />
        ))}
      </CapsuleTabs>

      <div style={{ flex: 1, overflow: 'hidden' }}>
        <PullToRefresh onRefresh={async () => void (await listQ.refetch())}>
          <div style={{ padding: 12 }}>
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
            ) : records.length === 0 ? (
              <Empty description="暂无工单" style={{ padding: '48px 0' }} />
            ) : (
              <>
                <Card style={{ overflow: 'hidden' }}>
                  {records.map((t, i) => (
                    <div
                      key={t.id}
                      onClick={() => void navigate(`/tickets/${t.id}`)}
                      style={{
                        padding: '14px 16px',
                        borderTop: i === 0 ? 'none' : '1px solid var(--divider, #f0f0f0)',
                      }}
                    >
                      <div
                        style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}
                      >
                        {(t.priority === 'HIGH' || t.priority === 'URGENT') && (
                          <Tag tone={PRIORITY_TONE[t.priority]}>{PRIORITY_LABEL[t.priority]}</Tag>
                        )}
                        <Tag tone="brand">{TYPE_LABEL[t.type]}</Tag>
                        <Tag tone={STATUS_TONE[t.status]}>{STATUS_LABEL[t.status]}</Tag>
                        <div style={{ flex: 1 }} />
                        <span style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
                          {formatRelative(t.lastActivityAt)}
                        </span>
                      </div>
                      <div style={{ fontSize: 14, fontWeight: 500, lineHeight: 1.5 }}>
                        {t.title}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 4 }}>
                        {t.ticketNo}
                      </div>
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
          </div>
        </PullToRefresh>
      </div>

      {/* 提交工单 FAB */}
      <div
        onClick={() => setSubmitOpen(true)}
        style={{
          position: 'fixed',
          right: 20,
          bottom: 'calc(64px + env(safe-area-inset-bottom))',
          width: 52,
          height: 52,
          borderRadius: 26,
          background: 'var(--brand)',
          color: '#fff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 28,
          boxShadow: '0 6px 16px rgba(43,95,255,0.4)',
          zIndex: 100,
        }}
      >
        +
      </div>

      {submitOpen && <SubmitTicketPopup onClose={() => setSubmitOpen(false)} />}
    </div>
  );
}

type SubmitForm = {
  title: string;
  type: TicketType[];
  priority: TicketPriority[];
  content: string;
};

function SubmitTicketPopup({ onClose }: { onClose: () => void }) {
  const [form] = Form.useForm<SubmitForm>();
  const submit = useSubmitTicket();
  // 挂载后下一帧置 true 触发入场动画；关闭走 visible=false → afterClose 通知父级卸载
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    const raf = requestAnimationFrame(() => setVisible(true));
    return () => cancelAnimationFrame(raf);
  }, []);

  // 已有输入内容时，关闭前确认放弃
  const requestClose = async () => {
    const v = form.getFieldsValue();
    const dirty = !!(v.title?.trim() || v.content?.trim());
    if (dirty) {
      const ok = await Dialog.confirm({ content: '放弃编辑？' });
      if (!ok) return;
    }
    setVisible(false);
  };

  const handleFinish = async (v: SubmitForm) => {
    const type = v.type[0];
    const priority = v.priority[0];
    if (!type || !priority) {
      Toast.show({ content: '请选择类型和优先级' });
      return;
    }
    const body: SubmitTicketRequest = {
      title: v.title.trim(),
      type,
      priority,
      content: v.content.trim(),
    };
    try {
      await submit.mutateAsync(body);
      Toast.show({ icon: 'success', content: '已提交' });
      setVisible(false);
    } catch {
      Toast.show({ content: '提交失败，请重试' });
    }
  };

  return (
    <Popup
      visible={visible}
      position="right"
      afterClose={onClose}
      bodyStyle={{
        width: '100vw',
        background: 'var(--bg)',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <NavBar onBack={() => void requestClose()} style={{ background: 'var(--bg-elev)' }}>
        提交工单
      </NavBar>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <Form
          form={form}
          layout="vertical"
          initialValues={{ type: ['CONSULT'], priority: ['NORMAL'] }}
          onFinish={(v: SubmitForm) => void handleFinish(v)}
          footer={
            <Button block type="submit" color="primary" size="large" loading={submit.isPending}>
              提交
            </Button>
          }
        >
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="简要描述问题" />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Selector
              options={(Object.keys(TYPE_LABEL) as TicketType[]).map((value) => ({
                label: TYPE_LABEL[value],
                value,
              }))}
            />
          </Form.Item>
          <Form.Item name="priority" label="优先级" rules={[{ required: true }]}>
            <Selector
              options={(['LOW', 'NORMAL', 'HIGH', 'URGENT'] as TicketPriority[]).map((value) => ({
                label: PRIORITY_LABEL[value],
                value,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="content"
            label="详情"
            rules={[{ required: true, message: '请输入详情' }]}
          >
            <TextArea placeholder="详细描述你的问题或需求" rows={4} maxLength={1000} showCount />
          </Form.Item>
        </Form>
      </div>
    </Popup>
  );
}
