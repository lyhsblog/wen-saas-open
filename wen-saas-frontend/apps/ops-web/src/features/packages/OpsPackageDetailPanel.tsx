import { useState } from 'react';
import { App, Button, Spin, Tabs } from 'antd';
import { CloseOutlined, CopyOutlined, EditOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { opsPackageAdminApi } from '@wen-saas/api-client';
import { CountChip, ErrorState, formatDate, PLATFORM_BASE_CAPABILITIES } from '@wen-saas/shared-ui';
import { http } from '../../lib/http';
import { StatusPill } from '@wen-saas/shared-ui';
import { packageStatusBadge, ttlLabel } from './utils/statusMeta';
import { OpsPackageFormModal } from './OpsPackageFormModal';
import { OpsPackageCopyModal } from './OpsPackageCopyModal';
import { TabPackageTenants } from './tabs/TabPackageTenants';
import { TabPackageHistory } from './tabs/TabPackageHistory';
import {
  useArchivePackage,
  useDeletePackage,
  usePublishPackage,
  useUnpublishPackage,
} from './hooks/usePackageMutations';

type Props = {
  code: string;
  onClose: () => void;
  onDeleted: () => void;
  /** 复制成功后选中新套餐。 */
  onSelect: (code: string) => void;
};

export function OpsPackageDetailPanel({ code, onClose, onDeleted, onSelect }: Props) {
  const { modal } = App.useApp();
  const [editOpen, setEditOpen] = useState(false);
  const [copyOpen, setCopyOpen] = useState(false);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['ops', 'package', code],
    queryFn: () => opsPackageAdminApi(http).getPackage(code),
  });
  const { data: modules } = useQuery({
    queryKey: ['ops', 'package-modules'],
    queryFn: () => opsPackageAdminApi(http).listAvailableModules(),
  });
  // 「已勾选租户 N 家」计数：取分页 total（OPS-P-011）。
  const { data: tenantsCount } = useQuery({
    queryKey: ['ops', 'package', code, 'tenants', 'count'],
    queryFn: () => opsPackageAdminApi(http).listPackageTenants(code, { page: 1, pageSize: 1 }),
  });

  const publish = usePublishPackage();
  const unpublish = useUnpublishPackage();
  const archive = useArchivePackage();
  const del = useDeletePackage();

  const moduleName = (mc: string) => modules?.find((m) => m.code === mc)?.name ?? mc;

  const confirmUnpublish = () => {
    modal.confirm({
      title: '确认下架此套餐？',
      content: '下架后新租户无法勾选，但已勾选该套餐的客户继续有效。',
      okText: '确认下架',
      cancelText: '取消',
      onOk: () => unpublish.mutateAsync(code),
    });
  };

  const confirmArchive = () => {
    modal.confirm({
      title: '确认归档此套餐？',
      content: '归档后该套餐将从默认列表隐藏，已勾选客户数据保留。归档不可恢复。',
      okText: '确认归档',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => archive.mutateAsync(code),
    });
  };

  const confirmDelete = () => {
    modal.confirm({
      title: '确认删除此套餐？',
      content: '仅草稿状态可删除，删除后不可恢复。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await del.mutateAsync(code);
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
          title="加载套餐详情失败"
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
                <StatusPill badge={packageStatusBadge(data.status)} />
                {data.isSystem && <CountChip tone="brand">系统</CountChip>}
                {data.sourceModule && (
                  <CountChip tone="brand">来自模块 · {moduleName(data.sourceModule)}</CountChip>
                )}
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
                <code style={{ fontFamily: 'var(--font-mono)' }}>{data.code}</code>
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
                复制套餐
              </Button>
              {renderActions()}
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
            <StatCard label="业务模块" value={`${data.moduleCodes.length} 项`} />
            <StatCard label="默认有效期" value={ttlLabel(data.defaultTtlDays)} />
            <StatCard label="已勾选租户" value={`${tenantsCount?.total ?? 0} 家`} />
            <StatCard label="状态" value={packageStatusBadge(data.status).text} />
          </div>

          {/* Tabs */}
          <div style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: '0 24px 24px' }}>
            <Tabs
              defaultActiveKey="overview"
              items={[
                {
                  key: 'overview',
                  label: '概览',
                  children: (
                    <>
                      <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4 }}>
                        业务模块（按套餐选配）
                      </div>
                      <div
                        style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 10 }}
                      >
                        本套餐额外开通的可选业务模块（如工单 / 合规）。
                      </div>
                      {data.moduleCodes.length === 0 ? (
                        <div
                          style={{ fontSize: 13, color: 'var(--text-secondary)', padding: '8px 0' }}
                        >
                          本套餐未选配额外业务模块，仅含下方「底座基础能力」。
                        </div>
                      ) : (
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                          {data.moduleCodes.map((mc) => (
                            <CountChip key={mc}>
                              {moduleName(mc)}
                              <span
                                style={{
                                  color: 'var(--text-tertiary)',
                                  fontSize: 11,
                                  marginLeft: 6,
                                }}
                              >
                                {mc}
                              </span>
                            </CountChip>
                          ))}
                        </div>
                      )}

                      <div style={{ fontWeight: 600, fontSize: 14, margin: '20px 0 4px' }}>
                        底座基础能力（所有套餐默认包含）
                      </div>
                      <div
                        style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 10 }}
                      >
                        平台框架级能力，无需选配、所有租户开通即享。
                      </div>
                      <div
                        style={{
                          display: 'grid',
                          gridTemplateColumns: 'repeat(2, 1fr)',
                          gap: 8,
                        }}
                      >
                        {PLATFORM_BASE_CAPABILITIES.map((cap) => (
                          <div
                            key={cap.name}
                            style={{
                              padding: '8px 12px',
                              background: 'var(--bg-muted, rgba(0,0,0,0.02))',
                              border: '1px solid var(--border)',
                              borderRadius: 'var(--r-sm, 6px)',
                            }}
                          >
                            <div style={{ fontSize: 13, fontWeight: 500 }}>{cap.name}</div>
                            <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
                              {cap.desc}
                            </div>
                          </div>
                        ))}
                      </div>
                    </>
                  ),
                },
                {
                  key: 'tenants',
                  label: '已勾选租户',
                  children: <TabPackageTenants code={code} />,
                },
                {
                  key: 'history',
                  label: '变更历史',
                  children: <TabPackageHistory code={code} />,
                },
              ]}
            />
          </div>

          <OpsPackageFormModal open={editOpen} editing={data} onClose={() => setEditOpen(false)} />
          <OpsPackageCopyModal
            open={copyOpen}
            source={data}
            onClose={() => setCopyOpen(false)}
            onCopied={onSelect}
          />
        </>
      )}
    </div>
  );

  function renderActions() {
    if (!data) return null;
    const editBtn = (
      <Button icon={<EditOutlined />} onClick={() => setEditOpen(true)}>
        编辑
      </Button>
    );
    if (data.isSystem) {
      // 基础包：仅可编辑名称/描述，禁用状态流转与删除。
      return editBtn;
    }
    switch (data.status) {
      case 'DRAFT':
        return (
          <>
            <Button
              type="primary"
              loading={publish.isPending}
              onClick={() => void publish.mutateAsync(code)}
            >
              上架
            </Button>
            {editBtn}
            {/* 模块预置套餐不可物理删除（重启会重新生成，服务端兜底），仅运营手工包可删 */}
            {!data.sourceModule && (
              <Button danger onClick={confirmDelete}>
                删除
              </Button>
            )}
          </>
        );
      case 'ACTIVE':
        return (
          <>
            <Button onClick={confirmUnpublish}>下架</Button>
            {editBtn}
          </>
        );
      case 'INACTIVE':
        return (
          <>
            <Button
              type="primary"
              loading={publish.isPending}
              onClick={() => void publish.mutateAsync(code)}
            >
              重新上架
            </Button>
            <Button danger onClick={confirmArchive}>
              归档
            </Button>
            {editBtn}
          </>
        );
      case 'ARCHIVED':
        return <span style={{ color: 'var(--text-tertiary)', fontSize: 13 }}>已归档（只读）</span>;
      default:
        return editBtn;
    }
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
