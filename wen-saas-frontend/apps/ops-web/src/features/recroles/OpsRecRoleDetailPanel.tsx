import { useMemo, useState } from 'react';
import { Alert, App, Button, Empty, Spin, Table, Tabs, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CloseOutlined, CopyOutlined, DownloadOutlined, EditOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { opsRecommendedRoleAdminApi } from '@wen-saas/api-client';
import type { RecRoleActivationItem } from '@wen-saas/api-client';
import { CountChip, ErrorState, formatDate, StatusPill, toneBadge } from '@wen-saas/shared-ui';
import { flattenCatalogLeaves, type CatalogLeaf } from '../permission/PermissionCatalogTree';
import { http } from '../../lib/http';
import { recRoleStatusBadge, dataScopeLabel } from './utils/recRoleMeta';
import { OpsRecRoleFormModal } from './OpsRecRoleFormModal';
import { OpsRecRoleCopyModal } from './OpsRecRoleCopyModal';
import {
  useDeleteRecRole,
  usePublishRecRole,
  useUnpublishRecRole,
} from './hooks/useRecRoleMutations';

type Props = {
  id: string;
  onClose: () => void;
  onDeleted: () => void;
  /** 复制成功后选中新模板。 */
  onSelect: (id: string) => void;
};

export function OpsRecRoleDetailPanel({ id, onClose, onDeleted, onSelect }: Props) {
  const { message, modal } = App.useApp();
  const [editOpen, setEditOpen] = useState(false);
  const [copyOpen, setCopyOpen] = useState(false);
  const [actPage, setActPage] = useState(1);
  const [exporting, setExporting] = useState(false);
  const ACT_PAGE_SIZE = 10;

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['ops', 'rec-role', id],
    queryFn: () => opsRecommendedRoleAdminApi(http).getTemplate(id),
  });
  const { data: catalog } = useQuery({
    queryKey: ['ops', 'rec-role-permission-catalog'],
    queryFn: () => opsRecommendedRoleAdminApi(http).listPermissionCatalog(),
  });
  const { data: activations, isLoading: actLoading } = useQuery({
    queryKey: ['ops', 'rec-role-activations', id, actPage],
    queryFn: () =>
      opsRecommendedRoleAdminApi(http).listActivations(id, {
        page: actPage,
        pageSize: ACT_PAGE_SIZE,
      }),
  });

  const publish = usePublishRecRole();
  const unpublish = useUnpublishRecRole();
  const del = useDeleteRecRole();

  // 失效权限点集合（OPS-R-022）：业务模块卸载后历史绑定残留。
  const invalidPerms = useMemo(
    () => new Set(data?.invalidPermissionCodes ?? []),
    [data?.invalidPermissionCodes],
  );

  // 选中权限点按中文分组渲染（用 catalogTree 拍平补全 name/group；目录缺失则降级到「其他」+ code）。
  const permGroups = useMemo(() => {
    if (!data) return [];
    const byCode = new Map(flattenCatalogLeaves(catalog?.tree ?? []).map((n) => [n.code, n]));
    const map = new Map<string, CatalogLeaf[]>();
    for (const code of data.permissionCodes) {
      const node = byCode.get(code) ?? { code, name: code, description: '', group: '其他' };
      const arr = map.get(node.group);
      if (arr) arr.push(node);
      else map.set(node.group, [node]);
    }
    return [...map.entries()];
  }, [data, catalog]);

  const activationColumns: ColumnsType<RecRoleActivationItem> = [
    {
      title: '租户',
      dataIndex: 'tenantName',
      key: 'tenantName',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '标识',
      dataIndex: 'tenantCode',
      key: 'tenantCode',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '租户内角色名',
      dataIndex: 'roleName',
      key: 'roleName',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '已分配成员',
      dataIndex: 'memberCount',
      key: 'memberCount',
      align: 'right',
      render: (v: number) => v ?? 0,
    },
    {
      title: '客户经理',
      dataIndex: 'csmName',
      key: 'csmName',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '复制时间',
      dataIndex: 'activatedAt',
      key: 'activatedAt',
      render: (v: string) => fmtDate(v),
    },
  ];

  // 已复制租户列表客户端导出 CSV（数据量小、无敏感字段，不另起后端端点）。
  const handleExportActivations = async () => {
    setExporting(true);
    try {
      const all: RecRoleActivationItem[] = [];
      const PAGE = 200;
      let p = 1;
      // 翻页拉全量（单模板复制租户数有限）。
      for (;;) {
        const res = await opsRecommendedRoleAdminApi(http).listActivations(id, {
          page: p,
          pageSize: PAGE,
        });
        all.push(...res.items);
        if (all.length >= res.total || res.items.length === 0) break;
        p += 1;
      }
      if (all.length === 0) {
        void message.info('暂无已复制租户可导出');
        return;
      }
      const headers = ['租户', '标识', '客户经理', '租户内角色名', '已分配成员', '复制时间'];
      const rows = all.map((r) => [
        r.tenantName ?? '',
        r.tenantCode ?? '',
        r.csmName ?? '',
        r.roleName ?? '',
        String(r.memberCount ?? 0),
        fmtDate(r.activatedAt),
      ]);
      const csv =
        '\uFEFF' +
        [headers, ...rows]
          .map((cols) => cols.map((c) => `"${c.replace(/"/g, '""')}"`).join(','))
          .join('\r\n');
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `rec-role-activations-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch {
      void message.error('导出失败，请稍后重试');
    } finally {
      setExporting(false);
    }
  };

  const confirmUnpublish = () => {
    modal.confirm({
      title: '确认下架此推荐角色？',
      content: '下架后不再出现在新租户的推荐区，已复制的客户角色不受影响。',
      okText: '确认下架',
      cancelText: '取消',
      onOk: () => unpublish.mutateAsync(id),
    });
  };

  const confirmDelete = () => {
    modal.confirm({
      title: '确认删除此推荐角色模板？',
      content: '仅在从未被任何租户复制过时允许删除，删除后不可恢复。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await del.mutateAsync(id);
        onDeleted();
      },
    });
  };

  return (
    <div
      style={{
        flex: 1,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--bg)',
        overflow: 'hidden',
      }}
    >
      {isLoading ? (
        <div style={{ padding: 80, textAlign: 'center' }}>
          <Spin size="large" />
        </div>
      ) : isError || !data ? (
        <ErrorState
          title="加载推荐角色详情失败"
          description={error instanceof Error ? error.message : undefined}
          primaryAction={{ label: '重试', onClick: () => void refetch() }}
        />
      ) : (
        <>
          {/* Header */}
          <div
            style={{
              padding: '16px 24px',
              borderBottom: '1px solid var(--divider)',
              display: 'flex',
              alignItems: 'flex-start',
              gap: 12,
              flexShrink: 0,
            }}
          >
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                <span style={{ fontWeight: 600, fontSize: 18 }}>{data.name}</span>
                <StatusPill badge={recRoleStatusBadge(data.status)} />
                <CountChip tone="brand">推荐角色模板</CountChip>
              </div>
              <div
                style={{
                  fontSize: 12,
                  color: 'var(--text-tertiary)',
                  display: 'flex',
                  gap: 12,
                  flexWrap: 'wrap',
                }}
              >
                <span>创建 {fmtDate(data.createdAt)}</span>
                <span>更新 {fmtDate(data.updatedAt)}</span>
              </div>
              {data.description && (
                <div style={{ marginTop: 8, color: 'var(--text-secondary)', fontSize: 13 }}>
                  {data.description}
                </div>
              )}
            </div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <Button icon={<CopyOutlined />} onClick={() => setCopyOpen(true)}>
                复制
              </Button>
              {renderStatusActions()}
              <Button icon={<EditOutlined />} onClick={() => setEditOpen(true)}>
                编辑
              </Button>
              {renderDelete()}
              <Button type="text" icon={<CloseOutlined />} onClick={onClose} aria-label="关闭" />
            </div>
          </div>

          {/* 统计卡 */}
          <div
            style={{
              padding: '16px 24px',
              display: 'grid',
              gridTemplateColumns: 'repeat(4, 1fr)',
              gap: 12,
              flexShrink: 0,
            }}
          >
            <StatCard label="关联套餐包" value={`${data.packageCodes.length} 个`} />
            <StatCard label="权限点数" value={`${data.permissionCodes.length} 项`} />
            <StatCard label="默认数据范围" value={dataScopeLabel(data.dataScope)} />
            <StatCard label="已复制租户" value={`${data.activatedTenantCount} 家`} />
          </div>

          {/* Tabs */}
          <div style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: '0 24px 24px' }}>
            {data.allPackagesArchived && (
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 12 }}
                message="所有关联套餐包均已归档，本模板已自动转为「已下架」，不再出现在新租户的推荐区。如需恢复，请关联未归档的套餐包后重新发布。"
              />
            )}
            <Tabs
              defaultActiveKey="packages"
              items={[
                {
                  key: 'packages',
                  label: `关联套餐包 (${data.packageCodes.length})`,
                  children: (
                    <>
                      <Alert
                        type="info"
                        showIcon
                        style={{ marginBottom: 12 }}
                        message="关联语义：租户启用任意一个关联套餐包后，该模板就出现在其后台的「推荐角色」分组。关联基础包则所有租户都能看到。"
                      />
                      {data.packages.length === 0 ? (
                        <Empty
                          description="未关联任何套餐包"
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                          style={{ padding: '32px 0' }}
                        />
                      ) : (
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                          {data.packages.map((p) => (
                            <CountChip key={p.code}>
                              {p.isSystem && (
                                <span style={{ marginRight: 4 }}>
                                  <CountChip tone="brand">基础</CountChip>
                                </span>
                              )}
                              {p.archived && (
                                <span style={{ marginRight: 4 }}>
                                  <CountChip>已归档</CountChip>
                                </span>
                              )}
                              {p.name}
                              <span
                                style={{
                                  color: 'var(--text-tertiary)',
                                  fontSize: 11,
                                  marginLeft: 6,
                                }}
                              >
                                {p.code}
                              </span>
                              <span
                                style={{
                                  color: 'var(--text-secondary)',
                                  fontSize: 11,
                                  marginLeft: 8,
                                }}
                              >
                                {p.tenantCount} 租户已勾选
                              </span>
                            </CountChip>
                          ))}
                        </div>
                      )}
                    </>
                  ),
                },
                {
                  key: 'permissions',
                  label: `权限点 (${data.permissionCodes.length})`,
                  children:
                    data.permissionCodes.length === 0 ? (
                      <Empty
                        description="未包含任何权限点"
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        style={{ padding: '32px 0' }}
                      />
                    ) : (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                        {invalidPerms.size > 0 && (
                          <Alert
                            type="warning"
                            showIcon
                            message={`有 ${invalidPerms.size} 个权限点已失效（所属业务模块已卸载/下线），复制时将自动跳过。建议编辑模板移除失效项。`}
                          />
                        )}
                        {permGroups.map(([group, nodes]) => (
                          <div
                            key={group}
                            style={{
                              border: '1px solid var(--border)',
                              borderRadius: 'var(--r-md)',
                              overflow: 'hidden',
                            }}
                          >
                            <div
                              style={{
                                padding: '8px 12px',
                                background: 'var(--bg-muted)',
                                fontWeight: 600,
                                fontSize: 13,
                                display: 'flex',
                                alignItems: 'center',
                                gap: 8,
                                borderBottom: '1px solid var(--divider)',
                              }}
                            >
                              {group}
                              <span style={{ color: 'var(--text-tertiary)', fontWeight: 400 }}>
                                {nodes.length}
                              </span>
                            </div>
                            <div
                              style={{
                                padding: 12,
                                display: 'grid',
                                gridTemplateColumns: 'repeat(2, 1fr)',
                                gap: 4,
                              }}
                            >
                              {nodes.map((n) => (
                                <div key={n.code} style={{ fontSize: 13 }}>
                                  {n.name}
                                  <span
                                    style={{
                                      color: 'var(--text-tertiary)',
                                      fontSize: 11,
                                      marginLeft: 6,
                                    }}
                                  >
                                    {n.code}
                                  </span>
                                  {invalidPerms.has(n.code) && (
                                    <span style={{ marginLeft: 6 }}>
                                      <StatusPill badge={toneBadge('warning', '失效')} />
                                    </span>
                                  )}
                                </div>
                              ))}
                            </div>
                          </div>
                        ))}
                      </div>
                    ),
                },
                {
                  key: 'activations',
                  label: `已复制租户 (${data.activatedTenantCount})`,
                  children: (
                    <Table<RecRoleActivationItem>
                      rowKey="tenantId"
                      size="small"
                      loading={actLoading}
                      columns={activationColumns}
                      dataSource={activations?.items ?? []}
                      title={() => (
                        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                          <Button
                            size="small"
                            icon={<DownloadOutlined />}
                            loading={exporting}
                            disabled={(activations?.total ?? 0) === 0}
                            onClick={() => void handleExportActivations()}
                          >
                            导出列表
                          </Button>
                        </div>
                      )}
                      locale={{
                        emptyText: (
                          <Empty
                            description="暂无租户复制此模板"
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            style={{ padding: '24px 0' }}
                          />
                        ),
                      }}
                      pagination={
                        (activations?.total ?? 0) > ACT_PAGE_SIZE
                          ? {
                              current: actPage,
                              pageSize: ACT_PAGE_SIZE,
                              total: activations?.total ?? 0,
                              onChange: setActPage,
                              showSizeChanger: false,
                            }
                          : false
                      }
                    />
                  ),
                },
              ]}
            />
            <Alert
              type="info"
              showIcon
              style={{ marginTop: 8 }}
              message="复制即「快照」：租户从该推荐角色复制后，会在其角色库中创建一个独立角色，与本模板脱钩，运营之后修改本模板不影响已复制角色。"
            />
          </div>

          <OpsRecRoleFormModal open={editOpen} editing={data} onClose={() => setEditOpen(false)} />
          <OpsRecRoleCopyModal
            open={copyOpen}
            source={data}
            onClose={() => setCopyOpen(false)}
            onCopied={onSelect}
          />
        </>
      )}
    </div>
  );

  function renderStatusActions() {
    if (!data) return null;
    switch (data.status) {
      case 'DRAFT':
        return (
          <Button
            type="primary"
            loading={publish.isPending}
            onClick={() => void publish.mutateAsync(id)}
          >
            发布
          </Button>
        );
      case 'PUBLISHED':
        return <Button onClick={confirmUnpublish}>下架</Button>;
      case 'INACTIVE':
        return (
          <Button
            type="primary"
            loading={publish.isPending}
            onClick={() => void publish.mutateAsync(id)}
          >
            重新发布
          </Button>
        );
      default:
        return null;
    }
  }

  function renderDelete() {
    if (!data) return null;
    const disabled = data.activatedTenantCount > 0;
    const btn = (
      <Button danger disabled={disabled} onClick={confirmDelete}>
        删除
      </Button>
    );
    return disabled ? <Tooltip title="已被租户复制过，无法删除，只能下架">{btn}</Tooltip> : btn;
  }
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-md)',
        padding: '12px 16px',
        background: 'var(--bg-elev)',
      }}
    >
      <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 18, fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </div>
    </div>
  );
}

function fmtDate(iso: string): string {
  return formatDate(iso);
}
