import { useMemo, useState, type ComponentProps, type ReactNode } from 'react';
import {
  App,
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Select,
  Table,
  Tag,
  Typography,
} from 'antd';
import { CloseCircleFilled, CopyOutlined, SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/lib/table';
import {
  FilterBar,
  formatDateTime,
  httpStatusBadge,
  shouldSkipRowNav,
  StatusPill,
  type FilterBarChip,
} from '@wen-saas/shared-ui';
import { WaPage, WaPageHead } from '../../components/wa';
import { useOperationLogDetail, useOperationLogs } from './auditQueries';
import { entityLookupApi, useEntityTypes } from './entityLookupApi';
import { ObjectPickerModal, type PickedObject } from './ObjectPickerModal';
import type { AuditScope, LogQuery, OperationLogItem } from './auditApi';

const { RangePicker } = DatePicker;
type RangeValue = ComponentProps<typeof RangePicker>['value'];

/** 「对象」列只展示对象本身（名称优先，回退 id）；对象类型单独成列。 */
function objectValue(row: OperationLogItem): string {
  return row.objectDesc ?? row.objectId ?? '—';
}

type Props = {
  scope: AuditScope;
  title: string;
  hint: string;
  /** 页头右侧动作区（如审计日志页的「导出」入口）。 */
  actions?: ReactNode;
};

type OperatorOption = { value: string; label: string };

const PAGE_SIZE = 20;

export function OperationLogView({ scope, title, hint, actions }: Props) {
  const { message } = App.useApp();
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<OperationLogItem | null>(null);

  // 筛选条件（仅审计日志页 scope==='tenant' 展示筛选区）。
  // 草稿（编辑中）与已提交（驱动查询）分离：只有点「查询」才提交（与客户管理同一套交互范式）。
  const showFilters = scope === 'tenant';
  const [rangeDraft, setRangeDraft] = useState<RangeValue>(null);
  const [operatorDraft, setOperatorDraft] = useState<OperatorOption | null>(null);
  const [actionDraft, setActionDraft] = useState('');
  const [objectTypeDraft, setObjectTypeDraft] = useState<string | undefined>(undefined);
  const [pickedDraft, setPickedDraft] = useState<PickedObject | null>(null);

  const [range, setRange] = useState<RangeValue>(null);
  const [operator, setOperator] = useState<OperatorOption | null>(null);
  const [operatorOptions, setOperatorOptions] = useState<OperatorOption[]>([]);
  const [operatorSearching, setOperatorSearching] = useState(false);
  const [action, setAction] = useState('');
  const [objectType, setObjectType] = useState<string | undefined>(undefined);
  const [picked, setPicked] = useState<PickedObject | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);

  // 始终拉类型表：tenant scope 喂筛选区，mine scope 仅用于把对象类型 code 映射成中文列。
  const { data: entityTypes } = useEntityTypes();

  // object_type 列存实体 code，前端按 entity-lookup 映射成中文展示。
  const typeLabel = useMemo(() => {
    const m = new Map<string, string>();
    (entityTypes ?? []).forEach((t) => m.set(t.code, t.label));
    return m;
  }, [entityTypes]);
  const renderObjectType = (v: string | null) => (v ? (typeLabel.get(v) ?? v) : '—');

  const query: LogQuery = useMemo(
    () => ({
      page,
      size: PAGE_SIZE,
      // 仅日期：起 = 当日 00:00；止 = 次日 00:00（后端 createdAt < endAt，含整个结束日）
      startAt: range?.[0]?.startOf('day').toISOString(),
      endAt: range?.[1]?.add(1, 'day').startOf('day').toISOString(),
      operatorId: operator?.value,
      action: action.trim() || undefined,
      objectType: objectType || undefined,
      objectId: picked?.id,
    }),
    [page, range, operator, action, objectType, picked],
  );

  const { data, isLoading } = useOperationLogs(scope, query);
  const detail = useOperationLogDetail(scope, selected?.id ?? null, selected?.createdAt ?? null);

  const applySearch = () => {
    setRange(rangeDraft);
    setOperator(operatorDraft);
    setAction(actionDraft.trim());
    setObjectType(objectTypeDraft);
    setPicked(pickedDraft);
    setPage(1);
  };
  const resetSearch = () => {
    setRangeDraft(null);
    setOperatorDraft(null);
    setActionDraft('');
    setObjectTypeDraft(undefined);
    setPickedDraft(null);
    setOperatorOptions([]);
    setRange(null);
    setOperator(null);
    setAction('');
    setObjectType(undefined);
    setPicked(null);
    setPage(1);
  };

  // 用通用实体反查（登录即可，无权限门控），不要改回 /api/v1/base/members/search——
  // 那个端点挂 org.member.list，没该权限的角色（如普通审计查看者）筛选会直接 403。
  const searchOperators = async (kw: string) => {
    if (!kw.trim()) {
      setOperatorOptions([]);
      return;
    }
    setOperatorSearching(true);
    try {
      const hits = await entityLookupApi.search('sys.user', kw);
      setOperatorOptions(hits.map((h) => ({ value: h.id, label: h.display })));
    } catch {
      // 静默：检索失败不打断筛选
    } finally {
      setOperatorSearching(false);
    }
  };

  const columns: ColumnsType<OperationLogItem> = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (v: string) => formatDateTime(v),
    },
    { title: '操作', dataIndex: 'action', width: 150, render: (v: string | null) => v || '—' },
    {
      title: '对象类型',
      dataIndex: 'objectType',
      width: 120,
      render: renderObjectType,
    },
    { title: '对象', key: 'object', render: (_, row) => objectValue(row) },
    {
      title: '操作人',
      dataIndex: 'operatorName',
      width: 130,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '状态',
      dataIndex: 'statusCode',
      width: 80,
      render: (v: number | null) => <StatusPill badge={httpStatusBadge(v)} />,
    },
  ];

  const typeOptions = (entityTypes ?? []).map((t) => ({ value: t.code, label: t.label }));

  // 「更多筛选」收起时以 chips 回显已生效（已提交）的对象类型/对象条件；点 × 撤销该条件并重查。
  const filterChips: FilterBarChip[] = [
    ...(objectType
      ? [
          {
            key: 'objectType',
            text: `对象类型：${typeLabel.get(objectType) ?? objectType}`,
            onRemove: () => {
              // 与筛选区清空类型同语义：类型清掉时已选对象一并清掉，保持类型↔对象一致。
              setObjectTypeDraft(undefined);
              setPickedDraft(null);
              setObjectType(undefined);
              setPicked(null);
              setPage(1);
            },
          },
        ]
      : []),
    ...(picked
      ? [
          {
            key: 'object',
            text: `对象：${picked.display}`,
            onRemove: () => {
              setPickedDraft(null);
              setPicked(null);
              setPage(1);
            },
          },
        ]
      : []),
  ];

  return (
    <WaPage>
      <WaPageHead title={title} hint={hint} actions={actions} />

      {/* 筛选区：shared-ui FilterBar 栅格排布（对齐客户管理）；草稿受控与「查询」提交语义仍在本组件。 */}
      {showFilters && (
        <FilterBar
          style={{
            border: '1px solid var(--border)',
            borderRadius: 'var(--r-lg)',
            boxShadow: 'var(--shadow-sm)',
          }}
          fields={[
            {
              key: 'range',
              label: '时间范围',
              control: (
                <RangePicker
                  style={{ width: '100%' }}
                  value={rangeDraft ?? null}
                  onChange={(v) => setRangeDraft(v)}
                  placeholder={['开始日期', '结束日期']}
                />
              ),
            },
            {
              key: 'operator',
              label: '操作人',
              control: (
                <Select<OperatorOption>
                  labelInValue
                  showSearch
                  allowClear
                  placeholder="输入姓名 / 账号检索"
                  style={{ width: '100%' }}
                  filterOption={false}
                  notFoundContent={operatorSearching ? '搜索中…' : '输入姓名 / 账号检索'}
                  options={operatorOptions}
                  value={operatorDraft}
                  onSearch={(kw) => void searchOperators(kw)}
                  onChange={(v) => setOperatorDraft(v ?? null)}
                />
              ),
            },
            {
              key: 'action',
              label: '操作',
              control: (
                <Input
                  placeholder="精确匹配"
                  allowClear
                  style={{ width: '100%' }}
                  value={actionDraft}
                  onChange={(e) => setActionDraft(e.target.value)}
                  onPressEnter={applySearch}
                />
              ),
            },
          ]}
          moreFields={[
            {
              key: 'objectType',
              label: '对象类型',
              control: (
                <Select
                  options={typeOptions}
                  value={objectTypeDraft}
                  // 改/清类型时，已选对象若不属于新类型则一并清掉，保持类型↔对象一致。
                  onChange={(v?: string) => {
                    setObjectTypeDraft(v || undefined);
                    setPickedDraft((cur) => (cur && cur.typeCode === v ? cur : null));
                  }}
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择类型"
                  style={{ width: '100%' }}
                />
              ),
            },
            {
              key: 'object',
              label: '对象',
              control: pickedDraft ? (
                <Tag
                  closable
                  closeIcon={<CloseCircleFilled />}
                  onClose={() => setPickedDraft(null)}
                  style={{ padding: '4px 8px', margin: 0, alignSelf: 'flex-start' }}
                >
                  {pickedDraft.display}
                </Tag>
              ) : (
                <Button
                  block
                  onClick={() => setPickerOpen(true)}
                  disabled={(entityTypes ?? []).length === 0}
                >
                  选择对象
                </Button>
              ),
            },
          ]}
          chips={filterChips}
          actions={
            <>
              <Button type="primary" icon={<SearchOutlined />} onClick={applySearch}>
                查询
              </Button>
              <Button onClick={resetSearch}>重置</Button>
            </>
          }
        />
      )}

      <Table<OperationLogItem>
        columns={columns}
        dataSource={data?.records}
        rowKey="id"
        loading={isLoading}
        size="small"
        onRow={(row) => ({
          onClick: () => {
            // 划词选中文字时不打开详情，让原生复制正常工作。
            if (shouldSkipRowNav()) return;
            setSelected(row);
          },
          style: { cursor: 'pointer' },
        })}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: data?.total ?? 0,
          showTotal: (n) => `共 ${n} 条`,
          onChange: setPage,
          showSizeChanger: false,
        }}
        locale={{ emptyText: <Empty description="暂无记录" /> }}
      />

      {showFilters && pickerOpen && (
        <ObjectPickerModal
          open
          types={entityTypes ?? []}
          defaultTypeCode={objectTypeDraft}
          onPick={(obj) => {
            // 选中对象同时回填「对象类型」(code) 与「对象」(id)，仍是草稿，点「查询」才生效。
            setObjectTypeDraft(obj.typeCode);
            setPickedDraft(obj);
            setPickerOpen(false);
          }}
          onClose={() => setPickerOpen(false)}
        />
      )}

      <Drawer
        title="操作详情"
        width={560}
        open={!!selected}
        onClose={() => setSelected(null)}
        destroyOnHidden
      >
        {selected && (
          <Descriptions column={1} size="small" bordered styles={{ label: { width: 96 } }}>
            <Descriptions.Item label="时间">{formatDateTime(selected.createdAt)}</Descriptions.Item>
            <Descriptions.Item label="操作">{selected.action || '—'}</Descriptions.Item>
            <Descriptions.Item label="对象类型">
              {renderObjectType(selected.objectType)}
            </Descriptions.Item>
            <Descriptions.Item label="对象">{objectValue(selected)}</Descriptions.Item>
            <Descriptions.Item label="操作人">{selected.operatorName ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="模块">{selected.module ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="状态码">
              <StatusPill badge={httpStatusBadge(selected.statusCode)} />
            </Descriptions.Item>
            {detail.data?.path && (
              <Descriptions.Item label="路径">
                <Typography.Text style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>
                  {detail.data.method} {detail.data.path}
                </Typography.Text>
              </Descriptions.Item>
            )}
            {selected.ip && <Descriptions.Item label="IP">{selected.ip}</Descriptions.Item>}
            {selected.requestId && (
              <Descriptions.Item label="Request ID">
                <Typography.Text
                  style={{ fontFamily: 'var(--font-mono)', fontSize: 11, cursor: 'pointer' }}
                  onClick={() => {
                    void navigator.clipboard
                      .writeText(selected.requestId!)
                      .then(() => message.success('已复制'));
                  }}
                >
                  <CopyOutlined style={{ marginRight: 4, opacity: 0.5 }} />
                  {selected.requestId}
                </Typography.Text>
              </Descriptions.Item>
            )}
            {detail.data?.errorMessage && (
              <Descriptions.Item label="错误">
                <Typography.Text type="danger">{detail.data.errorMessage}</Typography.Text>
              </Descriptions.Item>
            )}
          </Descriptions>
        )}

        {detail.data && detail.data.dataChanges.length > 0 && (
          <div style={{ marginTop: 20 }}>
            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 10 }}>数据变更</div>
            {detail.data.dataChanges.map((dc, i) => (
              <div key={`${dc.tableName}-${dc.rowPk}-${i}`} style={{ marginBottom: 12 }}>
                <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 4 }}>
                  {dc.tableName} · {dc.op}
                </div>
                {dc.fields.map((f) => (
                  <div key={f.column} style={{ fontSize: 13, padding: '2px 0' }}>
                    <span style={{ color: 'var(--text-secondary)' }}>{f.label}：</span>
                    <span style={{ color: 'var(--text-tertiary)' }}>{f.before ?? '空'}</span>
                    <span style={{ margin: '0 6px', color: 'var(--text-tertiary)' }}>→</span>
                    <span style={{ color: 'var(--text)', fontWeight: 500 }}>{f.after ?? '空'}</span>
                  </div>
                ))}
              </div>
            ))}
          </div>
        )}
      </Drawer>
    </WaPage>
  );
}
