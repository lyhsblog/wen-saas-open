import { Descriptions, Drawer, Spin } from 'antd';
import { formatDateTime, StatusPill } from '@wen-saas/shared-ui';
import { CustomFieldDescriptions } from '../customfield/CustomFieldDescriptions';
import { CRM_CUSTOMER_OBJECT } from './crmApi';
import { CustomerAttachments } from './CustomerAttachments';
import { useCustomerDetail } from './crmQueries';
import { useSourceName } from './crmSource';
import { statusBadge } from './crmStatus';

export function CustomerDetailDrawer({ id, onClose }: { id: string | null; onClose: () => void }) {
  const { data, isLoading } = useCustomerDetail(id);
  const sourceName = useSourceName();

  return (
    <Drawer title="客户详情" width={520} open={!!id} onClose={onClose} destroyOnHidden>
      {isLoading || !data ? (
        <Spin />
      ) : (
        <>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="客户名称">{data.name}</Descriptions.Item>
            <Descriptions.Item label="客户编号">{data.code ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="阶段">
              <StatusPill badge={statusBadge(data.status)} />
            </Descriptions.Item>
            <Descriptions.Item label="来源">{sourceName(data.source)}</Descriptions.Item>
            <Descriptions.Item label="负责人">
              {data.ownerName ?? data.ownerId ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="归属部门">
              {data.ownerDeptName ?? data.ownerDeptId ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">{formatDateTime(data.createdAt)}</Descriptions.Item>
          </Descriptions>
          <CustomFieldDescriptions objectCode={CRM_CUSTOMER_OBJECT} extra={data.extra} />
          <CustomerAttachments customerId={data.id} />
        </>
      )}
    </Drawer>
  );
}
