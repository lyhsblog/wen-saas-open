import { useEffect, useMemo, useState } from 'react';
import { App } from 'antd';
import { CountChip } from '@wen-saas/shared-ui';
import { SettingOutlined } from '@ant-design/icons';
import { useBlocker, useNavigate, useParams } from 'react-router';
import { useCapabilitiesStore } from '@wen-saas/permissions';
import { MembersTab } from './tabs/MembersTab';
import { RolesTab } from './tabs/RolesTab';
import { ParamsTab } from './tabs/ParamsTab';
import { LogsTab } from './tabs/LogsTab';

const TAB_KEYS = ['members', 'roles', 'params', 'logs'] as const;
type TabKey = (typeof TAB_KEYS)[number];

/** 各 tab 所需运营权限码（超管 tenantOwner 恒可见）。CSM 仅持 operation_log.list → 仅见操作日志。 */
const TAB_PERMISSION: Record<TabKey, string> = {
  members: 'ops.team.member.list',
  roles: 'ops.team.role.list',
  params: 'ops.setting.view',
  logs: 'ops.operation_log.list',
};
const TAB_LABEL: Record<TabKey, string> = {
  members: '团队成员',
  roles: '角色权限',
  params: '系统参数',
  logs: '操作日志',
};
/** URL 路径段 ↔ tab key（路径段对齐 sys_menu 种子：操作日志 = operation-logs）。 */
const SEGMENT_BY_TAB: Record<TabKey, string> = {
  members: 'members',
  roles: 'roles',
  params: 'params',
  logs: 'operation-logs',
};
const TAB_BY_SEGMENT: Record<string, TabKey> = {
  members: 'members',
  roles: 'roles',
  params: 'params',
  'operation-logs': 'logs',
};

export function OpsSysAdminPage() {
  const navigate = useNavigate();
  const { modal } = App.useApp();
  const { section } = useParams<{ section: string }>();
  // 系统参数板块的未保存改动（ParamsTab 上抛），离开本路由前 modal.confirm 拦截。
  const [paramsDirty, setParamsDirty] = useState(false);
  const owner = useCapabilitiesStore((s) => s.caps?.tenantOwner ?? false);
  const permissions = useCapabilitiesStore((s) => s.caps?.permissions);

  const visibleTabs = useMemo<TabKey[]>(() => {
    const perms = new Set(permissions ?? []);
    return TAB_KEYS.filter((k) => owner || perms.has(TAB_PERMISSION[k]));
  }, [owner, permissions]);

  const fromPath = section ? TAB_BY_SEGMENT[section] : undefined;
  // 路径段无权 / 缺省时回退到首个可见板块（无可见板块时 undefined，下方渲染空）
  const tab: TabKey | undefined =
    fromPath && visibleTabs.includes(fromPath) ? fromPath : visibleTabs[0];

  // 导航由侧栏各入口驱动（页内不再有 Tab）。系统参数有未保存改动时，离开该路由前拦截确认。
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      paramsDirty && currentLocation.pathname !== nextLocation.pathname,
  );
  useEffect(() => {
    if (blocker.state !== 'blocked') return;
    modal.confirm({
      title: '放弃未保存的更改？',
      content: '系统参数有未保存的修改，离开后将丢失。',
      okText: '放弃修改',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: () => blocker.proceed(),
      onCancel: () => blocker.reset(),
    });
  }, [blocker, modal]);

  // 规范化 URL：/sys（无段）或无权访问的段，跳到实际选中板块的路径，保证侧栏高亮一致
  useEffect(() => {
    if (tab && section !== SEGMENT_BY_TAB[tab]) {
      void navigate(`/sys/${SEGMENT_BY_TAB[tab]}`, { replace: true });
    }
  }, [tab, section, navigate]);

  if (!tab) {
    return (
      <div style={{ padding: 48, textAlign: 'center', color: 'var(--text-tertiary)' }}>
        无系统管理权限
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>
      <div
        style={{
          padding: '16px 24px',
          background: 'var(--bg-elev)',
          borderBottom: '1px solid var(--divider)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <SettingOutlined style={{ color: 'var(--brand)', fontSize: 18 }} />
          <span style={{ fontSize: 18, fontWeight: 600 }}>{TAB_LABEL[tab]}</span>
          <CountChip>运营内部</CountChip>
        </div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        {tab === 'members' && <MembersTab />}
        {tab === 'roles' && <RolesTab />}
        {tab === 'params' && <ParamsTab onDirtyChange={setParamsDirty} />}
        {tab === 'logs' && <LogsTab />}
      </div>
    </div>
  );
}
