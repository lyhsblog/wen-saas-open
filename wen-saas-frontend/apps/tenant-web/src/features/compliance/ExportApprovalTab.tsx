import { Alert, Card, Col, Row, Statistic } from 'antd';
import { useNavigate } from 'react-router';
import { ErrorState } from '@wen-saas/shared-ui';
import { useHasPermission } from '@wen-saas/permissions';
import { useComplianceOverview } from './complianceQueries';
import { ExportPolicyCard } from './ExportPolicyCard';

/**
 * 导出审批 Tab：导出审批维度的几个指标摘要 + 「导出审批策略」配置（各导出对象是否需审批的开关 +
 * 审批权限 + 超时提醒）。
 *
 * <p>导出申请的发起由各业务模块的导出流程触发（命中「需审批」对象时转审批）；申请台账与审批
 * 操作由「任务中心 → 待审批」承载（待接入），不在本配置页展示。仅有
 * {@code base.compliance.config} 权限者可见；其余成员见提示。
 */
export function ExportApprovalTab() {
  const navigate = useNavigate();
  const canConfig = useHasPermission('base.compliance.config');
  const { data, isLoading, isError, refetch } = useComplianceOverview(canConfig);

  if (!canConfig) {
    return (
      <Alert
        type="info"
        showIcon
        message="暂无权限配置导出审批策略（需「数据合规 · 策略配置」权限）"
        description="调整各导出对象的审批开关、审批权限或超时提醒需要 base.compliance.config 权限，请联系主管理员授予；若你是审批人，处理申请请前往「导入导出 → 待审批」。"
      />
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {isError ? (
        <Card>
          <ErrorState
            title="审批概览加载失败"
            description="请稍后重试；若持续失败请联系管理员。"
            primaryAction={{ label: '重试', onClick: () => void refetch() }}
          />
        </Card>
      ) : (
        <Row gutter={16}>
          <Col span={6}>
            {/* 审批实操在任务中心「待审批」页签；能进合规页者已持 export.approve（菜单门禁），链接恒可用 */}
            <Card
              hoverable
              loading={isLoading}
              style={{ height: '100%' }}
              styles={{ body: { cursor: 'pointer' } }}
              onClick={() => void navigate('/tasks?tab=approvals')}
            >
              <Statistic
                title="待审批的导出申请"
                value={data?.pendingCount ?? 0}
                valueStyle={
                  data && data.pendingCount > 0 ? { color: 'var(--warning, #d46b08)' } : {}
                }
              />
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginTop: 4,
                }}
              >
                <span style={{ color: 'var(--text-tertiary, #8c8c8c)', fontSize: 12 }}>
                  {data && data.overtimePendingCount > 0
                    ? `超时未处理 ${data.overtimePendingCount} 个`
                    : '暂无超时'}
                </span>
                <span style={{ color: 'var(--brand, #2B5FFF)', fontSize: 12, fontWeight: 600 }}>
                  前往审批 →
                </span>
              </div>
            </Card>
          </Col>
          <Col span={6}>
            <Card loading={isLoading} style={{ height: '100%' }}>
              <Statistic title="近 30 天批准" value={data?.approved30d ?? 0} />
            </Card>
          </Col>
          <Col span={6}>
            <Card loading={isLoading} style={{ height: '100%' }}>
              <Statistic title="近 30 天拒绝" value={data?.rejected30d ?? 0} />
            </Card>
          </Col>
          <Col span={6}>
            <Card loading={isLoading} style={{ height: '100%' }}>
              <Statistic
                title="需审批的导出对象"
                value={data?.approvalRequiredCount ?? 0}
                suffix="个"
              />
            </Card>
          </Col>
        </Row>
      )}

      <ExportPolicyCard />
    </div>
  );
}
