import { useState } from 'react';
import { Button, Descriptions, Typography } from 'antd';
import { EditOutlined } from '@ant-design/icons';
import { CountChip, formatDate, formatDateTime, StatusPill } from '@wen-saas/shared-ui';
import { ACTIVATION_MAP } from '../utils/statusOf';
import { UpdateRemarkModal } from '../modals/UpdateRemarkModal';
import { UpdateTenantNameModal } from '../modals/UpdateTenantNameModal';
import type { OpsTenantBasicView } from '@wen-saas/api-client';
import { h5WebHost, tenantWebHost } from '../../../lib/tenantWebUrl';

type Props = {
  tenant: OpsTenantBasicView;
};

const NONE = <span style={{ color: 'var(--text-quaternary)' }}>—</span>;

/** 一行访问域名：端类型标签 + 单行等宽域名 + 内联小复制图标（copyable 自带已复制反馈）。 */
function DomainLine({ label, host }: { label: string; host: string }) {
  return (
    <span>
      <span style={{ color: 'var(--text-tertiary)', fontSize: 12, marginRight: 6 }}>{label}</span>
      <Typography.Text copyable={{ text: host, tooltips: ['复制', '已复制'] }}>
        <code style={{ fontFamily: 'var(--font-mono)' }}>{host}</code>
      </Typography.Text>
    </span>
  );
}

function fmtBytes(n: number | null): React.ReactNode {
  if (n == null) return <span style={{ color: 'var(--text-tertiary)' }}>不限</span>;
  const gb = n / 1024 ** 3;
  if (gb >= 1024) return `${(gb / 1024).toFixed(1)} TB`;
  return `${gb >= 100 ? gb.toFixed(0) : gb.toFixed(1)} GB`;
}

export function TabBasic({ tenant }: Props) {
  const [remarkOpen, setRemarkOpen] = useState(false);
  const [nameOpen, setNameOpen] = useState(false);
  const activation = ACTIVATION_MAP[tenant.activationStatus] ?? ACTIVATION_MAP.unknown;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginBottom: 12 }}>
        <Button size="small" icon={<EditOutlined />} onClick={() => setNameOpen(true)}>
          编辑名称
        </Button>
        <Button size="small" icon={<EditOutlined />} onClick={() => setRemarkOpen(true)}>
          编辑备注
        </Button>
      </div>

      {/* 两列 Descriptions：左列基础身份 / 右列公司信息；备注跨两列 */}
      <Descriptions column={2} bordered size="small">
        <Descriptions.Item label="租户名称">{tenant.name}</Descriptions.Item>
        <Descriptions.Item label="公司全称">{tenant.fullName ?? NONE}</Descriptions.Item>

        <Descriptions.Item label="子域名">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <DomainLine label="PC 端" host={tenantWebHost(tenant.code)} />
            <DomainLine label="移动端" host={h5WebHost(tenant.code)} />
          </div>
        </Descriptions.Item>
        <Descriptions.Item label="统一社会信用代码">
          {tenant.uscc ? (
            <code style={{ fontFamily: 'var(--font-mono)' }}>{tenant.uscc}</code>
          ) : (
            NONE
          )}
        </Descriptions.Item>

        <Descriptions.Item label="内部编号">
          {tenant.tenantNo ? (
            <code style={{ fontFamily: 'var(--font-mono)' }}>{tenant.tenantNo}</code>
          ) : (
            NONE
          )}
        </Descriptions.Item>
        <Descriptions.Item label="行业">{tenant.industry ?? NONE}</Descriptions.Item>

        <Descriptions.Item label="激活状态">
          <StatusPill badge={activation} />
        </Descriptions.Item>
        <Descriptions.Item label="规模">{tenant.scale ?? NONE}</Descriptions.Item>

        <Descriptions.Item label="创建时间">
          {tenant.createdAt ? formatDateTime(tenant.createdAt) : NONE}
        </Descriptions.Item>
        <Descriptions.Item label="所在地区">{tenant.region ?? NONE}</Descriptions.Item>

        <Descriptions.Item label="最后活跃">
          {tenant.lastActiveAt ? formatDateTime(tenant.lastActiveAt) : NONE}
        </Descriptions.Item>
        <Descriptions.Item label="客户经理">
          {tenant.csmName ? (
            <span>
              {tenant.csmName}
              {tenant.csmUserId && (
                <code
                  style={{
                    fontFamily: 'var(--font-mono)',
                    fontSize: 11,
                    color: 'var(--text-tertiary)',
                    marginLeft: 8,
                  }}
                >
                  #{tenant.csmUserId}
                </code>
              )}
            </span>
          ) : (
            <span style={{ color: 'var(--text-tertiary)' }}>未指定</span>
          )}
        </Descriptions.Item>

        <Descriptions.Item label="到期时间">
          {tenant.expireAt ? (
            formatDate(tenant.expireAt)
          ) : (
            <span style={{ color: 'var(--text-tertiary)' }}>永久</span>
          )}
        </Descriptions.Item>
        <Descriptions.Item label="时区">
          {tenant.timezone ?? <span style={{ color: 'var(--text-tertiary)' }}>平台默认</span>}
        </Descriptions.Item>

        <Descriptions.Item label="成员上限">
          {tenant.memberCap != null ? (
            `${tenant.memberCap} 人`
          ) : (
            <span style={{ color: 'var(--text-tertiary)' }}>不限</span>
          )}
        </Descriptions.Item>
        <Descriptions.Item label="存储上限">{fmtBytes(tenant.storageCap)}</Descriptions.Item>

        <Descriptions.Item label="标签" span={2}>
          {tenant.tags && tenant.tags.length > 0 ? (
            <span style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap' }}>
              {tenant.tags.map((t) => (
                <CountChip key={t}>{t}</CountChip>
              ))}
            </span>
          ) : (
            <span style={{ color: 'var(--text-tertiary)' }}>—</span>
          )}
        </Descriptions.Item>

        <Descriptions.Item label="运营备注（内部可见）" span={2}>
          {tenant.remark ? (
            <span style={{ whiteSpace: 'pre-wrap', color: 'var(--text)' }}>{tenant.remark}</span>
          ) : (
            <span style={{ color: 'var(--text-tertiary)' }}>—（点击"编辑备注"添加）</span>
          )}
        </Descriptions.Item>
      </Descriptions>

      <UpdateRemarkModal
        open={remarkOpen}
        tenantId={tenant.id}
        currentRemark={tenant.remark}
        onClose={() => setRemarkOpen(false)}
      />
      <UpdateTenantNameModal
        open={nameOpen}
        tenantId={tenant.id}
        currentName={tenant.name}
        onClose={() => setNameOpen(false)}
      />
    </div>
  );
}
