import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';
import { App, DatePicker, Modal, Popconfirm, Skeleton, Switch, Tooltip } from 'antd';
import dayjs from 'dayjs';
import {
  BuildOutlined,
  GlobalOutlined,
  KeyOutlined,
  LinkOutlined,
  SafetyOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  CountChip,
  formatDateTime,
  NavItem,
  NavRailDrawer,
  NavRailTrigger,
  RowTextAction,
  StatusPill,
  toneBadge,
  useIsNarrow,
  useTabStatus,
} from '@wen-saas/shared-ui';
import {
  describeApiError,
  stepUpApi,
  type OAuthBinding,
  type TenantProfileBasic,
} from '@wen-saas/api-client';
import { WaBtn } from '../../components/wa';
import { GENDER_OPTIONS, OAUTH_PROVIDERS, genderLabel, profileApi } from './profileApi';
import {
  useBasic,
  useInvalidateProfile,
  useOAuthBindings,
  useSecurity,
  useSessions,
  useWork,
} from './profileQueries';
import { ChangePasswordModal } from './ChangePasswordModal';
import { MfaEnableModal } from './MfaEnableModal';
import { MobileChangeModal } from './MobileChangeModal';
import { EmailChangeModal } from './EmailChangeModal';
import { AvatarUploader } from './AvatarUploader';
import { StepUpGate } from './StepUpGate';
import { TotpBindModal } from './TotpBindModal';
import { http } from '../../lib/http';

// 暂无短信认证资源：改/登记手机号与短信验证通道统一置「暂未开放」；接入后翻开关即恢复
const SMS_READY = false;
const SMS_CLOSED_TAG = '短信服务未接入';

const stepUp = stepUpApi(http);

type SectionKey = 'basic' | 'work' | 'security' | 'oauth' | 'devices';

const NAV: { v: SectionKey; label: string; icon: React.ReactNode }[] = [
  { v: 'basic', label: '基本信息', icon: <UserOutlined /> },
  { v: 'work', label: '组织与角色', icon: <BuildOutlined /> },
  { v: 'security', label: '安全设置', icon: <SafetyOutlined /> },
  { v: 'oauth', label: '第三方账号', icon: <LinkOutlined /> },
  { v: 'devices', label: '登录设备', icon: <GlobalOutlined /> },
];

function initials(name?: string | null): string {
  if (!name) return '👤';
  // 取首字符（中文姓 / 英文首字母），避免「UI测试管理员」截成无意义的末尾「理员」
  return name.length > 2 ? name.slice(0, 2) : name;
}

/** 租户后台 · 个人中心。左侧个人卡 + 锚点导航（窄屏收进 NavRailDrawer），右侧分段。 */
export function ProfilePage() {
  const { modal } = App.useApp();
  // 窄屏（<1100px）时左栏收进 NavRailDrawer，标题上方按钮展开
  const narrow = useIsNarrow();
  const [railOpen, setRailOpen] = useState(false);
  // 支持 ?section= 深链（右上角用户卡「修改密码」→ 安全设置）。
  const [params] = useSearchParams();
  const [section, setSection] = useState<SectionKey>(() => {
    const s = params.get('section');
    return NAV.some((n) => n.v === s) ? (s as SectionKey) : 'basic';
  });
  const basic = useBasic();
  // 「基本信息」编辑中有未保存改动时为 true，切换左侧 section 前二次确认，防止静默丢弃
  const [basicDirty, setBasicDirty] = useState(false);

  // 切换成功才收起抽屉；脏态确认里点「继续编辑」则保持打开（宽屏时收起是 no-op）
  const applySection = (next: SectionKey) => {
    setSection(next);
    setRailOpen(false);
  };

  const switchSection = (next: SectionKey) => {
    if (next === section) {
      setRailOpen(false);
      return;
    }
    if (!basicDirty) {
      applySection(next);
      return;
    }
    modal.confirm({
      title: '放弃未保存的修改？',
      content: '「基本信息」的修改尚未保存，切换后将丢失。',
      okText: '放弃修改',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: () => applySection(next),
    });
  };

  const railNode = (
    <aside
      style={{
        background: 'var(--bg-elev)',
        flexShrink: 0,
        padding: 20,
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        overflow: 'auto',
        ...(narrow
          ? { width: '100%', height: '100%' }
          : { width: 260, borderRight: '1px solid var(--border)' }),
      }}
    >
      <PersonCard basic={basic.data} loading={basic.isLoading} />
      <nav style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {NAV.map((n) => (
          <NavItem
            key={n.v}
            active={section === n.v}
            icon={n.icon}
            onClick={() => switchSection(n.v)}
          >
            {n.label}
          </NavItem>
        ))}
      </nav>
    </aside>
  );

  return (
    <div style={{ display: 'flex', height: '100%', background: 'var(--bg)' }}>
      {narrow ? (
        <NavRailDrawer open={railOpen} onClose={() => setRailOpen(false)}>
          {railNode}
        </NavRailDrawer>
      ) : (
        railNode
      )}

      <main style={{ flex: 1, overflow: 'auto' }}>
        <div style={{ padding: 24, maxWidth: 960, margin: '0 auto' }}>
          {narrow && (
            <NavRailTrigger
              label="导航"
              onClick={() => setRailOpen(true)}
              style={{ marginBottom: 12 }}
            />
          )}
          <div style={{ marginBottom: 20 }}>
            <div style={{ fontSize: 20, fontWeight: 600 }}>个人中心</div>
            <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 6 }}>
              管理你的账户、安全、第三方账号与登录设备
            </div>
          </div>

          {section === 'basic' && <BasicSection onDirtyChange={setBasicDirty} />}
          {section === 'work' && <WorkSection />}
          {section === 'security' && <SecuritySection />}
          {section === 'oauth' && <OAuthSection />}
          {section === 'devices' && <DevicesSection />}
          <div style={{ height: 40 }} />
        </div>
      </main>
    </div>
  );
}

// ===================== 共用：节 + 行 =====================

function Section({
  title,
  hint,
  extra,
  children,
}: {
  title: string;
  hint?: string;
  extra?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section
      style={{
        background: 'var(--bg-elev)',
        border: '1px solid var(--border)',
        borderRadius: 12,
        padding: 24,
        marginBottom: 16,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-end',
          gap: 10,
          marginBottom: 14,
          paddingBottom: 14,
          borderBottom: '1px solid var(--divider)',
        }}
      >
        <div>
          <div style={{ fontSize: 16, fontWeight: 600 }}>{title}</div>
          {hint && (
            <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>{hint}</div>
          )}
        </div>
        <div className="wa-spacer" />
        {extra}
      </div>
      {children}
    </section>
  );
}

function Row({
  label,
  value,
  hint,
  action,
}: {
  label: string;
  value: React.ReactNode;
  hint?: string;
  action?: React.ReactNode;
}) {
  return (
    <div
      style={{
        padding: '14px 0',
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        borderBottom: '1px solid var(--divider)',
      }}
    >
      <div style={{ width: 130, fontSize: 13, color: 'var(--text-tertiary)', flexShrink: 0 }}>
        {label}
      </div>
      <div style={{ flex: 1, fontSize: 14 }}>
        {value}
        {hint && (
          <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 4 }}>{hint}</div>
        )}
      </div>
      {action}
    </div>
  );
}

// ===================== 个人卡 =====================

function PersonCard({
  basic,
  loading,
}: {
  basic: TenantProfileBasic | undefined;
  loading: boolean;
}) {
  return (
    <div style={{ textAlign: 'center', padding: '12px 0 16px' }}>
      <AvatarUploader avatarUrl={basic?.avatarUrl} fallback={initials(basic?.realName)} size={80} />
      <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginBottom: 4 }}>
        点击头像更换 · JPG/PNG · ≤ 1MB
      </div>
      {loading ? (
        <Skeleton active paragraph={false} />
      ) : (
        <>
          <div style={{ fontSize: 16, fontWeight: 600 }}>
            {basic?.realName ?? basic?.username ?? '—'}
          </div>
          <div
            style={{
              marginTop: 14,
              padding: '8px 10px',
              background: 'var(--bg-muted)',
              borderRadius: 8,
              fontSize: 11,
              color: 'var(--text-tertiary)',
              display: 'flex',
              justifyContent: 'space-between',
            }}
          >
            <span>账号</span>
            <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-secondary)' }}>
              {basic?.username ?? '—'}
            </span>
          </div>
        </>
      )}
    </div>
  );
}

// ===================== 基本信息 =====================

function BasicSection({ onDirtyChange }: { onDirtyChange: (dirty: boolean) => void }) {
  const { message } = App.useApp();
  const basic = useBasic();
  const invalidate = useInvalidateProfile();
  const [edit, setEdit] = useState(false);
  const [mobileModal, setMobileModal] = useState(false);
  const [emailModal, setEmailModal] = useState(false);

  // 编辑态表单（key-remount 重置，避免 set-state-in-effect）。
  const [form, setForm] = useState<{
    realName: string;
    gender: number;
    birthday: string | null;
  }>({
    realName: '',
    gender: 0,
    birthday: null,
  });

  const startEdit = () => {
    const d = basic.data;
    setForm({
      realName: d?.realName ?? '',
      gender: d?.gender ?? 0,
      birthday: d?.birthday ?? null,
    });
    setEdit(true);
  };

  const save = useMutation({
    // 头像走独立入口即传即存（O5-1），不带 avatarFileId；后端 null 视为未修改、不会清空头像。
    mutationFn: () =>
      profileApi.updateProfile({
        realName: form.realName.trim(),
        gender: form.gender,
        birthday: form.birthday,
      }),
    onSuccess: async () => {
      message.success('已保存');
      setEdit(false);
      await invalidate();
    },
    onError: (e) => message.error(describeApiError(e, '保存失败')),
  });

  // 可自助编辑的仅性别/生日；与服务端当前值比对，无改动不算脏
  const isDirty =
    edit &&
    basic.data != null &&
    (form.gender !== (basic.data.gender ?? 0) ||
      (form.birthday ?? null) !== (basic.data.birthday ?? null));

  // 编辑中有未保存改动：上抛给左侧导航拦截切换，并标记页签「未保存」（误关/刷新有二次确认）
  const { setDirty: setTabDirty } = useTabStatus();
  useEffect(() => {
    onDirtyChange(isDirty);
    setTabDirty(isDirty);
    return () => {
      onDirtyChange(false);
      setTabDirty(false);
    };
  }, [isDirty, onDirtyChange, setTabDirty]);

  if (basic.isLoading || !basic.data) return <Skeleton active />;
  const d = basic.data;

  return (
    <Section
      title="基本信息"
      hint="用于内部协同、消息推送、登录验证。手机/邮箱默认对租户内成员脱敏。"
      extra={
        !edit ? (
          <WaBtn size="sm" onClick={startEdit}>
            编辑
          </WaBtn>
        ) : (
          <div style={{ display: 'flex', gap: 8 }}>
            <WaBtn size="sm" onClick={() => setEdit(false)}>
              取消
            </WaBtn>
            <WaBtn
              size="sm"
              kind="primary"
              disabled={!isDirty || save.isPending}
              onClick={() => save.mutate()}
            >
              {save.isPending ? '保存中…' : '保存'}
            </WaBtn>
          </div>
        )
      }
    >
      <Row
        label="姓名"
        value={<b>{d.realName ?? '—'}</b>}
        hint="姓名由管理员在「用户管理」处维护，不可自助修改"
      />
      <Row
        label="账号"
        value={<code style={{ fontFamily: 'var(--font-mono)' }}>{d.username}</code>}
        hint="账号由主管理员开通时设定，不可自助修改"
      />
      {d.empNo && (
        <Row
          label="员工编号"
          value={<code style={{ fontFamily: 'var(--font-mono)' }}>{d.empNo}</code>}
        />
      )}
      <Row
        label="手机号"
        value={
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontFamily: 'var(--font-mono)' }}>{d.mobile ?? '—'}</span>
            {d.mobile && <StatusPill badge={toneBadge('success', '已验证')} />}
          </div>
        }
        hint="作为登录账号 / 短信验证码 / 二步验证的核心通道"
        action={
          <Tooltip title={SMS_READY ? undefined : '短信服务未接入，暂不可修改'}>
            <span>
              <WaBtn size="sm" disabled={!SMS_READY} onClick={() => setMobileModal(true)}>
                {d.mobile ? '修改手机号' : '登记手机号'}
              </WaBtn>
            </span>
          </Tooltip>
        }
      />
      <Row
        label="邮箱"
        value={
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontFamily: 'var(--font-mono)' }}>{d.email ?? '—'}</span>
            {d.email && <StatusPill badge={toneBadge('success', '已验证')} />}
          </div>
        }
        hint="作为登录 / 找回 / 二步验证的通道之一"
        action={
          <WaBtn size="sm" onClick={() => setEmailModal(true)}>
            {d.email ? '修改邮箱' : '登记邮箱'}
          </WaBtn>
        }
      />
      <Row
        label="性别"
        value={
          edit ? (
            <div style={{ display: 'flex', gap: 12, fontSize: 13 }}>
              {GENDER_OPTIONS.map((g) => (
                <label
                  key={g.value}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 4,
                    cursor: 'pointer',
                  }}
                >
                  <input
                    type="radio"
                    checked={form.gender === g.value}
                    onChange={() => setForm((f) => ({ ...f, gender: g.value }))}
                  />{' '}
                  {g.label}
                </label>
              ))}
            </div>
          ) : (
            genderLabel(d.gender)
          )
        }
      />
      <Row
        label="生日"
        value={
          edit ? (
            <DatePicker
              style={{ maxWidth: 200, width: '100%' }}
              placeholder="请选择生日"
              value={form.birthday ? dayjs(form.birthday) : null}
              onChange={(_, dateStr) => setForm((f) => ({ ...f, birthday: dateStr || null }))}
            />
          ) : (
            (d.birthday ?? '—')
          )
        }
      />
      <Row
        label="头像"
        value={
          <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
            点击左侧资料卡头像即可更换（即传即存，支持 JPG / PNG · ≤ 1MB）
          </span>
        }
      />

      {mobileModal && (
        <MobileChangeModal
          hasMobile={!!d.mobile}
          onClose={() => setMobileModal(false)}
          onDone={() => {
            void invalidate();
          }}
        />
      )}
      {emailModal && (
        <EmailChangeModal
          onClose={() => setEmailModal(false)}
          onDone={() => {
            void invalidate();
          }}
        />
      )}
    </Section>
  );
}

// ===================== 组织与角色 =====================

function WorkSection() {
  const work = useWork(true);
  if (work.isLoading || !work.data) return <Skeleton active />;
  const d = work.data;
  return (
    <Section title="组织与角色" hint="由主管理员维护，如需调整请联系所在部门负责人。">
      <Row
        label="所属部门"
        value={
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span>{d.mainDeptPath ?? d.mainDeptName ?? '未分配部门'}</span>
            {d.mainDeptName && <CountChip tone="brand">主部门</CountChip>}
          </div>
        }
        hint={
          d.secondaryDeptNames && d.secondaryDeptNames.length > 0
            ? `兼任：${d.secondaryDeptNames.join('、')}`
            : '支持多部门归属；新增部门由主管理员在「通讯录」中操作'
        }
      />
      <Row
        label="平台角色"
        value={
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {/* 「主管理员」是身份标记不是状态，按 D1 口径走 CountChip brand（与成员详情一致） */}
            {d.owner && <CountChip tone="brand">主管理员</CountChip>}
            {/* owner 已单独出徽章，过滤同名「主管理员」角色避免并排重复（T8-05） */}
            {(d.roles ?? [])
              .filter((r) => !(d.owner && r.name === '主管理员'))
              .map((r) => (
                <CountChip key={r.code}>{r.name}</CountChip>
              ))}
            {!d.owner && (d.roles ?? []).length === 0 && (
              <span style={{ color: 'var(--text-tertiary)' }}>未分配角色</span>
            )}
          </div>
        }
        hint="多角色合并取并集；主管理员账号至少保留一名"
      />
      <Row
        label="账号状态"
        value={
          <StatusPill
            badge={
              d.statusLabel === 'active'
                ? toneBadge('success', '在职 · 活跃')
                : toneBadge('neutral', '已停用')
            }
          />
        }
      />
      <Row label="数据范围" value={d.dataScopeLabel ?? '—'} hint="由所绑定的角色继承" />
    </Section>
  );
}

// ===================== 安全设置 =====================

function fmtTimeout(seconds?: number): string {
  if (!seconds) return '—';
  const min = Math.round(seconds / 60);
  if (min >= 60 && min % 60 === 0) return `${min / 60} 小时`;
  return `${min} 分钟`;
}

const MFA_METHOD_LABEL: Record<string, string> = {
  TOTP: '身份验证器 (TOTP)',
  SMS: '短信验证码',
  EMAIL: '邮箱验证码',
};

function SecuritySection() {
  const { message } = App.useApp();
  const sec = useSecurity(true);
  const invalidate = useInvalidateProfile();
  // 身份验证方式总览：与敏感操作「验证身份」弹窗同一数据源
  const channelsQ = useQuery({
    queryKey: ['tenant', 'stepup', 'channels'],
    queryFn: () => stepUp.getChannels(),
    staleTime: 0,
  });
  const [pwdOpen, setPwdOpen] = useState(false);
  const [enableOpen, setEnableOpen] = useState(false);
  const [disableOpen, setDisableOpen] = useState(false);
  const [bindOpen, setBindOpen] = useState(false);

  const disableMfa = useMutation({
    mutationFn: (stepUpToken: string) => profileApi.disableMfa(stepUpToken),
    onSuccess: async () => {
      message.success('登录二步验证已关闭');
      setDisableOpen(false);
      await invalidate();
      await channelsQ.refetch();
    },
    onError: (e) => message.error(describeApiError(e, '操作失败')),
  });

  if (sec.isLoading || !sec.data) return <Skeleton active />;
  const d = sec.data;
  const mfaOn = Boolean(d.mfaEnabled);
  const channels = channelsQ.data;
  const totpBound = channels?.totp?.available === true;
  const methodLabel = d.mfaMethod ? MFA_METHOD_LABEL[d.mfaMethod] : null;

  return (
    <Section title="安全设置" hint="保护账号安全的关键开关。重要变更将记录到操作日志。">
      <Row
        label="登录密码"
        value={<span style={{ fontFamily: 'var(--font-mono)' }}>••••••••</span>}
        hint="平台密码策略：≥ 8 位，含大小写 / 数字 / 符号中的 3 类"
        action={
          <WaBtn size="sm" icon={<KeyOutlined />} onClick={() => setPwdOpen(true)}>
            修改密码
          </WaBtn>
        }
      />
      <Row
        label="登录二步验证"
        value={
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Switch
              checked={mfaOn}
              onChange={(next) => {
                if (next) setEnableOpen(true);
                else setDisableOpen(true);
              }}
            />
            {mfaOn && methodLabel ? <StatusPill badge={toneBadge('success', methodLabel)} /> : null}
            {!mfaOn && <StatusPill badge={toneBadge('warning', '建议开启')} />}
          </div>
        }
        hint="开启后每次登录需额外验证一步；验证方式自动选用最强可用项（已绑验证器用动态码，否则邮箱验证码）"
      />
      <Row
        label="身份验证方式"
        value={
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 13 }}>
            <span
              style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}
            >
              <span>身份验证器 (TOTP)</span>
              <StatusPill
                badge={totpBound ? toneBadge('success', '已绑定') : toneBadge('neutral', '未绑定')}
              />
              <RowTextAction onClick={() => setBindOpen(true)}>
                {totpBound ? '重新绑定' : '绑定'}
              </RowTextAction>
            </span>
            <span
              style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}
            >
              <span>短信验证码</span>
              {SMS_READY ? (
                <StatusPill
                  badge={
                    channels?.sms?.available
                      ? toneBadge('success', '可用')
                      : toneBadge('neutral', '未绑定手机号')
                  }
                />
              ) : (
                <CountChip>{SMS_CLOSED_TAG}</CountChip>
              )}
            </span>
            <span
              style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}
            >
              <span>邮箱验证码</span>
              <StatusPill
                badge={
                  channels?.email?.available
                    ? toneBadge('success', '可用')
                    : toneBadge('neutral', '未绑定邮箱')
                }
              />
            </span>
          </div>
        }
        hint="登录二步验证与敏感操作前的身份验证均使用这些方式。手机号、邮箱在「基本信息」中维护"
      />
      <Row
        label="会话超时"
        value={fmtTimeout(d.tokenTtlSeconds)}
        hint="超过该时长无操作将自动退出登录（由平台统一控制）"
      />
      <Row
        label="登录失败锁定"
        value={`${d.maxAttempts ?? '—'} 次连续失败 · 锁定 ${Math.round((d.windowSeconds ?? 0) / 60)} 分钟`}
        hint="由平台统一控制，不可自助修改"
      />

      {pwdOpen && <ChangePasswordModal onClose={() => setPwdOpen(false)} />}
      {enableOpen && (
        <MfaEnableModal
          onClose={() => setEnableOpen(false)}
          onEnabled={() => {
            void invalidate();
            void channelsQ.refetch();
          }}
        />
      )}
      {bindOpen && (
        <TotpBindModal
          rebind={totpBound}
          onClose={() => setBindOpen(false)}
          onDone={() => {
            void invalidate();
            void channelsQ.refetch();
          }}
        />
      )}
      {disableOpen && (
        <Modal
          title="关闭登录二步验证"
          open
          width={520}
          onCancel={() => setDisableOpen(false)}
          footer={null}
          destroyOnHidden
        >
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 12 }}>
            关闭后登录仅凭密码，恢复码将同时作废（验证器绑定保留）。
          </div>
          <StepUpGate title="关闭前请先验证身份" onVerified={(token) => disableMfa.mutate(token)} />
        </Modal>
      )}
    </Section>
  );
}

// ===================== 第三方账号（OAuth）=====================

/** 当前三渠道均走 mock 客户端，同态可绑定。 */
const OAUTH_ENABLED: Record<string, boolean> = { dingtalk: true, wechat: true, wecom: true };

function OAuthSection() {
  const { message } = App.useApp();
  const bindings = useOAuthBindings(true);
  const invalidate = useInvalidateProfile();

  const bind = useMutation({
    mutationFn: (provider: string) => profileApi.bindOAuth(provider, `mock-${provider}-profile`),
    onSuccess: async () => {
      message.success('已绑定');
      await invalidate();
    },
    onError: (e) => message.error(describeApiError(e, '绑定失败')),
  });
  const unbind = useMutation({
    mutationFn: (provider: string) => profileApi.unbindOAuth(provider),
    onSuccess: async () => {
      message.success('已解绑');
      await invalidate();
    },
    onError: (e) => message.error(describeApiError(e, '解绑失败')),
  });

  const boundByProvider = useMemo(() => {
    const m = new Map<string, OAuthBinding>();
    for (const b of bindings.data ?? []) {
      if (b.provider) m.set(b.provider, b);
    }
    return m;
  }, [bindings.data]);

  return (
    <Section title="第三方账号" hint="绑定后可在登录页一键登录；解绑不会影响该平台账号的其他功能。">
      {bindings.isLoading ? (
        <Skeleton active />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {OAUTH_PROVIDERS.map((o) => {
            const bound = boundByProvider.get(o.kind);
            const enabled = OAUTH_ENABLED[o.kind] ?? false;
            return (
              <div
                key={o.kind}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 14,
                  padding: '14px 16px',
                  border: '1px solid var(--border)',
                  borderRadius: 8,
                  background: 'var(--bg-muted)',
                }}
              >
                <div
                  style={{
                    width: 36,
                    height: 36,
                    borderRadius: 8,
                    background: o.color,
                    flexShrink: 0,
                  }}
                />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontWeight: 600, fontSize: 14 }}>{o.name}</span>
                    <StatusPill
                      badge={
                        bound ? toneBadge('success', '已绑定') : toneBadge('neutral', '未绑定')
                      }
                    />
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>
                    {bound
                      ? (bound.nickname ?? `${o.name}账号`)
                      : enabled
                        ? '绑定后可在登录页用该账号一键登录'
                        : 'OAuth 登录模块即将上线，敬请期待'}
                  </div>
                </div>
                {bound ? (
                  <Popconfirm
                    title={`解绑${o.name}`}
                    description="解绑后将无法用该账号登录，确定继续？"
                    okText="解绑"
                    cancelText="取消"
                    onConfirm={() => unbind.mutate(o.kind)}
                  >
                    <WaBtn size="sm" disabled={unbind.isPending}>
                      解绑
                    </WaBtn>
                  </Popconfirm>
                ) : enabled ? (
                  <WaBtn
                    size="sm"
                    kind="primary"
                    disabled={bind.isPending}
                    onClick={() => bind.mutate(o.kind)}
                  >
                    {bind.isPending ? '绑定中…' : '立即绑定'}
                  </WaBtn>
                ) : (
                  <Tooltip title="即将上线">
                    <span>
                      <WaBtn size="sm" disabled>
                        立即绑定
                      </WaBtn>
                    </span>
                  </Tooltip>
                )}
              </div>
            );
          })}
        </div>
      )}
    </Section>
  );
}

// ===================== 登录设备 / 会话 =====================

function fmtTime(iso?: string): string {
  return formatDateTime(iso);
}

function DevicesSection() {
  const { message, modal } = App.useApp();
  const sessions = useSessions(true);
  const invalidate = useInvalidateProfile();

  const revoke = useMutation({
    mutationFn: (sessionId: string) => profileApi.revokeSession(sessionId),
    onSuccess: async () => {
      message.success('已下线该会话');
      await invalidate();
    },
    onError: (e) => message.error(describeApiError(e, '下线失败')),
  });
  const revokeOthers = useMutation({
    mutationFn: () => profileApi.revokeOtherSessions(),
    onSuccess: async () => {
      message.success('已下线其他设备');
      await invalidate();
    },
    onError: (e) => message.error(describeApiError(e, '操作失败')),
  });

  const list = useMemo(() => sessions.data ?? [], [sessions.data]);
  const hasOthers = list.some((s) => !s.current);

  const confirmRevoke = (sessionId: string) => {
    modal.confirm({
      title: '确认下线该会话？',
      content: '下线后该设备需重新登录。如怀疑账号被盗用，建议同时修改密码。',
      okText: '确认下线',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => revoke.mutateAsync(sessionId),
    });
  };
  const confirmRevokeOthers = () => {
    modal.confirm({
      title: '确认下线其他所有设备？',
      content: '将下线除当前设备外的全部登录会话，相关设备需重新登录。当前设备不受影响。',
      okText: '全部下线',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => revokeOthers.mutateAsync(),
    });
  };

  return (
    <Section
      title="登录设备与会话"
      hint="发现陌生设备？请立即下线并修改密码。"
      extra={
        <WaBtn
          size="sm"
          danger
          disabled={!hasOthers || revokeOthers.isPending}
          onClick={confirmRevokeOthers}
        >
          全部下线（除当前）
        </WaBtn>
      }
    >
      {sessions.isLoading ? (
        <Skeleton active />
      ) : list.length === 0 ? (
        <div style={{ padding: 24, textAlign: 'center', color: 'var(--text-tertiary)' }}>
          暂无活跃会话
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {list.map((s, i) => (
            <div
              key={s.sessionId}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 14,
                padding: '14px 0',
                borderBottom: i < list.length - 1 ? '1px solid var(--divider)' : 'none',
              }}
            >
              <div
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: 8,
                  background: s.current ? 'var(--brand-bg)' : 'var(--bg-muted)',
                  color: s.current ? 'var(--brand)' : 'var(--text-secondary)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                }}
              >
                <GlobalOutlined />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ fontWeight: 500, fontSize: 14 }}>
                    {s.deviceLabel ?? '未知设备'}
                  </span>
                  {s.current && (
                    <StatusPill badge={{ kind: 'brand', text: '当前会话', color: '' }} />
                  )}
                </div>
                <div
                  style={{
                    fontSize: 12,
                    color: 'var(--text-tertiary)',
                    marginTop: 4,
                    display: 'flex',
                    gap: 10,
                    flexWrap: 'wrap',
                  }}
                >
                  <span>IP：{s.ip ?? '—'}</span>
                  <span style={{ color: 'var(--border-strong)' }}>·</span>
                  <span>登录：{fmtTime(s.loginAt)}</span>
                  <span style={{ color: 'var(--border-strong)' }}>·</span>
                  <span>最近活跃：{fmtTime(s.lastAccessAt)}</span>
                </div>
              </div>
              {!s.current && (
                <WaBtn
                  size="sm"
                  kind="ghost"
                  danger
                  disabled={revoke.isPending}
                  onClick={() => confirmRevoke(s.sessionId!)}
                >
                  下线
                </WaBtn>
              )}
            </div>
          ))}
        </div>
      )}
      <div
        style={{
          marginTop: 16,
          padding: 12,
          background: 'var(--bg-muted)',
          borderRadius: 8,
          fontSize: 12,
          color: 'var(--text-secondary)',
        }}
      >
        多端共享会话由平台策略统一：Web 端与 H5 端同账号可同时在线，不互踢。
      </div>
    </Section>
  );
}
