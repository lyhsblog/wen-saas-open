import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Empty, Segmented, Spin, Table, Upload } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DownloadOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { WizardModal, type WizardFooterContext, type WizardStep } from '@wen-saas/shared-ui';
import { describeApiError } from '@wen-saas/api-client';
import type { ImportEntityView, ParseResultView, PreviewRowView } from '@wen-saas/domain';
import { useImportEntities, useParseImport, useConfirmImport } from './importQueries';
import { useTaskDetail } from './taskQueries';
import { importApi, taskApi } from './taskApi';
import { downloadBlob } from '../../lib/download';

type Props = {
  open: boolean;
  onClose: () => void;
  /** 预选导入对象（从业务列表页「导入」入口打开时传，第 1 步锁定到该对象、不可改选）。 */
  initialEntityCode?: string;
  /** 完成（成功提交并跑完）后回调，外层刷新任务列表。 */
  onDone?: () => void;
};

const STEP_KEYS = ['entity', 'upload', 'preview', 'executing', 'done'] as const;

/**
 * 数据导入向导。5 步：
 * 选择对象/下载模板 → 上传 → 预览校验 → 执行（任务中心轮询）→ 完成。
 */
export function ImportWizardModal({ open, onClose, initialEntityCode, onDone }: Props) {
  const { message } = App.useApp();
  const [step, setStep] = useState(0);
  // 懒初始化：从列表页入口打开时（条件挂载，每次打开都是新实例）预选实体。
  const [entityCode, setEntityCode] = useState<string | null>(initialEntityCode ?? null);
  const [file, setFile] = useState<File | null>(null);
  const [session, setSession] = useState<ParseResultView | null>(null);
  const [previewFilter, setPreviewFilter] = useState<'all' | 'passed' | 'error'>('all');
  const [taskId, setTaskId] = useState<string | null>(null);

  const entities = useImportEntities(open);
  const parse = useParseImport();
  const confirm = useConfirmImport();
  const taskDetail = useTaskDetail(taskId, step >= 3);

  const entity = useMemo(
    () => (entities.data ?? []).find((e) => e.code === entityCode) ?? null,
    [entities.data, entityCode],
  );

  // 从业务列表页入口打开（带 initialEntityCode）时锁定到当前业务对象，第 1 步只显示该对象、不可改选；
  // 任务中心「新建导入任务」入口无预选，照常显示全部可导入对象。
  const visibleEntities = useMemo(() => {
    const all = entities.data ?? [];
    return initialEntityCode ? all.filter((e) => e.code === initialEntityCode) : all;
  }, [entities.data, initialEntityCode]);

  // 执行步：任务进入终态 → 派生为完成步（不在 effect 里 setState，避免级联渲染）
  const taskStatus = taskDetail.data?.status;
  const taskTerminal =
    taskStatus === 'completed' || taskStatus === 'failed' || taskStatus === 'cancelled';
  const effectiveStep = step === 3 && taskTerminal ? 4 : step;

  // 任务跑完 → 通知外层刷新列表（纯副作用，无 setState）
  useEffect(() => {
    if (step === 3 && taskTerminal) onDone?.();
  }, [step, taskTerminal, onDone]);

  const reset = () => {
    setStep(0);
    setEntityCode(null);
    setFile(null);
    setSession(null);
    setPreviewFilter('all');
    setTaskId(null);
  };

  const close = () => {
    onClose();
    // 关闭后延迟重置，避免关闭动画期间内容跳变
    setTimeout(reset, 200);
  };

  const downloadTemplate = async () => {
    if (!entity?.code) return;
    try {
      const blob = await importApi.downloadTemplate(entity.code);
      downloadBlob(blob, `${entity.name ?? entity.code}-导入模板.xlsx`);
      message.success('模板已开始下载');
    } catch (err) {
      message.error(describeApiError(err, '下载模板失败'));
    }
  };

  const doParse = async (): Promise<boolean> => {
    if (!entityCode || !file) return false;
    try {
      const res = await parse.mutateAsync({ entityCode, file });
      setSession(res);
      setPreviewFilter('all');
      return true;
    } catch (err) {
      message.error(describeApiError(err, '解析失败，请检查文件是否符合模板'));
      return false;
    }
  };

  const doConfirm = async () => {
    if (!session?.sessionId) return;
    try {
      const res = await confirm.mutateAsync(session.sessionId);
      setTaskId(res.taskId != null ? String(res.taskId) : null);
      setStep(3);
    } catch (err) {
      message.error(describeApiError(err, '提交导入失败'));
    }
  };

  const downloadErrors = async () => {
    if (!session?.sessionId) return;
    try {
      const blob = await importApi.downloadErrors(session.sessionId);
      downloadBlob(blob, `${entity?.name ?? '导入'}-错误明细.xlsx`);
      message.success('错误明细已开始下载');
    } catch (err) {
      message.error(describeApiError(err, '下载错误明细失败'));
    }
  };

  const downloadResult = async () => {
    if (!taskId) return;
    try {
      const blob = await taskApi.downloadResult(taskId);
      downloadBlob(blob, `${entity?.name ?? '导入'}-结果.xlsx`);
      message.success('结果文件已开始下载');
    } catch (err) {
      message.error(describeApiError(err, '下载结果失败'));
    }
  };

  const steps: WizardStep[] = [
    {
      key: STEP_KEYS[0],
      title: '选择对象 / 下载模板',
      content: (
        <StepEntity
          entities={visibleEntities}
          loading={entities.isLoading}
          value={entityCode}
          onChange={setEntityCode}
          onDownloadTemplate={() => void downloadTemplate()}
        />
      ),
    },
    {
      key: STEP_KEYS[1],
      title: '上传文件',
      content: <StepUpload entity={entity} file={file} onPick={setFile} />,
    },
    {
      key: STEP_KEYS[2],
      title: '预览校验',
      content: (
        <StepPreview
          entity={entity}
          session={session}
          sessionId={session?.sessionId ?? null}
          filter={previewFilter}
          onFilterChange={setPreviewFilter}
          onDownloadErrors={() => void downloadErrors()}
        />
      ),
    },
    {
      key: STEP_KEYS[3],
      title: '执行',
      content: <StepExecuting task={taskDetail.data} />,
    },
    {
      key: STEP_KEYS[4],
      title: '完成',
      content: <StepDone task={taskDetail.data} onDownloadResult={() => void downloadResult()} />,
    },
  ];

  const hasErrors = (session?.failed ?? 0) > 0;
  const canConfirm = session != null && !hasErrors && (session.total ?? 0) > 0;

  const footer = (ctx: WizardFooterContext) => (
    <ImportFooter
      step={effectiveStep}
      canEntityNext={!!entityCode}
      canParse={!!file}
      parsing={parse.isPending}
      canConfirm={canConfirm}
      confirming={confirm.isPending}
      onPrev={() => setStep(step - 1)}
      onCancel={ctx.cancel}
      onEntityNext={() => setStep(1)}
      onParse={async () => {
        if (await doParse()) setStep(2);
      }}
      onConfirm={() => void doConfirm()}
      onFinish={ctx.finish}
    />
  );

  return (
    <WizardModal
      open={open}
      title="新建导入任务"
      steps={steps}
      current={effectiveStep}
      onChange={setStep}
      onCancel={close}
      onFinish={close}
      width={820}
      footer={footer}
    />
  );
}

/* ============ footer ============ */

function ImportFooter({
  step,
  canEntityNext,
  canParse,
  parsing,
  canConfirm,
  confirming,
  onPrev,
  onCancel,
  onEntityNext,
  onParse,
  onConfirm,
  onFinish,
}: {
  step: number;
  canEntityNext: boolean;
  canParse: boolean;
  parsing: boolean;
  canConfirm: boolean;
  confirming: boolean;
  onPrev: () => void;
  onCancel: () => void;
  onEntityNext: () => void;
  onParse: () => void | Promise<void>;
  onConfirm: () => void;
  onFinish: () => void | Promise<void>;
}) {
  return (
    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
      {step > 0 && step < 3 && <Button onClick={onPrev}>上一步</Button>}
      {step < 3 && <Button onClick={onCancel}>取消</Button>}
      {step === 0 && (
        <Button type="primary" disabled={!canEntityNext} onClick={onEntityNext}>
          下一步：上传
        </Button>
      )}
      {step === 1 && (
        <Button
          type="primary"
          disabled={!canParse}
          loading={parsing}
          onClick={() => void onParse()}
        >
          解析文件
        </Button>
      )}
      {step === 2 && (
        <Button type="primary" disabled={!canConfirm} loading={confirming} onClick={onConfirm}>
          确认导入
        </Button>
      )}
      {step === 3 && (
        <Button type="primary" disabled>
          执行中…
        </Button>
      )}
      {step === 4 && (
        <Button type="primary" onClick={() => void onFinish()}>
          完成
        </Button>
      )}
    </div>
  );
}

/* ============ step 0: 选择对象 ============ */

function StepEntity({
  entities,
  loading,
  value,
  onChange,
  onDownloadTemplate,
}: {
  entities: ImportEntityView[];
  loading: boolean;
  value: string | null;
  onChange: (code: string) => void;
  onDownloadTemplate: () => void;
}) {
  if (loading) return <Spin />;
  if (entities.length === 0) return <Empty description="当前没有可导入的对象" />;
  const selected = entities.find((e) => e.code === value);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div>
        <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>导入对象</div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 8 }}>
          {entities.map((e) => {
            const active = e.code === value;
            return (
              <button
                key={e.code}
                type="button"
                onClick={() => e.code && onChange(e.code)}
                style={{
                  padding: 12,
                  textAlign: 'left',
                  cursor: 'pointer',
                  border: '1.5px solid',
                  borderColor: active ? 'var(--brand, #2563eb)' : 'var(--border, #e5e7eb)',
                  background: active ? 'var(--brand-bg, #eff6ff)' : '#fff',
                  borderRadius: 8,
                }}
              >
                <div style={{ fontSize: 13, fontWeight: 600 }}>{e.name}</div>
                <div style={{ fontSize: 11, color: 'var(--text-tertiary, #9ca3af)', marginTop: 4 }}>
                  {(e.fields ?? []).map((f) => f.header).join(' / ')}
                </div>
                <div
                  style={{
                    fontSize: 11,
                    color: 'var(--text-tertiary, #9ca3af)',
                    marginTop: 4,
                    fontFamily: 'var(--font-mono, monospace)',
                  }}
                >
                  单文件 ≤ {(e.rowLimit ?? 5000).toLocaleString()} 行
                </div>
              </button>
            );
          })}
        </div>
      </div>
      {selected && (
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>模板下载</div>
          <Button icon={<DownloadOutlined />} onClick={onDownloadTemplate}>
            下载「{selected.name}」导入模板.xlsx
          </Button>
          <div style={{ fontSize: 12, color: 'var(--text-tertiary, #9ca3af)', marginTop: 6 }}>
            模板按当前字段定义生成，请按模板填写后上传。严格按表头映射，请勿改列名。
          </div>
        </div>
      )}
    </div>
  );
}

/* ============ step 1: 上传 ============ */

function StepUpload({
  entity,
  file,
  onPick,
}: {
  entity: ImportEntityView | null;
  file: File | null;
  onPick: (f: File | null) => void;
}) {
  const { message } = App.useApp();
  return (
    <div>
      <Upload.Dragger
        accept=".xlsx,.xls"
        showUploadList={false}
        multiple={false}
        beforeUpload={(f) => {
          if (f.size > 10 * 1024 * 1024) {
            message.error(
              `文件大小 ${(f.size / 1024 / 1024).toFixed(1)} MB，超出单文件 10 MB 上限`,
            );
            return Upload.LIST_IGNORE;
          }
          onPick(f);
          return false;
        }}
      >
        <p style={{ margin: 0 }}>
          <UploadOutlined style={{ fontSize: 32, color: 'var(--text-tertiary, #9ca3af)' }} />
        </p>
        <p style={{ fontSize: 14, fontWeight: 500, marginTop: 10 }}>
          把 .xlsx 文件拖到这里，或点击选择
        </p>
        <p style={{ fontSize: 12, color: 'var(--text-tertiary, #9ca3af)' }}>
          支持 .xlsx / .xls · 单文件 ≤ 10 MB
          {entity ? ` · ≤ ${(entity.rowLimit ?? 5000).toLocaleString()} 行` : ''} ·
          模板需与下载版本字段一致
        </p>
      </Upload.Dragger>
      {file && (
        <div
          style={{
            marginTop: 14,
            padding: '12px 14px',
            border: '1px solid var(--border, #e5e7eb)',
            borderRadius: 8,
            display: 'flex',
            alignItems: 'center',
            gap: 10,
          }}
        >
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 13, fontWeight: 500 }}>{file.name}</div>
            <div style={{ fontSize: 11, color: 'var(--text-tertiary, #9ca3af)' }}>
              {(file.size / 1024 / 1024).toFixed(2)} MB · 已就绪，点「解析文件」开始预校验
            </div>
          </div>
          <Button size="small" onClick={() => onPick(null)}>
            移除
          </Button>
        </div>
      )}
    </div>
  );
}

/* ============ step 2: 预览 ============ */

function StepPreview({
  entity,
  session,
  sessionId,
  filter,
  onFilterChange,
  onDownloadErrors,
}: {
  entity: ImportEntityView | null;
  session: ParseResultView | null;
  sessionId: string | null;
  filter: 'all' | 'passed' | 'error';
  onFilterChange: (f: 'all' | 'passed' | 'error') => void;
  onDownloadErrors: () => void;
}) {
  if (!session) return <Spin />;
  const total = session.total ?? 0;
  const passed = session.passed ?? 0;
  const failed = session.failed ?? 0;
  const rows = (session.preview ?? []).filter((r) =>
    filter === 'all' ? true : filter === 'passed' ? r.status === 'ok' : r.status !== 'ok',
  );
  void sessionId;

  const fieldCols: ColumnsType<PreviewRowView> = (entity?.fields ?? []).map((f, i) => {
    const fieldKey = f.key ?? `col-${i}`;
    return {
      title: f.header ?? fieldKey,
      key: fieldKey,
      render: (_: unknown, r: PreviewRowView) => r.cells?.[fieldKey] ?? '',
      ellipsis: true,
    };
  });

  const columns: ColumnsType<PreviewRowView> = [
    {
      title: 'Excel行号',
      dataIndex: 'excelRow',
      width: 80,
      render: (n: number | undefined, r: PreviewRowView) => n ?? r.rowNo,
    },
    ...fieldCols,
    {
      title: '状态',
      key: 'status',
      width: 240,
      render: (_: unknown, r: PreviewRowView) =>
        r.status === 'ok' ? (
          <span style={{ color: 'var(--success, #047857)' }}>
            <CheckCircleOutlined /> 通过
          </span>
        ) : (
          <span style={{ color: 'var(--danger, #dc2626)' }}>
            <CloseCircleOutlined /> {(r.errors ?? []).map((e) => e.message).join('；')}
          </span>
        ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <Alert
        type={failed > 0 ? 'error' : 'success'}
        showIcon
        message={
          <span>
            总 <b>{total}</b> 行数据 · 通过{' '}
            <b style={{ color: 'var(--success, #047857)' }}>{passed}</b> · 错误{' '}
            <b style={{ color: 'var(--danger, #dc2626)' }}>{failed}</b>
            {failed > 0 && <span style={{ marginLeft: 8 }}>必须修正全部错误才能进入执行阶段</span>}
          </span>
        }
        action={
          failed > 0 ? (
            <Button size="small" icon={<DownloadOutlined />} onClick={onDownloadErrors}>
              下载错误明细
            </Button>
          ) : null
        }
      />
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <Segmented
          size="small"
          value={filter}
          onChange={(v) => onFilterChange(v as 'all' | 'passed' | 'error')}
          options={[
            { label: `全部 ${total}`, value: 'all' },
            { label: `仅通过 ${passed}`, value: 'passed' },
            { label: `仅错误 ${failed}`, value: 'error' },
          ]}
        />
        <span style={{ fontSize: 11, color: 'var(--text-tertiary, #9ca3af)', marginLeft: 'auto' }}>
          显示前 200 行 · 错误优先 + 序号排序
        </span>
      </div>
      <Table<PreviewRowView>
        rowKey="rowNo"
        size="small"
        columns={columns}
        dataSource={rows}
        pagination={false}
        scroll={{ y: 280 }}
        rowClassName={(r) => (r.status === 'ok' ? '' : 'import-row-error')}
      />
    </div>
  );
}

/* ============ step 3: 执行 ============ */

function StepExecuting({
  task,
}: {
  task: { progress?: number; processedCount?: number; totalCount?: number } | undefined;
}) {
  const pct = task?.progress ?? 0;
  const processed = task?.processedCount ?? 0;
  const total = task?.totalCount ?? 0;
  return (
    <div style={{ padding: '30px 20px', textAlign: 'center' }}>
      <div style={{ fontSize: 14, fontWeight: 500, marginBottom: 16 }}>正在导入…</div>
      <div
        style={{
          height: 8,
          background: 'var(--bg-muted, #f3f4f6)',
          borderRadius: 4,
          marginBottom: 12,
        }}
      >
        <div
          style={{
            width: `${pct}%`,
            height: '100%',
            background: 'var(--brand, #2563eb)',
            borderRadius: 4,
            transition: 'width 0.4s',
          }}
        />
      </div>
      <div style={{ fontSize: 13, color: 'var(--text-secondary, #4b5563)' }}>
        已处理 <b style={{ fontFamily: 'var(--font-mono, monospace)' }}>{processed}</b>
        {total > 0 && (
          <>
            {' '}
            / <b style={{ fontFamily: 'var(--font-mono, monospace)' }}>{total}</b>
          </>
        )}{' '}
        · {pct}%
      </div>
      <div
        style={{
          marginTop: 14,
          padding: 10,
          background: 'var(--bg-muted, #f3f4f6)',
          borderRadius: 8,
          fontSize: 12,
          color: 'var(--text-tertiary, #9ca3af)',
          display: 'inline-block',
        }}
      >
        关闭弹窗任务会继续在后台执行，完成后站内信通知，可在「导入导出」查看。
      </div>
    </div>
  );
}

/* ============ step 4: 完成 ============ */

function StepDone({
  task,
  onDownloadResult,
}: {
  task:
    | {
        status?: string;
        successCount?: number;
        failedCount?: number;
        totalCount?: number;
        errorMessage?: string;
      }
    | undefined;
  onDownloadResult: () => void;
}) {
  const failedTask = task?.status === 'failed';
  const success = task?.successCount ?? 0;
  const failed = task?.failedCount ?? 0;
  const total = task?.totalCount ?? success + failed;
  return (
    <div style={{ padding: '12px 0', display: 'flex', flexDirection: 'column', gap: 14 }}>
      <Alert
        type={failedTask ? 'error' : failed > 0 ? 'warning' : 'success'}
        showIcon
        message={failedTask ? '导入失败' : '导入完成'}
        description={
          failedTask ? (
            task?.errorMessage || '任务执行失败，请稍后重试'
          ) : (
            <span>
              总 <b>{total}</b> 行数据 · 成功{' '}
              <b style={{ color: 'var(--success, #047857)' }}>{success}</b> · 失败{' '}
              <b style={{ color: 'var(--danger, #dc2626)' }}>{failed}</b>
            </span>
          )
        }
      />
      {failed > 0 && (
        <div>
          <Button icon={<DownloadOutlined />} onClick={onDownloadResult}>
            下载结果（含失败原因 + 原数据）
          </Button>
        </div>
      )}
    </div>
  );
}
