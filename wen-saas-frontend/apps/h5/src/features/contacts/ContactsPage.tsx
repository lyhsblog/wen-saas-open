import { useEffect, useState } from 'react';
import {
  Button,
  CenterPopup,
  DotLoading,
  Empty,
  ErrorBlock,
  InfiniteScroll,
  NavBar,
  SearchBar,
  Toast,
} from 'antd-mobile';
import { useNavigate } from 'react-router';
import type { ContactReveal } from '@wen-saas/api-client';
import { memberApi } from '../../lib/api';
import { Card } from '../../components/ui';
import { useDeptTree, useMemberDetail, useMembersInfinite } from './contactQueries';

export function ContactsPage() {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  // 关键字 300ms 防抖后再进 queryKey，避免中文输入每键触发请求
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedKeyword(keyword.trim()), 300);
    return () => clearTimeout(timer);
  }, [keyword]);
  const [deptId, setDeptId] = useState<string | undefined>(undefined);
  const [openMemberId, setOpenMemberId] = useState<string | undefined>(undefined);

  const treeQ = useDeptTree();
  const topDepts = treeQ.data ?? [];
  const membersQ = useMembersInfinite(deptId, debouncedKeyword);
  const members = membersQ.data?.pages.flatMap((p) => p.items) ?? [];

  return (
    <div
      style={{
        minHeight: '100dvh',
        background: 'var(--bg)',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <NavBar
        onBack={() => void navigate(`/profile`)}
        style={{ background: 'var(--bg-elev)' }}
      >
        通讯录
      </NavBar>

      <div style={{ padding: '8px 12px', background: 'var(--bg-elev)' }}>
        <SearchBar placeholder="搜索成员 / 账号" value={keyword} onChange={setKeyword} />
      </div>

      {/* 部门 chips */}
      {topDepts.length > 0 && !keyword && (
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
          <Chip active={!deptId} onClick={() => setDeptId(undefined)}>
            全部
          </Chip>
          {topDepts.map((d) => (
            <Chip
              key={String(d.id)}
              active={deptId === String(d.id)}
              onClick={() => setDeptId(String(d.id))}
            >
              {d.name ?? '部门'}
            </Chip>
          ))}
        </div>
      )}

      <div style={{ flex: 1, overflowY: 'auto', padding: 12 }}>
        {membersQ.isLoading ? (
          <div style={{ padding: '40px 0', textAlign: 'center', color: 'var(--brand)' }}>
            <DotLoading color="primary" />
          </div>
        ) : members.length === 0 ? (
          <Empty description="没有匹配的成员" style={{ padding: '48px 0' }} />
        ) : (
          <Card style={{ overflow: 'hidden' }}>
            {members.map((m, i) => (
              <div
                key={m.id}
                onClick={() => setOpenMemberId(m.id)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: '12px 16px',
                  borderTop: i === 0 ? 'none' : '1px solid var(--divider, #f5f5f5)',
                }}
              >
                <Avatar name={m.realName || m.username} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 14, fontWeight: 500 }}>{m.realName || m.username}</div>
                  <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 2 }}>
                    {m.mainDeptName ?? ''}
                    {m.roleNames.length > 0 ? ` · ${m.roleNames.join('、')}` : ''}
                  </div>
                </div>
                <span style={{ color: 'var(--text-tertiary)', fontSize: 16 }}>›</span>
              </div>
            ))}
          </Card>
        )}
        {members.length > 0 && (
          <InfiniteScroll
            loadMore={async () => {
              await membersQ.fetchNextPage();
            }}
            hasMore={membersQ.hasNextPage}
          />
        )}
      </div>

      <MemberDetailPopup memberId={openMemberId} onClose={() => setOpenMemberId(undefined)} />
    </div>
  );
}

function MemberDetailPopup({
  memberId,
  onClose,
}: {
  memberId: string | undefined;
  onClose: () => void;
}) {
  const detailQ = useMemberDetail(memberId);
  const [reveal, setReveal] = useState<ContactReveal | null>(null);
  const [revealing, setRevealing] = useState(false);
  const d = detailQ.data;

  const doReveal = async () => {
    if (!memberId) return;
    setRevealing(true);
    try {
      setReveal(await memberApi.revealContact(memberId));
    } catch {
      Toast.show({ content: '获取联系方式失败' });
    } finally {
      setRevealing(false);
    }
  };

  return (
    <CenterPopup
      visible={!!memberId}
      onClose={onClose}
      onMaskClick={onClose}
      afterClose={() => setReveal(null)}
      style={{ '--max-width': '340px' }}
    >
      <div style={{ padding: 20 }}>
        {detailQ.isError ? (
          <div style={{ padding: '12px 0', textAlign: 'center' }}>
            <ErrorBlock
              status="default"
              title="加载失败"
              description="成员详情获取失败，请重试。"
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
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
              <Avatar name={d.realName || d.username} size={48} />
              <div>
                <div style={{ fontSize: 17, fontWeight: 600 }}>{d.realName || d.username}</div>
                <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 2 }}>
                  {d.mainDept?.deptName ?? ''}
                </div>
              </div>
            </div>
            <Field label="账号" value={d.username} />
            <Field label="角色" value={d.roles.map((r) => r.name).join('、') || '—'} />
            <Field label="手机" value={reveal ? (reveal.mobile ?? '—') : (d.mobile ?? '—')} />
            <Field label="邮箱" value={reveal ? (reveal.email ?? '—') : (d.email ?? '—')} />
            <div style={{ display: 'flex', gap: 10, marginTop: 16 }}>
              {!reveal ? (
                <Button
                  color="primary"
                  loading={revealing}
                  onClick={() => void doReveal()}
                  style={{ flex: 1 }}
                >
                  查看完整联系方式
                </Button>
              ) : (
                <>
                  {reveal.mobile && (
                    // tel: 拨号必须走 <a href>，Button 仅承担观感
                    <a
                      href={`tel:${reveal.mobile}`}
                      style={{ flex: 1, textDecoration: 'none' }}
                    >
                      <Button block color="primary">
                        拨打电话
                      </Button>
                    </a>
                  )}
                  <Button onClick={onClose} style={{ flex: 1 }}>
                    关闭
                  </Button>
                </>
              )}
            </div>
            <div
              style={{
                fontSize: 11,
                color: 'var(--text-tertiary)',
                marginTop: 12,
                lineHeight: 1.5,
              }}
            >
              查看完整联系方式将被记录审计。
            </div>
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
      <span style={{ width: 56, fontSize: 13, color: 'var(--text-tertiary)' }}>{label}</span>
      <span style={{ flex: 1, fontSize: 13, wordBreak: 'break-all' }}>{value}</span>
    </div>
  );
}

function Avatar({ name, size = 38 }: { name: string; size?: number }) {
  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: size / 2,
        background: 'var(--brand-bg)',
        color: 'var(--brand)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: size * 0.4,
        fontWeight: 600,
        flexShrink: 0,
      }}
    >
      {(name || '?').slice(0, 1).toUpperCase()}
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
