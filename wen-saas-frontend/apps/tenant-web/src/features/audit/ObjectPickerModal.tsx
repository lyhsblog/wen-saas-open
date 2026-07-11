import { useMemo, useState } from 'react';
import { Button, Empty, Input, List, Modal, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/lib/table';
import { describeApiError } from '@wen-saas/api-client';
import { shouldSkipRowNav } from '@wen-saas/shared-ui';
import { App } from 'antd';
import {
  entityLookupApi,
  searchPlaceholder,
  type EntityHit,
  type EntityTypeMeta,
} from './entityLookupApi';

export type PickedObject = {
  id: string;
  display: string;
  typeCode: string;
  typeLabel: string;
};

type Props = {
  open: boolean;
  types: EntityTypeMeta[];
  /** 外层「对象类型」筛选的 code，用于默认选中左侧类型。 */
  defaultTypeCode?: string | undefined;
  onPick: (obj: PickedObject) => void;
  onClose: () => void;
};

/**
 * 对象选择器：左侧选实体类型、右侧按单关键字搜（OR 命中该类型可搜字段，≤20，无分页）。
 *
 * <p>object_type 与对象同属一套实体 code 体系，故选中对象后由调用方
 * 同时回填外层「对象类型」(code) 与「对象」(object_id) 两个筛选；左侧「类型」即对象类型。
 */
export function ObjectPickerModal({ open, types, defaultTypeCode, onPick, onClose }: Props) {
  const { message } = App.useApp();
  // 父组件按 open 条件挂载本组件，故初始态在挂载时一次性算定（按外层 code 默认选中类型，否则取第一个）。
  const initialCode =
    (defaultTypeCode ? types.find((t) => t.code === defaultTypeCode) : undefined)?.code ??
    types[0]?.code ??
    null;
  const [activeCode, setActiveCode] = useState<string | null>(initialCode);
  const [keyword, setKeyword] = useState('');
  const [rows, setRows] = useState<EntityHit[]>([]);
  const [searched, setSearched] = useState(false);
  const [loading, setLoading] = useState(false);

  const activeType = useMemo(
    () => types.find((t) => t.code === activeCode) ?? null,
    [types, activeCode],
  );

  const runSearch = async () => {
    if (!activeType) return;
    if (!keyword.trim()) {
      message.info(searchPlaceholder(activeType.fields));
      return;
    }
    setLoading(true);
    try {
      const hits = await entityLookupApi.search(activeType.code, keyword.trim());
      setRows(hits);
      setSearched(true);
    } catch (err) {
      message.error(describeApiError(err, '搜索失败'));
    } finally {
      setLoading(false);
    }
  };

  const switchType = (t: EntityTypeMeta) => {
    setActiveCode(t.code);
    setKeyword('');
    setRows([]);
    setSearched(false);
  };

  const primaryLabel = activeType?.fields[0]?.label ?? '名称';
  const secondaryLabel = activeType?.fields[1]?.label;
  const columns: ColumnsType<EntityHit> = [
    {
      title: primaryLabel,
      dataIndex: 'primary',
      render: (v: string | null, row) => v ?? row.display,
    },
    ...(secondaryLabel
      ? [
          {
            title: secondaryLabel,
            dataIndex: 'secondary',
            width: 140,
            render: (v: string | null) => v ?? '—',
          },
        ]
      : []),
    {
      title: 'ID',
      dataIndex: 'id',
      width: 180,
      render: (v: string) => (
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>{v}</span>
      ),
    },
  ];

  return (
    <Modal open={open} title="选择对象" width={720} onCancel={onClose} footer={null} destroyOnHidden>
      <div style={{ display: 'flex', gap: 16, minHeight: 360 }}>
        <div
          style={{
            width: 176,
            flexShrink: 0,
            padding: 10,
            background: 'var(--bg-muted)',
            border: '1px solid var(--border)',
            borderRadius: 'var(--r-lg)',
          }}
        >
          <div
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: 'var(--text-tertiary)',
              padding: '2px 6px 10px',
              letterSpacing: 0.3,
            }}
          >
            对象类型
          </div>
          <List
            size="small"
            split={false}
            dataSource={types}
            locale={{
              emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无可搜索类型" />,
            }}
            renderItem={(t) => {
              const on = t.code === activeCode;
              return (
                <List.Item
                  onClick={() => switchType(t)}
                  onMouseEnter={(e) => {
                    if (!on) e.currentTarget.style.background = 'var(--brand-bg)';
                  }}
                  onMouseLeave={(e) => {
                    if (!on) e.currentTarget.style.background = 'transparent';
                  }}
                  style={{
                    cursor: 'pointer',
                    padding: '7px 10px',
                    marginBottom: 2,
                    borderRadius: 'var(--r-md)',
                    fontSize: 13,
                    transition: 'background .15s, color .15s',
                    background: on ? 'var(--bg-elev)' : 'transparent',
                    color: on ? 'var(--brand)' : 'var(--text-secondary)',
                    fontWeight: on ? 600 : 400,
                    boxShadow: on ? 'var(--shadow-sm)' : 'none',
                  }}
                >
                  {t.label}
                </List.Item>
              );
            }}
          />
        </div>

        <div style={{ flex: 1, minWidth: 0 }}>
          <Space style={{ marginBottom: 12 }} wrap>
            <Input
              placeholder={searchPlaceholder(activeType?.fields)}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={() => void runSearch()}
              allowClear
              style={{ width: 260 }}
            />
            <Button type="primary" onClick={() => void runSearch()} loading={loading}>
              查询
            </Button>
          </Space>
          <Table<EntityHit>
            columns={columns}
            dataSource={rows}
            rowKey="id"
            size="small"
            pagination={false}
            loading={loading}
            onRow={(row) => ({
              onClick: () => {
                // 划词选中文字（如 ID）复制时不选中行，让原生复制正常工作。
                if (shouldSkipRowNav()) return;
                if (activeType)
                  onPick({
                    id: row.id,
                    display: row.display,
                    typeCode: activeType.code,
                    typeLabel: activeType.label,
                  });
              },
              style: { cursor: 'pointer' },
            })}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={
                    searched ? '无匹配对象' : `${searchPlaceholder(activeType?.fields)}后点查询`
                  }
                />
              ),
            }}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            最多 20 条；点击行即选中。
          </Typography.Text>
        </div>
      </div>
    </Modal>
  );
}
