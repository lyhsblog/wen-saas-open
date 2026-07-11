import { useEffect, useMemo, useState } from 'react';
import { App, Alert, Button, Checkbox, Dropdown, Empty, Input, Space, Spin, Tooltip } from 'antd';
import { CountChip, useTabStatus } from '@wen-saas/shared-ui';
import {
  SearchOutlined,
  DownOutlined,
  RightOutlined,
  LockOutlined,
  CheckOutlined,
  EditOutlined,
  ColumnHeightOutlined,
  VerticalAlignMiddleOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import type { CatalogModule, CatalogMenu } from '@wen-saas/api-client';
import { describeApiError } from '@wen-saas/api-client';
import { useIsReadonly, PermissionGate, ReadonlyButton } from '@wen-saas/permissions';
import { catalogApi } from './permissionCatalogApi';
import { useUpdateRolePermissions, useDataPermissionActions } from './roleQueries';
import { WaBtn } from '../../components/wa';

type Props = {
  roleId: string;
  permissions: string[];
  readonly: boolean;
  /** 编辑态勾选集偏离服务端已保存集时上抛 true，供父级拦截切角色/切 Tab。 */
  onDirtyChange: (dirty: boolean) => void;
};

const menuId = (modKey: string, menuKey: string) => `${modKey}::${menuKey}`;

function menuLeafCodes(menu: CatalogMenu): string[] {
  return menu.points.map((p) => p.code);
}
function moduleLeafCodes(mod: CatalogModule): string[] {
  return mod.menus.flatMap(menuLeafCodes);
}

export function RolePermissionsTab({ roleId, permissions, readonly, onDirtyChange }: Props) {
  const { message } = App.useApp();
  // 业务只读（主管理员角色）或套餐到期只读，任一为真即禁止勾选/保存。
  const pkgReadonly = useIsReadonly();
  const interactionReadonly = readonly || pkgReadonly;
  const catalogQuery = useQuery({
    queryKey: ['permission-catalog'],
    queryFn: () => catalogApi.catalog(),
  });
  const updatePermissions = useUpdateRolePermissions(roleId);
  const { data: dataPermActions = [] } = useDataPermissionActions();
  const dataPermCodes = useMemo(
    () => new Set(dataPermActions.map((a) => a.code)),
    [dataPermActions],
  );

  const modules = useMemo(() => catalogQuery.data ?? [], [catalogQuery.data]);
  const leafCodes = useMemo(() => {
    const s = new Set<string>();
    modules.forEach((m) => moduleLeafCodes(m).forEach((c) => s.add(c)));
    return s;
  }, [modules]);
  const total = leafCodes.size;
  const allModKeys = useMemo(() => modules.map((m) => m.key), [modules]);
  const allMenuIds = useMemo(
    () => modules.flatMap((m) => m.menus.map((menu) => menuId(m.key, menu.key))),
    [modules],
  );

  // 角色持有但目录里不存在的权限点（未购套餐模块隐去 / 码未注册）—— 当前不可见，保存时原样保留
  const invalidCodes = useMemo(
    () => permissions.filter((c) => !leafCodes.has(c)),
    [permissions, leafCodes],
  );

  // 服务端已保存的勾选集（仅保留目录内有效叶子），也是 dirty 比对基准
  const serverChecked = useMemo(
    () => new Set(permissions.filter((c) => leafCodes.has(c))),
    [permissions, leafCodes],
  );
  // checked 受控；初值用角色现有权限（仅保留目录内有效叶子）
  const [checked, setChecked] = useState<Set<string> | null>(null);
  const effectiveChecked = useMemo(() => checked ?? serverChecked, [checked, serverChecked]);

  // 查看 / 编辑态：默认查看，只读角色（主管理员）恒为查看
  const [editing, setEditing] = useState(false);

  // 当前勾选集与服务端已保存集比对；相等即无改动
  const isDirty = useMemo(() => {
    if (effectiveChecked.size !== serverChecked.size) return true;
    for (const c of effectiveChecked) if (!serverChecked.has(c)) return true;
    return false;
  }, [effectiveChecked, serverChecked]);

  // 编辑中有未保存改动：上抛给父级拦截切角色/切 Tab，并标记页签「未保存」（误关/刷新有二次确认）
  const editingDirty = editing && isDirty;
  const { setDirty: setTabDirty } = useTabStatus();
  useEffect(() => {
    onDirtyChange(editingDirty);
    setTabDirty(editingDirty);
    return () => {
      onDirtyChange(false);
      setTabDirty(false);
    };
  }, [editingDirty, onDirtyChange, setTabDirty]);
  const [search, setSearch] = useState('');
  const [activeModule, setActiveModule] = useState<string>('all');
  // null = 默认全部展开；非 null = 用户显式展开集合
  const [expandedMods, setExpandedMods] = useState<Set<string> | null>(null);
  const [expandedMenus, setExpandedMenus] = useState<Set<string> | null>(null);

  const lc = (s: string) => s.toLowerCase();
  const kw = lc(search.trim());
  const matchText = (s: string) => !kw || lc(s).includes(kw);
  const menuMatches = (menu: CatalogMenu) =>
    matchText(menu.name) || menu.points.some((p) => matchText(p.name));
  const moduleMatches = (m: CatalogModule) => matchText(m.name) || m.menus.some(menuMatches);

  // 查看态只列已勾选的功能点（含数据权限标）；编辑态列全部
  const grantedSet = effectiveChecked;
  const menuHasGranted = (menu: CatalogMenu) => menu.points.some((p) => grantedSet.has(p.code));
  const moduleHasGranted = (m: CatalogModule) => moduleLeafCodes(m).some((c) => grantedSet.has(c));
  const pointVisible = (code: string) => editing || grantedSet.has(code);

  const visibleModules = modules
    .filter((m) => activeModule === 'all' || m.key === activeModule)
    .filter((m) => !kw || moduleMatches(m))
    .filter((m) => editing || moduleHasGranted(m));

  // 搜索时强制展开命中项；否则 null=全展开，非 null 用集合
  const isModOpen = (m: CatalogModule) =>
    kw ? moduleMatches(m) : expandedMods === null ? true : expandedMods.has(m.key);
  const isMenuOpen = (m: CatalogModule, menu: CatalogMenu) =>
    kw
      ? menuMatches(menu)
      : expandedMenus === null
        ? true
        : expandedMenus.has(menuId(m.key, menu.key));

  const setCheckedFrom = (next: Set<string>) => setChecked(new Set(next));
  const canInteract = editing && !interactionReadonly;

  const toggleLeaf = (code: string) => {
    if (!canInteract) return;
    const next = new Set(effectiveChecked);
    if (next.has(code)) next.delete(code);
    else next.add(code);
    setCheckedFrom(next);
  };
  const toggleGroup = (codes: string[]) => {
    if (!canInteract) return;
    const next = new Set(effectiveChecked);
    const allOn = codes.length > 0 && codes.every((c) => next.has(c));
    if (allOn) codes.forEach((c) => next.delete(c));
    else codes.forEach((c) => next.add(c));
    setCheckedFrom(next);
  };

  const toggleModExpand = (key: string) =>
    setExpandedMods((s) => {
      const base = s ?? new Set(allModKeys);
      const n = new Set(base);
      if (n.has(key)) n.delete(key);
      else n.add(key);
      return n;
    });
  const toggleMenuExpand = (id: string) =>
    setExpandedMenus((s) => {
      const base = s ?? new Set(allMenuIds);
      const n = new Set(base);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });

  // 展开级别：1=全部收起 2=仅展开模块（菜单收起）3=全部展开
  const expandToLevel = (level: 1 | 2 | 3) => {
    if (level === 1) {
      setExpandedMods(new Set());
      setExpandedMenus(new Set());
    } else if (level === 2) {
      setExpandedMods(new Set(allModKeys));
      setExpandedMenus(new Set());
    } else {
      setExpandedMods(new Set(allModKeys));
      setExpandedMenus(new Set(allMenuIds));
    }
  };

  const exitEdit = () => {
    setChecked(null);
    setEditing(false);
  };

  const save = async () => {
    try {
      const codes = [...[...effectiveChecked].filter((c) => leafCodes.has(c)), ...invalidCodes];
      await updatePermissions.mutateAsync({ codes });
      message.success('已保存功能权限');
      exitEdit();
    } catch (err) {
      message.error(describeApiError(err, '保存失败'));
    }
  };

  if (catalogQuery.isPending) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }
  if (modules.length === 0) {
    return (
      <div style={{ padding: 24 }}>
        <Empty description="暂无可配置的权限点（业务模块注册后自动出现）" />
      </div>
    );
  }

  const selectedCount = [...effectiveChecked].filter((c) => leafCodes.has(c)).length;
  const showEmptyView = !editing && selectedCount === 0;

  return (
    <div style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 16 }}>
      {invalidCodes.length > 0 && (
        <Alert
          type="warning"
          showIcon
          message={`部分历史已选菜单当前不可见（共 ${invalidCodes.length} 项），保存时将保留。如部分模块到期，续期后将恢复。`}
        />
      )}

      <div className="wa-card" style={{ padding: 0, overflow: 'visible' }}>
        {/* 工具条 */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            padding: '14px 20px',
            borderBottom: '1px solid var(--divider)',
            flexWrap: 'wrap',
          }}
        >
          <Input
            allowClear
            placeholder="搜索权限点"
            prefix={<SearchOutlined style={{ color: 'var(--text-tertiary)' }} />}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ width: 240 }}
          />
          {/* 展开控制：对齐通讯录部门树（展开全部 + 悬停级数 / 折叠） */}
          <Space.Compact size="small">
            <Dropdown
              trigger={['hover']}
              disabled={!!kw}
              menu={{
                items: [{ key: '2', label: '仅展开模块' }],
                onClick: ({ key }) => expandToLevel(Number(key) as 2),
              }}
            >
              <Button
                size="small"
                icon={<ColumnHeightOutlined />}
                disabled={!!kw}
                onClick={() => expandToLevel(3)}
                style={{ fontSize: 12 }}
              >
                展开全部
                <DownOutlined style={{ fontSize: 9, marginInlineStart: 2 }} />
              </Button>
            </Dropdown>
            <Tooltip title="全部收起">
              <Button
                size="small"
                icon={<VerticalAlignMiddleOutlined />}
                disabled={!!kw}
                onClick={() => expandToLevel(1)}
                style={{ fontSize: 12 }}
              >
                折叠
              </Button>
            </Tooltip>
          </Space.Compact>
          <div className="wa-spacer" />
          <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
            {editing ? '已选' : '已分配'} <b style={{ color: 'var(--brand)' }}>{selectedCount}</b> /
            共 <b>{total}</b>
          </span>
          {editing && !readonly && (
            <>
              <WaBtn
                size="sm"
                kind="ghost"
                disabled={pkgReadonly}
                onClick={() => setCheckedFrom(new Set(leafCodes))}
              >
                全选
              </WaBtn>
              <WaBtn
                size="sm"
                kind="ghost"
                disabled={pkgReadonly}
                onClick={() => setCheckedFrom(new Set())}
              >
                清空
              </WaBtn>
            </>
          )}
          {!editing && !readonly && (
            <PermissionGate permission="role.update">
              <ReadonlyButton
                size="small"
                icon={<EditOutlined />}
                disabled={pkgReadonly}
                onClick={() => setEditing(true)}
              >
                编辑
              </ReadonlyButton>
            </PermissionGate>
          )}
        </div>

        {/* 模块 tab 切换 */}
        <div
          style={{
            display: 'flex',
            gap: 4,
            padding: '8px 20px',
            borderBottom: '1px solid var(--divider)',
            overflowX: 'auto',
          }}
        >
          <ModuleTab
            label="全部"
            active={activeModule === 'all'}
            onClick={() => setActiveModule('all')}
          />
          {modules.map((m) => (
            <ModuleTab
              key={m.key}
              label={m.name}
              active={activeModule === m.key}
              onClick={() => setActiveModule(m.key)}
            />
          ))}
        </div>

        {readonly && (
          <div
            style={{
              padding: 10,
              background: 'var(--info-bg)',
              borderBottom: '1px solid #BFDBFE',
              fontSize: 12,
              color: 'var(--info)',
              textAlign: 'center',
            }}
          >
            <LockOutlined style={{ marginRight: 6 }} />
            主管理员的功能权限不可修改，永远是「全部权限」。
          </div>
        )}

        {/* 树 */}
        <div style={{ padding: '8px 0' }}>
          {showEmptyView ? (
            <div style={{ padding: 24 }}>
              <Empty description="该角色暂未分配任何功能权限" />
            </div>
          ) : visibleModules.length === 0 ? (
            <div style={{ padding: 24 }}>
              <Empty description="没有匹配的权限点" />
            </div>
          ) : (
            visibleModules.map((m) => {
              const modLeaves = moduleLeafCodes(m);
              const modChecked = modLeaves.filter((c) => effectiveChecked.has(c)).length;
              const allOn = modLeaves.length > 0 && modChecked === modLeaves.length;
              const some = modChecked > 0 && !allOn;
              const open = isModOpen(m);
              return (
                <div key={m.key} style={{ borderBottom: '1px solid var(--divider)' }}>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      padding: '10px 20px',
                      background: 'var(--bg-muted)',
                      cursor: 'pointer',
                    }}
                    onClick={() => !kw && toggleModExpand(m.key)}
                  >
                    <span style={{ color: 'var(--text-tertiary)', marginRight: 8, fontSize: 12 }}>
                      {open ? <DownOutlined /> : <RightOutlined />}
                    </span>
                    <span onClick={(e) => e.stopPropagation()} style={{ display: 'inline-flex' }}>
                      <Checkbox
                        checked={allOn}
                        indeterminate={some}
                        disabled={!canInteract}
                        onChange={() => toggleGroup(modLeaves)}
                      />
                    </span>
                    <span style={{ marginLeft: 10, fontWeight: 600, fontSize: 14 }}>{m.name}</span>
                    <div className="wa-spacer" />
                    <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
                      {modChecked} / {modLeaves.length}
                    </span>
                  </div>

                  {open &&
                    m.menus
                      .filter((menu) => !kw || menuMatches(menu))
                      .filter((menu) => editing || menuHasGranted(menu))
                      .map((menu) => {
                        const mLeaves = menuLeafCodes(menu);
                        const mChecked = mLeaves.filter((c) => effectiveChecked.has(c)).length;
                        const mAllOn = mLeaves.length > 0 && mChecked === mLeaves.length;
                        const mSome = mChecked > 0 && !mAllOn;
                        const mid = menuId(m.key, menu.key);
                        const mOpen = isMenuOpen(m, menu);
                        const points = menu.points.filter((p) => pointVisible(p.code));
                        return (
                          <div key={mid}>
                            <div
                              style={{
                                display: 'flex',
                                alignItems: 'center',
                                padding: '8px 20px 8px 44px',
                                cursor: 'pointer',
                              }}
                              onClick={() => !kw && toggleMenuExpand(mid)}
                            >
                              <span
                                style={{
                                  color: 'var(--text-tertiary)',
                                  marginRight: 8,
                                  fontSize: 11,
                                }}
                              >
                                {mOpen ? <DownOutlined /> : <RightOutlined />}
                              </span>
                              <span
                                onClick={(e) => e.stopPropagation()}
                                style={{ display: 'inline-flex' }}
                              >
                                <Checkbox
                                  checked={mAllOn}
                                  indeterminate={mSome}
                                  disabled={!canInteract}
                                  onChange={() => toggleGroup(mLeaves)}
                                />
                              </span>
                              <span style={{ marginLeft: 8, fontWeight: 500, fontSize: 13 }}>
                                {menu.name}
                              </span>
                              <span
                                style={{
                                  marginLeft: 8,
                                  fontSize: 11,
                                  color: 'var(--text-tertiary)',
                                }}
                              >
                                {mChecked} / {mLeaves.length}
                              </span>
                            </div>
                            {mOpen && (
                              <div
                                style={{
                                  padding: '4px 20px 12px 80px',
                                  display: 'grid',
                                  gridTemplateColumns: 'repeat(3, 1fr)',
                                  gap: 4,
                                }}
                              >
                                {points.map((p) => {
                                  const on = effectiveChecked.has(p.code);
                                  const hl = !!kw && matchText(p.name);
                                  const hasDataPerm = dataPermCodes.has(p.code);
                                  return (
                                    <Checkbox
                                      key={p.code}
                                      {...(p.description ? { title: p.description } : {})}
                                      checked={on}
                                      disabled={!canInteract}
                                      onChange={() => toggleLeaf(p.code)}
                                      style={{
                                        alignItems: 'center',
                                        padding: '6px 10px',
                                        borderRadius: 'var(--r-sm)',
                                        background: hl ? 'var(--warning-bg)' : 'transparent',
                                      }}
                                    >
                                      {/* 查看态复选框恒禁用，文字色显式固定，避免整棵已分配清单被置灰 */}
                                      <span
                                        style={{
                                          display: 'inline-flex',
                                          alignItems: 'center',
                                          gap: 8,
                                          fontSize: 13,
                                          color: 'var(--text)',
                                        }}
                                      >
                                        {p.name}
                                        {hasDataPerm && (
                                          <CountChip tone="brand">数据权限</CountChip>
                                        )}
                                        {p.description && (
                                          <InfoCircleOutlined
                                            style={{ color: 'var(--text-tertiary)', fontSize: 11 }}
                                          />
                                        )}
                                      </span>
                                    </Checkbox>
                                  );
                                })}
                              </div>
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

      {editing && !readonly && (
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <WaBtn onClick={exitEdit}>取消</WaBtn>
          <WaBtn
            kind="primary"
            icon={<CheckOutlined />}
            disabled={!isDirty || updatePermissions.isPending || pkgReadonly}
            onClick={() => void save()}
          >
            保存权限
          </WaBtn>
        </div>
      )}
    </div>
  );
}

function ModuleTab({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  // 胶囊态视觉（选中=品牌浅底描边）走内联覆盖，AntD Button 只承担交互壳。
  return (
    <Button
      size="small"
      onClick={onClick}
      style={{
        borderRadius: 'var(--r-pill)',
        borderColor: active ? 'var(--brand)' : 'var(--border)',
        background: active ? 'var(--brand-bg)' : 'var(--bg-elev)',
        color: active ? 'var(--brand)' : 'var(--text-secondary)',
        fontSize: 13,
        fontWeight: active ? 600 : 500,
      }}
    >
      {label}
    </Button>
  );
}
