import { useMemo, useState, type ComponentProps } from 'react';
import { Button, DatePicker, Popover, Select, Space, Table, Typography } from 'antd';
import { formatDateTime, httpStatusBadge, StatusPill } from '@wen-saas/shared-ui';
import type { ApiCallLogItem } from '@wen-saas/api-client';
import { useApiCallLogs, useApiKeyList } from './apiKeyQueries';

const { RangePicker } = DatePicker;
type RangeValue = ComponentProps<typeof RangePicker>['value'];

const { Text } = Typography;

/** 常见状态码选项（后端为精确匹配）。 */
const STATUS_OPTIONS = [200, 201, 400, 401, 402, 403, 404, 409, 422, 429, 500].map((v) => ({
  value: v,
  label: String(v),
}));

function statusTag(code: number | null) {
  return <StatusPill badge={httpStatusBadge(code)} />;
}

/** 调用日志 tab：开放 API 网关按凭证 / 状态码 / 时间窗过滤的分页日志。 */
export function ApiCallLogsPanel() {
  const [apiKeyId, setApiKeyId] = useState<string | undefined>();
  const [statusCode, setStatusCode] = useState<number | undefined>();
  const [range, setRange] = useState<RangeValue>(null);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);

  const keysQuery = useApiKeyList();
  const keyName = useMemo(() => {
    const m = new Map<string, string>();
    (keysQuery.data ?? []).forEach((k) => m.set(k.id, k.name));
    return m;
  }, [keysQuery.data]);

  const logsQuery = useApiCallLogs({
    ...(apiKeyId ? { apiKeyId } : {}),
    ...(statusCode != null ? { statusCode } : {}),
    ...(range?.[0] ? { from: range[0].format('YYYY-MM-DDTHH:mm:ss') } : {}),
    ...(range?.[1] ? { to: range[1].format('YYYY-MM-DDTHH:mm:ss') } : {}),
    page,
    size,
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <Space wrap>
        <Select
          allowClear
          placeholder="凭证"
          style={{ width: 200 }}
          options={(keysQuery.data ?? []).map((k) => ({ value: k.id, label: k.name }))}
          value={apiKeyId}
          onChange={(v) => {
            setApiKeyId(v);
            setPage(1);
          }}
        />
        <Select
          allowClear
          placeholder="状态码"
          style={{ width: 110 }}
          options={STATUS_OPTIONS}
          value={statusCode}
          onChange={(v) => {
            setStatusCode(v);
            setPage(1);
          }}
        />
        <RangePicker
          showTime
          value={range ?? null}
          onChange={(v) => {
            setRange(v);
            setPage(1);
          }}
        />
      </Space>
      <Table<ApiCallLogItem>
        rowKey="id"
        size="small"
        loading={logsQuery.isLoading}
        dataSource={logsQuery.data?.records ?? []}
        pagination={{
          current: page,
          pageSize: size,
          total: logsQuery.data?.total ?? 0,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
          },
        }}
        columns={[
          {
            title: '时间',
            dataIndex: 'createdAt',
            width: 165,
            render: (v: string) => formatDateTime(v),
          },
          {
            title: '凭证',
            dataIndex: 'apiKeyId',
            width: 140,
            render: (v: string | null) =>
              v ? (keyName.get(v) ?? v) : <Text type="secondary">匿名</Text>,
          },
          { title: 'Method', dataIndex: 'method', width: 80 },
          {
            title: '路径',
            dataIndex: 'path',
            ellipsis: true,
            render: (v: string) => (
              <Text style={{ fontFamily: 'monospace', fontSize: 12 }}>{v}</Text>
            ),
          },
          {
            title: '状态',
            dataIndex: 'statusCode',
            width: 80,
            render: statusTag,
          },
          {
            title: '耗时',
            dataIndex: 'durationMs',
            width: 90,
            render: (v: number | null) => (v == null ? '-' : `${v}ms`),
          },
          {
            title: '模块',
            dataIndex: 'moduleCode',
            width: 110,
            render: (v: string | null) => v ?? '-',
          },
          { title: 'IP', dataIndex: 'ip', width: 130 },
          {
            title: '错误',
            dataIndex: 'errorMessage',
            width: 70,
            // 点击展开可选中复制的错误全文（Popover 而非 Tooltip：触屏 / 键盘可达）
            render: (v: string | null) =>
              v ? (
                <Popover
                  trigger="click"
                  placement="topRight"
                  content={
                    <pre style={{ margin: 0, maxWidth: 420, whiteSpace: 'pre-wrap' }}>{v}</pre>
                  }
                >
                  <Button type="link" size="small" style={{ padding: 0 }}>
                    详情
                  </Button>
                </Popover>
              ) : (
                '-'
              ),
          },
        ]}
      />
    </div>
  );
}
