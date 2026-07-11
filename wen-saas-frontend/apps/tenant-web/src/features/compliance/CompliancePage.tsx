import { useState } from 'react';
import { Tabs, Typography } from 'antd';
import { ExportApprovalTab } from './ExportApprovalTab';
import { DataExportTab } from './DataExportTab';

/** URL ?tab= 与 Tab key 对齐，便于站内信深链直达（如全量导出完成通知）。 */
function initialTab(): string {
  const t = new URLSearchParams(window.location.search).get('tab');
  return t === 'dataExport' ? t : 'export';
}

/**
 * 租户后台 · 系统管理 · 数据隐私与合规。租户侧聚焦「租户能操作」的合规动作：
 * 「导出审批」（策略配置）+「全量数据导出」（GDPR）。数据留存矩阵 / 回收站待文件中心承接，
 * 敏感字段加密底座属平台事实、移至运营端自查；停用账号联动依赖成员管理能力后续补齐。
 * 菜单已按 base.compliance.export.approve 门禁（主管理员恒可见）。
 */
export function CompliancePage() {
  const [activeKey, setActiveKey] = useState<string>(initialTab);
  return (
    // padding 放内层：滚动容器自身不留 padding-bottom，策略卡的吸底保存栏（sticky bottom:0）
    // 才能贴住可视底边，否则底部 padding 区会透出滚动内容
    <div style={{ height: '100%', overflow: 'auto' }}>
      <div style={{ padding: 16 }}>
        <Typography.Title level={4} style={{ marginTop: 0 }}>
          数据隐私与合规
        </Typography.Title>
        <Tabs
          activeKey={activeKey}
          onChange={setActiveKey}
          items={[
            { key: 'export', label: '导出审批', children: <ExportApprovalTab /> },
            { key: 'dataExport', label: '全量数据导出', children: <DataExportTab /> },
          ]}
        />
      </div>
    </div>
  );
}
