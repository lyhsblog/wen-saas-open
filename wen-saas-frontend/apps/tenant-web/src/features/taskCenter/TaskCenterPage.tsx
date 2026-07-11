import { useMemo, useState } from 'react';
import { App, Pagination, Skeleton, Tabs } from 'antd';
import { useSearchParams } from 'react-router';
import {
  ErrorState,
  TaskCenter,
  type TaskAction,
  type TaskFilter,
  type TaskItem,
} from '@wen-saas/shared-ui';
import { describeApiError } from '@wen-saas/api-client';
import type { TaskView } from '@wen-saas/domain';
import { useCapabilities, useHasPermission } from '@wen-saas/permissions';
import { useTaskList, useCancelTask } from './taskQueries';
import { toTaskItem, hasResultFile } from './taskMappers';
import { taskApi } from './taskApi';
import { ExportApprovalsTab } from './ExportApprovalsTab';
import { downloadBlob } from '../../lib/download';

/**
 * 租户后台 · 任务中心。导入 / 导出任务统一管理：列表 + 状态筛选 + 分页 +
 * 进度（有进行中任务时 3s 轮询）+ 取消 + 结果下载。纯展示交给 shared-ui `TaskCenter`，
 * 数据 / 操作在此注入。审批人（主管理员 / 持 base.compliance.export.approve）额外见
 * 「待审批」Tab（导出审批的「任务中心 → 待审批」落点）。
 */
export function TaskCenterPage() {
  const caps = useCapabilities();
  const canApprove = useHasPermission('base.compliance.export.approve') || !!caps?.tenantOwner;
  // URL ?tab= 与 Tab key 双向同步：审批通知深链直达（approvals），切换回写 URL，
  // 刷新 / 复制链接保留当前页签。
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get('tab') === 'approvals' ? 'approvals' : 'tasks';
  const setActiveTab = (key: string) => {
    const next = new URLSearchParams(searchParams);
    if (key === 'approvals') next.set('tab', key);
    else next.delete('tab');
    setSearchParams(next, { replace: true });
  };

  if (!canApprove) {
    return <TaskListPane />;
  }
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        style={{ padding: '8px 24px 0' }}
        items={[
          { key: 'tasks', label: '任务列表' },
          { key: 'approvals', label: '待审批' },
        ]}
      />
      {activeTab === 'tasks' ? (
        <div style={{ flex: 1, minHeight: 0 }}>
          <TaskListPane />
        </div>
      ) : (
        <div style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: '0 24px 24px' }}>
          <ExportApprovalsTab />
        </div>
      )}
    </div>
  );
}

function TaskListPane() {
  const { message } = App.useApp();
  const [filter, setFilter] = useState<TaskFilter>('all');
  const [page, setPage] = useState(1);
  const { data, isLoading, isError, refetch } = useTaskList({ page, size: 50 });
  const cancel = useCancelTask();

  const records = useMemo(() => data?.records ?? [], [data]);
  const byId = useMemo(() => {
    const m = new Map<string, TaskView>();
    for (const t of records) m.set(String(t.id ?? ''), t);
    return m;
  }, [records]);
  const items = useMemo(() => records.map(toTaskItem), [records]);

  const actionsForTask = (task: TaskItem): TaskAction[] => {
    if (task.status === 'running') {
      return [{ key: 'cancel', label: '取消', danger: true }];
    }
    if (task.status === 'done') {
      const raw = byId.get(task.id);
      return raw && hasResultFile(raw) ? [{ key: 'download', label: '下载结果' }] : [];
    }
    if (task.status === 'failed') {
      const raw = byId.get(task.id);
      return raw && hasResultFile(raw) ? [{ key: 'download', label: '下载结果' }] : [];
    }
    return [];
  };

  const onAction = async (task: TaskItem, actionKey: string) => {
    if (actionKey === 'cancel') {
      try {
        await cancel.mutateAsync(task.id);
        message.success('已请求取消，任务将尽快停止');
      } catch (err) {
        message.error(describeApiError(err, '取消失败'));
      }
      return;
    }
    if (actionKey === 'download') {
      try {
        const blob = await taskApi.downloadResult(task.id);
        downloadBlob(blob, `${task.name || '任务结果'}.xlsx`);
      } catch (err) {
        message.error(describeApiError(err, '下载失败'));
      }
    }
  };

  if (isLoading) {
    return (
      <div style={{ padding: 24 }}>
        <Skeleton active paragraph={{ rows: 6 }} />
      </div>
    );
  }
  if (isError) {
    return (
      <ErrorState
        title="任务列表加载失败"
        description="请稍后重试。"
        primaryAction={{ label: '重试', onClick: () => void refetch() }}
      />
    );
  }
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, minHeight: 0 }}>
        <TaskCenter
          tasks={items}
          title="导入导出"
          subtitle="导入 / 导出任务统一管理 · 任务保留 30 天"
          filter={filter}
          onFilterChange={(next) => {
            setFilter(next);
            setPage(1);
          }}
          filterHint="同租户、同对象同时只能进行一个导入"
          actionsForTask={actionsForTask}
          onAction={(task, key) => void onAction(task, key)}
          emptyDescription="暂无任务，任务保留 30 天"
        />
      </div>
      <Pagination
        simple
        current={page}
        pageSize={50}
        total={data?.total ?? 0}
        onChange={setPage}
        style={{ padding: '8px 16px', textAlign: 'center', borderTop: '1px solid var(--border)' }}
      />
    </div>
  );
}
