import { useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  CountChip,
  NavRailDrawer,
  NavRailTrigger,
  ServerError5xxPage,
  shouldSkipRowNav,
  StatusPill,
  toneBadge,
  useIsNarrow,
  useTabStatus,
} from '@wen-saas/shared-ui';
import { App, Empty, Input, Popconfirm, Skeleton, Switch, Tooltip } from 'antd';
import {
  SearchOutlined,
  DownOutlined,
  RightOutlined,
  CheckOutlined,
  CloseOutlined,
  EditOutlined,
  LoadingOutlined,
  ReloadOutlined,
  BellOutlined,
  MailOutlined,
  MessageOutlined,
  WechatOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { useHasPermission, useIsReadonly } from '@wen-saas/permissions';
import {
  ApiError,
  describeApiError,
  type NotifyChannelMeta,
  type NotifyTemplateDetail,
  type NotifyTemplateListItem,
  type NotifyTemplatePriority,
} from '@wen-saas/api-client';
import {
  useNotifyChannels,
  useNotifyTemplateDetail,
  useNotifyTemplateList,
  useResetNotifyPref,
  useUpdateNotifyPref,
} from './notificationTemplateQueries';
import { Lbl, WaBtn, WaCard } from '../../components/wa';
import { moduleDisplayName } from '../../lib/moduleNames';

const STATION = 'STATION';

type ChannelStyle = { icon: ReactNode; desc: string };
const CHANNEL_STYLE: Record<string, ChannelStyle> = {
  STATION: { icon: <BellOutlined />, desc: '站内通知 · 保底通道，不可关闭' },
  EMAIL: { icon: <MailOutlined />, desc: '支持 HTML 富文本' },
  SMS: { icon: <MessageOutlined />, desc: '短信 · 建议 ≤ 70 字 · 计费' },
  IM: { icon: <WechatOutlined />, desc: '企业微信 / 钉钉 / 微信' },
};
function channelStyle(ch: string): ChannelStyle {
  return CHANNEL_STYLE[ch] ?? { icon: <BellOutlined />, desc: '' };
}

/** {var} 用示例值替换；缺失变量保留原占位符。 */
function renderPreview(text: string | null, vars: Record<string, string>): string {
  if (!text) return '';
  return text.replace(/\{([a-zA-Z0-9_]+)\}/g, (m, name: string) => vars[name] ?? m);
}

/**
 * 租户后台 · 系统管理 · 通知模板。
 * 租户侧：文案只读、直接内联展示预览（不提供编辑与「预览」按钮），仅定制 启停 / 渠道 / 优先级。
 */
export function NotificationTemplatePage() {
  const { modal } = App.useApp();
  // 窄屏（<1100px）时左侧模板列表收进 NavRailDrawer，顶部按钮展开
  const narrow = useIsNarrow();
  const [railOpen, setRailOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const [openMods, setOpenMods] = useState<Set<string> | null>(null);
  // 详情区处于编辑态且有未保存改动时为 true，切换模板前二次确认，防止静默丢弃定制
  const [detailDirty, setDetailDirty] = useState(false);
  const list = useNotifyTemplateList('');
  const channels = useNotifyChannels();

  const filtered = useMemo<NotifyTemplateListItem[]>(() => {
    const kw = keyword.trim().toLowerCase();
    const items = list.data ?? [];
    if (!kw) return items;
    return items.filter(
      (t) => t.templateCode.toLowerCase().includes(kw) || t.name.toLowerCase().includes(kw),
    );
  }, [list.data, keyword]);

  const grouped = useMemo(() => {
    const map = new Map<string, NotifyTemplateListItem[]>();
    for (const t of filtered) {
      const key = moduleDisplayName(t.moduleCode);
      const arr = map.get(key) ?? [];
      arr.push(t);
      map.set(key, arr);
    }
    return [...map.entries()];
  }, [filtered]);

  const allModKeys = useMemo(() => grouped.map(([m]) => m), [grouped]);
  const kw = keyword.trim();
  // 默认全部展开；搜索时强制展开
  const isModOpen = (m: string) => (kw ? true : (openMods ?? new Set(allModKeys)).has(m));
  const toggleMod = (m: string) =>
    setOpenMods((s) => {
      const base = s ?? new Set(allModKeys);
      const n = new Set(base);
      if (n.has(m)) n.delete(m);
      else n.add(m);
      return n;
    });
  const allOpen = (openMods ?? new Set(allModKeys)).size > 0;

  const effectiveSelected =
    selected && filtered.some((t) => t.templateCode === selected)
      ? selected
      : (filtered[0]?.templateCode ?? null);

  // 左：模板列表（宽屏 inline 定宽 280；窄屏放进 NavRailDrawer，选中后收起）
  const railNode = (
    <div
      style={{
        background: 'var(--bg-elev)',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        ...(narrow
          ? { width: '100%', height: '100%' }
          : { width: 280, borderRight: '1px solid var(--border)' }),
      }}
    >
      <div
        style={{
          padding: '14px 16px',
          borderBottom: '1px solid var(--divider)',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <div style={{ fontSize: 14, fontWeight: 600, flex: 1 }}>模板列表</div>
        <CountChip>
          {kw ? `${filtered.length} / ${(list.data ?? []).length}` : (list.data ?? []).length}
        </CountChip>
        <WaBtn
          size="sm"
          kind="ghost"
          onClick={() => setOpenMods(allOpen ? new Set() : new Set(allModKeys))}
        >
          {allOpen ? '全部收起' : '全部展开'}
        </WaBtn>
      </div>
      <div style={{ padding: '8px 12px', borderBottom: '1px solid var(--divider)' }}>
        <Input
          placeholder="搜索模板"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          prefix={<SearchOutlined style={{ color: 'var(--text-tertiary)', fontSize: 12 }} />}
          style={{ height: 30, fontSize: 12 }}
        />
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: 8 }}>
        {list.isLoading ? (
          <Skeleton active style={{ padding: 12 }} />
        ) : grouped.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无模板" />
        ) : (
          grouped.map(([mod, items]) => {
            const open = isModOpen(mod);
            const hasCustomized = items.some((t) => t.customized);
            return (
              <div key={mod} style={{ marginBottom: 8 }}>
                <div
                  onClick={() => toggleMod(mod)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '6px 8px',
                    fontSize: 11,
                    fontWeight: 600,
                    color: 'var(--text-tertiary)',
                    letterSpacing: 0.3,
                    cursor: 'pointer',
                    userSelect: 'none',
                    borderRadius: 'var(--r-sm)',
                  }}
                >
                  <span style={{ fontSize: 10 }}>
                    {open ? <DownOutlined /> : <RightOutlined />}
                  </span>
                  <span style={{ flex: 1 }}>{mod}</span>
                  <span>· {items.length}</span>
                  {!open && hasCustomized && (
                    <span
                      title="该分组下有已定制"
                      style={{
                        width: 5,
                        height: 5,
                        borderRadius: 3,
                        background: 'oklch(0.65 0.18 75)',
                      }}
                    />
                  )}
                </div>
                {open &&
                  items.map((t) => {
                    const active = t.templateCode === effectiveSelected;
                    return (
                      <div
                        key={t.templateCode}
                        onClick={() => {
                          if (shouldSkipRowNav()) return;
                          if (t.templateCode === effectiveSelected) {
                            setRailOpen(false); // 窄屏点已选中项也收起抽屉
                            return;
                          }
                          if (detailDirty) {
                            modal.confirm({
                              title: '放弃未保存的定制？',
                              content: '当前模板的定制尚未保存，切换后修改将丢失。',
                              okText: '放弃修改',
                              okButtonProps: { danger: true },
                              cancelText: '继续编辑',
                              onOk: () => {
                                setSelected(t.templateCode);
                                setRailOpen(false);
                              },
                            });
                            return;
                          }
                          setSelected(t.templateCode);
                          setRailOpen(false);
                        }}
                        style={{
                          padding: '8px 12px 8px 32px',
                          borderRadius: 'var(--r-md)',
                          cursor: 'pointer',
                          background: active ? 'var(--brand-bg)' : 'transparent',
                          color: active
                            ? 'var(--brand)'
                            : t.enabled
                              ? 'var(--text)'
                              : 'var(--text-tertiary)',
                          fontWeight: active ? 600 : 500,
                          fontSize: 13,
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6,
                        }}
                      >
                        <span style={{ flex: 1 }}>{t.name}</span>
                        {!t.enabled && (
                          <span style={{ fontSize: 10, color: 'var(--text-tertiary)' }}>停用</span>
                        )}
                        {t.customized && (
                          <span
                            title="已定制"
                            style={{
                              width: 6,
                              height: 6,
                              borderRadius: 3,
                              background: 'oklch(0.65 0.18 75)',
                            }}
                          />
                        )}
                      </div>
                    );
                  })}
              </div>
            );
          })
        )}
      </div>
    </div>
  );

  return (
    <div
      style={{
        flex: 1,
        display: 'flex',
        height: '100%',
        overflow: 'hidden',
        background: 'var(--bg)',
      }}
    >
      {narrow ? (
        <NavRailDrawer open={railOpen} onClose={() => setRailOpen(false)}>
          {railNode}
        </NavRailDrawer>
      ) : (
        railNode
      )}

      {/* 右：详情 */}
      <div
        style={{
          flex: 1,
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        {narrow && (
          <div
            style={{
              padding: '10px 16px',
              borderBottom: '1px solid var(--divider)',
              flexShrink: 0,
            }}
          >
            <NavRailTrigger label="模板列表" onClick={() => setRailOpen(true)} />
          </div>
        )}
        {effectiveSelected ? (
          <TemplateDetailPane
            code={effectiveSelected}
            channelMeta={channels.data ?? []}
            onDirtyChange={setDetailDirty}
          />
        ) : list.isError ? (
          <div style={{ padding: 24 }}>
            <ServerError5xxPage
              {...(list.error instanceof ApiError && list.error.requestId
                ? { requestId: list.error.requestId }
                : {})}
              onRetry={() => void list.refetch()}
            />
          </div>
        ) : (
          <Empty
            description={kw ? `未找到匹配「${kw}」的模板` : '选择左侧模板查看详情'}
            style={{ marginTop: 120 }}
          />
        )}
      </div>
    </div>
  );
}

function TemplateDetailPane({
  code,
  channelMeta,
  onDirtyChange,
}: {
  code: string;
  channelMeta: NotifyChannelMeta[];
  onDirtyChange: (dirty: boolean) => void;
}) {
  const { data, isLoading, isError, error, refetch } = useNotifyTemplateDetail(code);

  if (isError) {
    return (
      <div style={{ padding: 24 }}>
        <ServerError5xxPage
          {...(error instanceof ApiError && error.requestId ? { requestId: error.requestId } : {})}
          onRetry={() => void refetch()}
        />
      </div>
    );
  }
  if (isLoading || !data) {
    return (
      <div style={{ padding: 24 }}>
        <Skeleton active />
      </div>
    );
  }
  // key-remount：详情变化时整段重挂，规避 set-state-in-effect
  return (
    <TemplateDetailContent
      key={`${data.templateCode}:${data.updatedAt ?? ''}:${String(data.customized)}`}
      detail={data}
      channelMeta={channelMeta}
      onDirtyChange={onDirtyChange}
    />
  );
}

function TemplateDetailContent({
  detail,
  channelMeta,
  onDirtyChange,
}: {
  detail: NotifyTemplateDetail;
  channelMeta: NotifyChannelMeta[];
  onDirtyChange: (dirty: boolean) => void;
}) {
  const { message } = App.useApp();
  const canEdit = useHasPermission('notification_template.edit');
  const readonly = useIsReadonly();
  const updatePref = useUpdateNotifyPref();
  const resetPref = useResetNotifyPref();

  const supportedChannels = useMemo(() => Object.keys(detail.contents), [detail.contents]);
  const labelOf = useMemo(() => {
    const m = new Map(channelMeta.map((c) => [c.channel, c.label]));
    return (ch: string) => m.get(ch) ?? ch;
  }, [channelMeta]);
  const sampleVars = useMemo<Record<string, string>>(() => {
    const out: Record<string, string> = {};
    for (const v of detail.availableVars) out[v.name] = v.sampleValue ?? `{${v.name}}`;
    return out;
  }, [detail.availableVars]);

  const [editing, setEditing] = useState(false);
  const [enabled, setEnabled] = useState(detail.enabled);
  const [priority, setPriority] = useState<NotifyTemplatePriority>(detail.priority);
  const [channels, setChannels] = useState<string[]>(detail.channels);
  const [activeChannel, setActiveChannel] = useState<string>(
    () => detail.channels[0] ?? supportedChannels[0] ?? STATION,
  );

  const dirty =
    enabled !== detail.enabled ||
    priority !== detail.priority ||
    channels.slice().sort().join(',') !== detail.channels.slice().sort().join(',');

  // 编辑中有未保存改动：上抛给左侧列表拦截切换，并标记页签「未保存」（误关/刷新有二次确认）
  const editingDirty = editing && dirty;
  const { setDirty: setTabDirty } = useTabStatus();
  useEffect(() => {
    onDirtyChange(editingDirty);
    setTabDirty(editingDirty);
    return () => {
      onDirtyChange(false);
      setTabDirty(false);
    };
  }, [editingDirty, onDirtyChange, setTabDirty]);

  const presetCannotDisable = detail.preset;

  const toggleChannel = (ch: string) => {
    if (!editing || !enabled || ch === STATION) return;
    setChannels((prev) => {
      const next = new Set(prev);
      if (next.has(ch)) next.delete(ch);
      else next.add(ch);
      next.add(STATION); // 站内信保底，恒选
      return supportedChannels.filter((c) => next.has(c));
    });
  };

  const cancelEdit = () => {
    setEnabled(detail.enabled);
    setPriority(detail.priority);
    setChannels(detail.channels);
    setEditing(false);
  };

  const onSave = () =>
    updatePref.mutate(
      { code: detail.templateCode, body: { enabled, channels, priority } },
      {
        onSuccess: () => {
          message.success('已保存定制');
          setEditing(false);
        },
        onError: (e) => {
          message.error(describeApiError(e, '保存失败'));
        },
      },
    );
  const onReset = () =>
    resetPref.mutate(detail.templateCode, {
      onSuccess: () => {
        message.success('已清除定制，恢复为平台默认');
      },
      onError: (e) => {
        message.error(describeApiError(e, '恢复失败'));
      },
    });

  const activeContent = detail.contents[activeChannel];
  const activeOn = activeChannel === STATION || channels.includes(activeChannel);

  return (
    <>
      {/* Header */}
      <div style={{ padding: '20px 24px 16px', borderBottom: '1px solid var(--divider)' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            marginBottom: 8,
            flexWrap: 'wrap',
          }}
        >
          <div style={{ fontSize: 20, fontWeight: 600 }}>{detail.name}</div>
          {detail.preset && <CountChip tone="brand">系统预置</CountChip>}
          {detail.customized && <StatusPill badge={toneBadge('warning', '已定制')} />}
          {!detail.enabled && <StatusPill badge={toneBadge('neutral', '已停用')} />}
          <span
            style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-tertiary)' }}
          >
            {detail.templateCode}
          </span>
          <div className="wa-spacer" />
          {canEdit && !editing && (
            <>
              {detail.customized && (
                <Popconfirm
                  placement="bottomRight"
                  title="恢复为平台默认"
                  description={
                    <span style={{ display: 'inline-block', maxWidth: 260 }}>
                      将清除本模板的全部定制（启停 / 渠道 /
                      优先级），恢复为平台维护的默认设置，且不可撤销。
                    </span>
                  }
                  okText="恢复默认"
                  okButtonProps={{ danger: true, loading: resetPref.isPending }}
                  cancelText="取消"
                  onConfirm={onReset}
                >
                  <WaBtn size="sm" kind="ghost" icon={<ReloadOutlined />} disabled={readonly}>
                    恢复平台默认
                  </WaBtn>
                </Popconfirm>
              )}
              <WaBtn
                kind="primary"
                size="sm"
                icon={<EditOutlined />}
                disabled={readonly}
                onClick={() => setEditing(true)}
              >
                编辑定制
              </WaBtn>
            </>
          )}
          {canEdit && editing && (
            <>
              <WaBtn size="sm" icon={<CloseOutlined />} onClick={cancelEdit}>
                取消
              </WaBtn>
              <WaBtn
                kind="primary"
                size="sm"
                icon={updatePref.isPending ? <LoadingOutlined /> : <CheckOutlined />}
                disabled={!dirty || updatePref.isPending || readonly}
                onClick={onSave}
              >
                {updatePref.isPending ? '保存中…' : '保存定制'}
              </WaBtn>
            </>
          )}
        </div>
        {detail.description && (
          <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{detail.description}</div>
        )}
      </div>

      <div
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: 24,
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
      >
        {!detail.defaultActive && (
          <div
            style={{
              padding: '10px 14px',
              background: 'var(--warning-bg)',
              border: '1px solid #FED7AA',
              borderRadius: 'var(--r-md)',
              fontSize: 13,
              color: 'var(--warning)',
            }}
          >
            <InfoCircleOutlined style={{ marginRight: 6 }} />
            该模板已被平台全局停用，当前不会发送。
          </div>
        )}

        {/* 基本信息 */}
        <WaCard title="基本信息">
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'minmax(120px, 160px) 1fr',
              rowGap: 14,
              columnGap: 24,
              fontSize: 13,
            }}
          >
            <Lbl>来源模块</Lbl>
            <div style={{ color: 'var(--text-secondary)' }}>
              {moduleDisplayName(detail.moduleCode)}
            </div>

            <Lbl>可用变量</Lbl>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {detail.availableVars.length === 0 ? (
                <span style={{ color: 'var(--text-tertiary)' }}>无</span>
              ) : (
                detail.availableVars.map((v) => (
                  <span
                    key={v.name}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 4,
                      padding: '2px 8px',
                      borderRadius: 4,
                      background: 'var(--bg-muted)',
                      fontFamily: 'var(--font-mono)',
                      fontSize: 12,
                    }}
                  >
                    <span style={{ color: 'var(--brand)' }}>{`{${v.name}}`}</span>
                    <span style={{ color: 'var(--text-tertiary)' }}>
                      例：{v.sampleValue ?? '—'}
                    </span>
                  </span>
                ))
              )}
            </div>

            <Lbl>优先级</Lbl>
            {editing ? (
              <div style={{ display: 'flex', gap: 6 }}>
                {(
                  [
                    { k: 'NORMAL', l: '普通' },
                    { k: 'IMPORTANT', l: '重要' },
                  ] as const
                ).map((o) => {
                  const on = priority === o.k;
                  return (
                    <button
                      key={o.k}
                      type="button"
                      disabled={!enabled}
                      onClick={() => setPriority(o.k)}
                      style={{
                        padding: '4px 14px',
                        fontSize: 12,
                        fontWeight: on ? 600 : 500,
                        border: '1px solid',
                        borderColor: on ? 'var(--brand)' : 'var(--border)',
                        background: on ? 'var(--brand-bg)' : '#fff',
                        color: on ? 'var(--brand)' : 'var(--text-secondary)',
                        borderRadius: 'var(--r-pill)',
                        cursor: !enabled ? 'not-allowed' : 'pointer',
                      }}
                    >
                      {o.l}
                    </button>
                  );
                })}
              </div>
            ) : (
              <div>
                <StatusPill
                  badge={toneBadge(
                    priority === 'IMPORTANT' ? 'warning' : 'neutral',
                    priority === 'IMPORTANT' ? '重要' : '普通',
                  )}
                />
              </div>
            )}

            <Lbl>状态</Lbl>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              {editing ? (
                <Tooltip title={presetCannotDisable ? '系统预置模板不可停用' : undefined}>
                  <Switch
                    size="small"
                    checked={enabled}
                    disabled={presetCannotDisable}
                    onChange={(v) => setEnabled(v)}
                  />
                </Tooltip>
              ) : (
                <StatusPill
                  badge={toneBadge(enabled ? 'success' : 'neutral', enabled ? '已启用' : '已停用')}
                />
              )}
              <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                停用后该模板不再发送任何渠道（含站内信）
              </span>
            </div>
          </div>
        </WaCard>

        {/* 渠道与文案（只读预览） */}
        <WaCard
          title="渠道与文案"
          extra={
            <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
              文案由平台维护 · 此处只读预览
            </span>
          }
        >
          {/* 渠道开关 */}
          <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
            {supportedChannels.map((ch) => {
              const cs = channelStyle(ch);
              const on = ch === STATION || channels.includes(ch);
              const active = ch === activeChannel;
              const locked = ch === STATION;
              return (
                <button
                  key={ch}
                  type="button"
                  onClick={() => setActiveChannel(ch)}
                  style={{
                    flex: '1 1 160px',
                    padding: '10px 12px',
                    textAlign: 'left',
                    border: '1.5px solid',
                    borderColor: active ? 'var(--brand)' : 'var(--border)',
                    background: active ? 'var(--brand-bg)' : '#fff',
                    borderRadius: 'var(--r-md)',
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 4,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span
                      style={{ color: on ? 'var(--brand)' : 'var(--text-tertiary)', fontSize: 13 }}
                    >
                      {cs.icon}
                    </span>
                    <span style={{ fontSize: 13, fontWeight: 600 }}>{labelOf(ch)}</span>
                    {locked ? (
                      <span
                        style={{
                          marginLeft: 'auto',
                          fontSize: 10,
                          padding: '1px 6px',
                          borderRadius: 3,
                          background: 'var(--bg-muted)',
                          color: 'var(--text-tertiary)',
                          fontWeight: 600,
                        }}
                      >
                        保底
                      </span>
                    ) : editing ? (
                      <span
                        onClick={(e) => e.stopPropagation()}
                        style={{ marginLeft: 'auto', display: 'inline-flex' }}
                      >
                        <Tooltip title={!enabled ? '模板停用时不可调整渠道' : undefined}>
                          <Switch
                            size="small"
                            checked={on}
                            disabled={!enabled}
                            onChange={() => toggleChannel(ch)}
                          />
                        </Tooltip>
                      </span>
                    ) : (
                      <span
                        style={{
                          marginLeft: 'auto',
                          fontSize: 10,
                          fontWeight: 600,
                          padding: '1px 6px',
                          borderRadius: 3,
                          background: on ? 'var(--brand-bg)' : 'var(--bg-muted)',
                          color: on ? 'var(--brand)' : 'var(--text-tertiary)',
                        }}
                      >
                        {on ? '已开启' : '已关闭'}
                      </span>
                    )}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--text-tertiary)', lineHeight: 1.4 }}>
                    {cs.desc}
                  </div>
                </button>
              );
            })}
          </div>

          {/* 当前渠道预览（只读，示例值替换） */}
          {!activeOn ? (
            <div
              style={{
                padding: '30px 14px',
                background: 'var(--bg)',
                border: '1px dashed var(--border)',
                borderRadius: 'var(--r-md)',
                textAlign: 'center',
                fontSize: 13,
                color: 'var(--text-tertiary)',
              }}
            >
              <InfoCircleOutlined style={{ marginRight: 6 }} />
              {labelOf(activeChannel)}渠道已关闭 · 开启后将按以下文案发送
            </div>
          ) : (
            <>
              <div
                style={{
                  fontSize: 11,
                  color: 'var(--text-tertiary)',
                  fontWeight: 600,
                  marginBottom: 8,
                  letterSpacing: 0.3,
                }}
              >
                预览 · 用示例值替换 {`{变量}`}
              </div>
              <ChannelPreview channel={activeChannel} content={activeContent} vars={sampleVars} />
            </>
          )}
        </WaCard>

        {detail.customized && (
          <div
            style={{
              padding: '12px 14px',
              background: 'var(--warning-bg)',
              border: '1px solid #FED7AA',
              borderRadius: 'var(--r-md)',
              fontSize: 12,
              color: 'var(--warning)',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
          >
            <InfoCircleOutlined />
            <div style={{ flex: 1, lineHeight: 1.6 }}>
              该模板已定制（启停 / 渠道 / 优先级），平台升级默认模板时<b>会保留您的定制</b>。
              如想回到平台最新默认，请点右上「恢复默认」。
            </div>
          </div>
        )}
      </div>
    </>
  );
}

function ChannelPreview({
  channel,
  content,
  vars,
}: {
  channel: string;
  content: { title: string | null; body: string | null } | undefined;
  vars: Record<string, string>;
}) {
  const title = renderPreview(content?.title ?? null, vars);
  const body = renderPreview(content?.body ?? null, vars);

  if (channel === 'EMAIL') {
    return (
      <div
        style={{
          background: '#fff',
          border: '1px solid var(--border)',
          borderRadius: 'var(--r-md)',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: '10px 14px',
            background: 'var(--bg-muted)',
            borderBottom: '1px solid var(--border)',
            fontSize: 12,
            color: 'var(--text-secondary)',
          }}
        >
          收件人 <b>主管理员</b> · 来自 noreply@example.com
        </div>
        <div style={{ padding: 14 }}>
          {title && <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 8 }}>{title}</div>}
          <div
            style={{
              fontSize: 13,
              color: 'var(--text-secondary)',
              lineHeight: 1.7,
              whiteSpace: 'pre-wrap',
            }}
          >
            {body}
          </div>
        </div>
      </div>
    );
  }
  if (channel === 'SMS') {
    return (
      <div
        style={{
          padding: '14px 16px',
          background: 'oklch(0.96 0.04 145)',
          border: '1px solid oklch(0.85 0.08 145)',
          borderRadius: 18,
          fontSize: 13,
          lineHeight: 1.6,
          maxWidth: 320,
          whiteSpace: 'pre-wrap',
        }}
      >
        {body}
        <div style={{ fontSize: 10, color: 'var(--text-tertiary)', marginTop: 6 }}>
          共 {body.length} 字 · {Math.max(1, Math.ceil(body.length / 70))} 条
        </div>
      </div>
    );
  }
  if (channel === 'IM') {
    return (
      <div
        style={{
          padding: '10px 14px',
          background: 'oklch(0.96 0.04 200)',
          border: '1px solid oklch(0.85 0.08 200)',
          borderRadius: 12,
          fontSize: 13,
          lineHeight: 1.6,
          maxWidth: 360,
          whiteSpace: 'pre-wrap',
        }}
      >
        <div
          style={{ fontSize: 11, color: 'oklch(0.45 0.16 200)', fontWeight: 600, marginBottom: 4 }}
        >
          企业微信 / 钉钉 / 微信 · 简短文字 + 跳转链接
        </div>
        {body}
      </div>
    );
  }
  // 站内信 / 默认
  return (
    <div
      style={{
        padding: '12px 14px',
        background: 'var(--bg)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-md)',
        display: 'flex',
        gap: 10,
      }}
    >
      <div
        style={{
          width: 32,
          height: 32,
          borderRadius: 8,
          background: 'var(--brand-bg)',
          color: 'var(--brand)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        <BellOutlined />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        {title && <div style={{ fontSize: 13, fontWeight: 600 }}>{title}</div>}
        <div
          style={{
            fontSize: 12,
            color: 'var(--text-secondary)',
            marginTop: 4,
            lineHeight: 1.5,
            whiteSpace: 'pre-wrap',
          }}
        >
          {body}
        </div>
      </div>
    </div>
  );
}
