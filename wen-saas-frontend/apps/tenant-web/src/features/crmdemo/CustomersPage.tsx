import { useMemo, useState } from 'react';
import { App, Button, Dropdown, Input, Select, Space } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import {
  DataTable,
  FilterBar,
  formatDateTime,
  RowTextAction,
  StatusPill,
  useCloseOnTabLeave,
  type PrefColumn,
} from '@wen-saas/shared-ui';
import {
  describeApiError,
  type CrmCustomerListItem,
  type CrmCustomerListQuery,
} from '@wen-saas/api-client';
import { useCustomFieldColumns } from '../customfield/useCustomFieldColumns';
import { useCustomFieldFilterFields } from '../customfield/CustomFieldFilters';
import { EntityRefPicker } from '../customfield/EntityRefPicker';
import { ColumnSettingsButton } from '../columnpref/ColumnSettings';
import { useColumnPreference } from '../columnpref/useColumnPreference';
import { DictEnumSelect } from '../customfield/DictEnumSelect';
import { PermissionGate } from '@wen-saas/permissions';
import { ExportEntryButton } from '../taskCenter/ExportEntryButton';
import { ImportEntryButton } from '../taskCenter/ImportEntryButton';
import { CRM_CUSTOMER_OBJECT } from './crmApi';
import { useCustomerList, useDeleteCustomer, useTransitionCustomer } from './crmQueries';
import { CRM_SOURCE_DICT, useSourceName } from './crmSource';
import { STATUS_META, STATUS_OPTIONS, statusBadge } from './crmStatus';
import { CustomerFormModal } from './CustomerFormModal';
import { CustomerDetailDrawer } from './CustomerDetailDrawer';

/**
 * 租户后台 · 客户管理（全部客户，数据范围由角色数据权限自动叠加）。
 * 与「我的客户」分成两个独立页面：管理视角多一个「归属人」筛选，我的客户里这个维度没意义（恒等于自己）。
 *
 * 「归属人」筛选用通用实体反查 `EntityRefPicker objectCode="sys.user"`，不要改回
 * `/api/v1/base/members/search`——那个端点挂 `org.member.list` 权限，CRM 角色未必有，会导致筛选直接 403。
 */
export function CustomersPage() {
  const { message, modal } = App.useApp();

  // 草稿（编辑中）与已提交（驱动查询）分离：只有点「搜索」才提交（B 端交互规范）
  const [statusDraft, setStatusDraft] = useState<string | undefined>(undefined);
  const [keywordDraft, setKeywordDraft] = useState('');
  const [ownerDraft, setOwnerDraft] = useState<string | undefined>(undefined);
  const [sourceDraft, setSourceDraft] = useState<string | undefined>(undefined);
  const [cfDraft, setCfDraft] = useState<Record<string, string>>({});
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [ownerId, setOwnerId] = useState<string | undefined>(undefined);
  const [source, setSource] = useState<string | undefined>(undefined);
  const [cfEquals, setCfEquals] = useState<Record<string, string>>({});

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);

  const [detailId, setDetailId] = useState<string | null>(null);
  const [form, setForm] = useState<{ open: boolean; editId: string | null }>({
    open: false,
    editId: null,
  });

  // 切到其它页签后自动关闭，避免再切回客户列表以外的页面时弹窗仍悬浮在上方。
  useCloseOnTabLeave(form.open, () => setForm({ open: false, editId: null }));
  useCloseOnTabLeave(detailId !== null, () => setDetailId(null));

  const applySearch = () => {
    setStatus(statusDraft);
    setKeyword(keywordDraft.trim());
    setOwnerId(ownerDraft);
    setSource(sourceDraft);
    setCfEquals(cfDraft);
    setPage(1);
  };
  const resetSearch = () => {
    setStatusDraft(undefined);
    setKeywordDraft('');
    setOwnerDraft(undefined);
    setSourceDraft(undefined);
    setCfDraft({});
    setStatus(undefined);
    setKeyword('');
    setOwnerId(undefined);
    setSource(undefined);
    setCfEquals({});
    setPage(1);
  };

  const query: CrmCustomerListQuery = { page, size };
  if (status) query.status = status;
  if (keyword) query.keyword = keyword;
  if (ownerId) query.ownerId = ownerId;
  if (source) query.source = source;
  const listQuery = useCustomerList(query, cfEquals);
  const records = listQuery.data?.records ?? [];
  const total = Number(listQuery.data?.total ?? 0);

  const del = useDeleteCustomer();
  const transition = useTransitionCustomer();

  const doTransition = async (id: string, target: string) => {
    try {
      await transition.mutateAsync({ id, target });
      message.success('已推进状态');
    } catch (e) {
      message.error(describeApiError(e, '状态推进失败'));
    }
  };
  const doDelete = (row: CrmCustomerListItem) => {
    modal.confirm({
      title: `删除客户「${row.name}」？`,
      content: '删除后该客户数据不可恢复。',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await del.mutateAsync(row.id);
          message.success('已删除');
        } catch (e) {
          message.error(describeApiError(e, '删除失败'));
        }
      },
    });
  };

  const cfCols = useCustomFieldColumns<CrmCustomerListItem>(CRM_CUSTOMER_OBJECT);
  const sourceName = useSourceName();
  const cfFilterFields = useCustomFieldFilterFields(CRM_CUSTOMER_OBJECT, cfDraft, setCfDraft);
  // 收起「更多筛选」时以 chips 回显已生效的自定义字段条件；点 × 即撤销该条件并重查
  const cfChips = Object.entries(cfEquals).map(([code, v]) => ({
    key: code,
    text: `${cfFilterFields.find((f) => f.key === code)?.label ?? code}：${v}`,
    onRemove: () => {
      const nextDraft = { ...cfDraft };
      delete nextDraft[code];
      setCfDraft(nextDraft);
      const next = { ...cfEquals };
      delete next[code];
      setCfEquals(next);
      setPage(1);
    },
  }));

  const standardCols: PrefColumn<CrmCustomerListItem>[] = useMemo(
    () => [
      { key: 'name', title: '客户名称', dataIndex: 'name', pin: 'left' },
      { key: 'code', title: '客户编号', dataIndex: 'code' },
      {
        key: 'status',
        title: '阶段',
        dataIndex: 'status',
        render: (_v: unknown, r: CrmCustomerListItem) => (
          <StatusPill badge={statusBadge(r.status, r.statusLabel)} />
        ),
      },
      {
        key: 'source',
        title: '来源',
        dataIndex: 'source',
        render: (_v: unknown, r: CrmCustomerListItem) => sourceName(r.source),
      },
      {
        key: 'ownerName',
        title: '负责人',
        dataIndex: 'ownerName',
        render: (_v: unknown, r: CrmCustomerListItem) => r.ownerName ?? r.ownerId ?? '-',
      },
      {
        key: 'createdAt',
        title: '创建时间',
        dataIndex: 'createdAt',
        render: (_v: unknown, r: CrmCustomerListItem) => formatDateTime(r.createdAt),
      },
      {
        key: 'actions',
        title: '操作',
        pin: 'right',
        render: (_v: unknown, r: CrmCustomerListItem) => {
          const next = STATUS_META[r.status]?.next ?? [];
          return (
            <Space size="middle">
              <RowTextAction onClick={() => setDetailId(r.id)}>详情</RowTextAction>
              <PermissionGate permission="crm_demo.customer.edit">
                <Space size="middle">
                  <RowTextAction onClick={() => setForm({ open: true, editId: r.id })}>
                    编辑
                  </RowTextAction>
                  {next.length > 0 && (
                    <Dropdown
                      menu={{
                        items: next.map((s) => ({
                          key: s,
                          label: `推进到「${STATUS_META[s]?.label ?? s}」`,
                          onClick: () => doTransition(r.id, s),
                        })),
                      }}
                    >
                      <Button type="link" size="small" style={{ padding: 0 }}>
                        推进
                      </Button>
                    </Dropdown>
                  )}
                  <RowTextAction danger onClick={() => doDelete(r)}>
                    删除
                  </RowTextAction>
                </Space>
              </PermissionGate>
            </Space>
          );
        },
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [sourceName],
  );

  const allColumns = useMemo(() => [...standardCols, ...cfCols], [standardCols, cfCols]);
  const { columns, panel, isCustomized, saving, apply, reset } = useColumnPreference(
    'crm_demo.customer',
    allColumns,
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
      {/* 顶部：标题 + 操作区（列设置 / 新增） */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '12px 24px',
          borderBottom: '1px solid var(--divider)',
        }}
      >
        <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--text)' }}>客户管理</div>
        <ColumnSettingsButton
          panel={panel}
          isCustomized={isCustomized}
          saving={saving}
          onApply={apply}
          onReset={reset}
        />
        <div style={{ flex: 1 }} />
        <PermissionGate permission="crm_demo.customer.view_manage">
          <ExportEntryButton
            objectCode="crm_demo_customer"
            label="导出"
            filters={{
              ...(status ? { status } : {}),
              ...(source ? { source } : {}),
              ...(keyword ? { keyword } : {}),
              ...(ownerId ? { ownerId } : {}),
              // 自定义字段等值条件与列表查询同源（cf_* 键，后端导出同样消费）
              ...cfEquals,
            }}
            filterSummary={[
              status ? `阶段=${status}` : '',
              source ? `来源=${source}` : '',
              keyword ? `关键字=${keyword}` : '',
              ownerId ? `归属人ID=${ownerId}` : '',
              ...Object.entries(cfEquals).map(
                ([code, v]) => `${cfFilterFields.find((f) => f.key === code)?.label ?? code}=${v}`,
              ),
            ]
              .filter(Boolean)
              .join(' ')}
          />
        </PermissionGate>
        <PermissionGate permission="crm_demo.customer.edit">
          <ImportEntryButton entityCode="crm_demo.customer" label="导入" />
        </PermissionGate>
        <PermissionGate permission="crm_demo.customer.edit">
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setForm({ open: true, editId: null })}
          >
            新增客户
          </Button>
        </PermissionGate>
      </div>

      {/* 筛选区：标题下方，栅格布局；自定义字段折叠在「更多筛选」，统一「搜索」按钮提交 */}
      <FilterBar
        style={{ borderBottom: '1px solid var(--divider)' }}
        fields={[
          {
            key: 'status',
            label: '阶段',
            control: (
              <Select
                allowClear
                placeholder="请选择"
                style={{ width: '100%' }}
                value={statusDraft}
                onChange={(v) => setStatusDraft(v)}
                options={STATUS_OPTIONS}
              />
            ),
          },
          {
            key: 'keyword',
            label: '名称/编号',
            control: (
              <Input
                allowClear
                placeholder="请输入"
                style={{ width: '100%' }}
                value={keywordDraft}
                onChange={(e) => setKeywordDraft(e.target.value)}
                onPressEnter={applySearch}
              />
            ),
          },
          {
            key: 'owner',
            label: '归属人',
            control: (
              <EntityRefPicker
                objectCode="sys.user"
                placeholder="输入姓名检索"
                style={{ width: '100%' }}
                {...(ownerDraft ? { value: ownerDraft } : {})}
                onChange={setOwnerDraft}
              />
            ),
          },
          {
            key: 'source',
            label: '来源',
            control: (
              <DictEnumSelect
                dictCode={CRM_SOURCE_DICT}
                allowClear
                style={{ width: '100%' }}
                {...(sourceDraft ? { value: sourceDraft } : {})}
                onChange={setSourceDraft}
              />
            ),
          },
        ]}
        moreFields={cfFilterFields}
        chips={cfChips}
        actions={
          <>
            <Button type="primary" icon={<SearchOutlined />} onClick={applySearch}>
              搜索
            </Button>
            <Button onClick={resetSearch}>重置</Button>
          </>
        }
      />

      <div style={{ flex: 1, minHeight: 0 }}>
        <DataTable<CrmCustomerListItem>
          columns={columns}
          dataSource={records}
          loading={listQuery.isLoading}
          onRowClick={(r) => setDetailId(r.id)}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            showTotal: (n) => `共 ${n} 条`,
            onChange: (p, s) => {
              setPage(p);
              setSize(s);
            },
          }}
        />
      </div>

      <CustomerFormModal
        open={form.open}
        editId={form.editId}
        onClose={() => setForm({ open: false, editId: null })}
      />
      <CustomerDetailDrawer id={detailId} onClose={() => setDetailId(null)} />
    </div>
  );
}
