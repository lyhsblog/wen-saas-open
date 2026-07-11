import { useState, type ReactNode } from 'react';
import { App, Input, Select, Skeleton, Typography } from 'antd';
import {
  CheckOutlined,
  CloudServerOutlined,
  LoadingOutlined,
  LockOutlined,
  ReloadOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  DangerConfirm,
  ServerError5xxPage,
  StatusPill,
  TestPill,
  toneBadge,
  type TestPillState,
} from '@wen-saas/shared-ui';
import { useHasPermission, useIsReadonly } from '@wen-saas/permissions';
import { ApiError, describeApiError, type CustomOssRequest } from '@wen-saas/api-client';
import { storageApi } from './storageApi';
import { useTenantKey } from '../../lib/tenant-path';
import { Lbl, WaBtn, WaCard, WaPage, WaPageHead } from '../../components/wa';

const storageKeys = { current: ['storage', 'current'] as const };

type Mode = 'platform' | 'custom';
type TestState = TestPillState;

type Vendor = 'aliyun_oss' | 'tencent_cos';

type OssForm = {
  vendor: Vendor;
  bucket: string;
  endpoint: string;
  accessKeyId: string;
  accessKeySecret: string;
  pathPrefix: string;
};

const EMPTY_FORM: OssForm = {
  vendor: 'aliyun_oss',
  bucket: '',
  endpoint: '',
  accessKeyId: '',
  accessKeySecret: '',
  pathPrefix: '',
};

/** secret 掩码占位：提交它 = 沿用后端已保存的凭证（后端解密现值替换后测试 / 落库）。 */
const SECRET_MASK = '********';

const isFormUntouched = (f: OssForm) =>
  !f.bucket && !f.endpoint && !f.accessKeyId && !f.accessKeySecret && !f.pathPrefix;

const VENDOR_COPY: Record<
  Vendor,
  {
    endpointLabel: string;
    endpointPlaceholder: string;
    endpointHint: ReactNode;
    keyIdLabel: string;
    keyIdPlaceholder: string;
    keySecretLabel: string;
  }
> = {
  aliyun_oss: {
    endpointLabel: 'Endpoint',
    endpointPlaceholder: '例：oss-cn-hangzhou.aliyuncs.com',
    endpointHint: (
      <>
        形如{' '}
        <span style={{ fontFamily: 'var(--font-mono)' }}>oss-cn-&lt;region&gt;.aliyuncs.com</span>
      </>
    ),
    keyIdLabel: 'AccessKey ID',
    keyIdPlaceholder: 'LTAI5t…',
    keySecretLabel: 'AccessKey Secret',
  },
  tencent_cos: {
    endpointLabel: 'Region',
    endpointPlaceholder: '例：ap-guangzhou',
    endpointHint: (
      <>
        填地域代码而非域名，形如{' '}
        <span style={{ fontFamily: 'var(--font-mono)' }}>ap-guangzhou</span>
      </>
    ),
    keyIdLabel: 'SecretId',
    keyIdPlaceholder: 'AKID…',
    keySecretLabel: 'SecretKey',
  },
};

/** 租户后台 · 系统管理 · 存储设置。 */
export function StoragePage() {
  const { message } = App.useApp();
  const qc = useQueryClient();
  const tenantKey = useTenantKey();
  const canEdit = useHasPermission('storage.edit');
  // 套餐到期只读时，即便有 storage.edit 也禁止写入（切换 / 测试 / 保存）。
  const readonly = useIsReadonly();
  const writable = canEdit && !readonly;

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: storageKeys.current,
    queryFn: () => storageApi.current(),
  });

  const [mode, setMode] = useState<Mode | null>(null);
  const [form, setForm] = useState<OssForm>(EMPTY_FORM);
  const [testState, setTestState] = useState<TestState>('idle');
  const [testMsg, setTestMsg] = useState('');
  const [confirm, setConfirm] = useState<null | 'custom' | 'platform'>(null);

  const usingCustom = data?.usingCustom ?? false;
  const effMode: Mode = mode ?? (usingCustom ? 'custom' : 'platform');

  // 已配自有存储时把 active 配置回填到表单（secret 后端永不回传，留空 = 沿用已保存凭证）。
  // 仅表单未被编辑时回填，避免后台 refetch 覆盖用户输入；render 期按 data 身份派生，不走 effect。
  const [hydratedFor, setHydratedFor] = useState<typeof data | null>(null);
  if (data && data !== hydratedFor) {
    setHydratedFor(data);
    const cfg = data.usingCustom ? data.config : null;
    if (cfg && isFormUntouched(form)) {
      setForm({
        vendor: cfg.backendType === 'tencent_cos' ? 'tencent_cos' : 'aliyun_oss',
        bucket: cfg.bucket ?? '',
        endpoint: cfg.endpoint ?? '',
        accessKeyId: cfg.accessKeyId ?? '',
        accessKeySecret: '',
        pathPrefix: cfg.pathPrefix ?? '',
      });
    }
  }

  const setF = (k: Exclude<keyof OssForm, 'vendor'>, v: string) => {
    setForm((s) => ({ ...s, [k]: v }));
    setTestState('idle');
    setTestMsg('');
  };
  const setVendor = (vendor: Vendor) => {
    setForm((s) => ({ ...s, vendor }));
    setTestState('idle');
    setTestMsg('');
  };
  const copy = VENDOR_COPY[form.vendor];

  const requiredFilled =
    effMode === 'platform'
      ? true
      : Boolean(
          form.bucket.trim() &&
          form.endpoint.trim() &&
          form.accessKeyId.trim() &&
          // 已配自有存储时 secret 可留空（提交掩码沿用已保存凭证）
          (form.accessKeySecret.trim() || usingCustom),
        );
  const isDirty = effMode !== (usingCustom ? 'custom' : 'platform') || effMode === 'custom';
  const needsTest = effMode === 'custom' && testState !== 'success';
  const canSave = writable && requiredFilled && !needsTest && isDirty;

  const toRequest = (): CustomOssRequest => ({
    backendType: form.vendor,
    endpoint: form.endpoint.trim(),
    bucket: form.bucket.trim(),
    accessKeyId: form.accessKeyId.trim(),
    // 留空 = 沿用已保存凭证：传掩码占位，后端解密现值替换后测试 / 落库
    accessKeySecret: form.accessKeySecret || (usingCustom ? SECRET_MASK : ''),
    ...(form.pathPrefix.trim() ? { pathPrefix: form.pathPrefix.trim() } : {}),
  });

  const test = useMutation({
    mutationFn: () => storageApi.testConnection(toRequest()),
    onMutate: () => {
      setTestState('testing');
      setTestMsg('正在上传 1KB 测试文件…');
    },
    onSuccess: () => {
      setTestState('success');
      setTestMsg('连接测试通过 · 上传 / 删除 测试文件均成功');
    },
    onError: (e) => {
      setTestState('fail');
      setTestMsg(describeApiError(e, '连接测试失败'));
    },
  });
  const switchCustom = useMutation({
    mutationFn: () => storageApi.switchToCustom(toRequest()),
    onSuccess: () => {
      message.success(`已切换到自有${vendorLabel(form.vendor)} · 新上传文件将存入您的 Bucket`);
      setConfirm(null);
      setMode(null);
      setForm(EMPTY_FORM);
      setTestState('idle');
      void qc.invalidateQueries({ queryKey: storageKeys.current });
    },
    onError: (e) => message.error(describeApiError(e, '切换失败')),
  });
  const switchPlatform = useMutation({
    mutationFn: () => storageApi.switchToPlatform(),
    onSuccess: () => {
      message.success('已切换回平台默认存储');
      setConfirm(null);
      setMode(null);
      void qc.invalidateQueries({ queryKey: storageKeys.current });
    },
    onError: (e) => message.error(describeApiError(e, '切换失败')),
  });

  const onCancel = () => {
    setMode(null);
    setForm(EMPTY_FORM);
    setTestState('idle');
    setTestMsg('');
  };
  const onSave = () => {
    if (!canSave) return;
    setConfirm(effMode === 'custom' ? 'custom' : 'platform');
  };

  const actions = (
    <>
      <WaBtn onClick={onCancel} disabled={mode == null}>
        取消
      </WaBtn>
      <WaBtn kind="primary" icon={<CheckOutlined />} onClick={onSave} disabled={!canSave}>
        保存
      </WaBtn>
    </>
  );

  let body;
  if (isError) {
    body = (
      <ServerError5xxPage
        {...(error instanceof ApiError && error.requestId ? { requestId: error.requestId } : {})}
        onRetry={() => void refetch()}
      />
    );
  } else if (isLoading || !data) {
    body = (
      <WaCard>
        <Skeleton active />
      </WaCard>
    );
  } else {
    body = (
      <>
        {/* 当前存储 */}
        <WaCard title="当前存储">
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '4px 0' }}>
            <div
              style={{
                width: 52,
                height: 52,
                borderRadius: 'var(--r-md)',
                background: usingCustom ? 'oklch(0.94 0.05 60)' : 'var(--brand-bg)',
                color: usingCustom ? 'oklch(0.45 0.12 60)' : 'var(--brand)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 26,
              }}
            >
              <CloudServerOutlined />
            </div>
            <div style={{ flex: 1 }}>
              <div
                style={{
                  fontSize: 16,
                  fontWeight: 600,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                }}
              >
                {usingCustom
                  ? `自有${vendorLabel(data.config?.backendType)} · ${data.config?.bucket || '—'}`
                  : '平台默认存储'}
                <StatusPill badge={toneBadge('success', '运行中')} />
              </div>
              <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>
                {usingCustom
                  ? `${data.config?.endpoint || ''} · 凭证加密保存`
                  : '由平台统一运维 · 加密：AES256 · 区域：cn-shanghai'}
              </div>
            </div>
          </div>
        </WaCard>

        {/* 选择存储方式 */}
        <WaCard
          title="存储方式"
          extra={
            <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
              切换不影响已上传的历史文件
            </span>
          }
        >
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            {(
              [
                {
                  v: 'platform',
                  t: '使用平台默认存储',
                  d: '推荐 · 开箱即用，由平台统一运维与扩容',
                  tag: '推荐',
                },
                {
                  v: 'custom',
                  t: '使用我自己的 OSS',
                  d: '文件存入您指定的对象存储，满足合规 / 数据出境要求',
                  tag: 'KA / 合规',
                },
              ] as const
            ).map((opt) => {
              const checked = effMode === opt.v;
              return (
                <div
                  key={opt.v}
                  onClick={() => {
                    if (!writable) return;
                    setMode(opt.v);
                    setTestState('idle');
                    setTestMsg('');
                  }}
                  style={{
                    padding: 16,
                    cursor: writable ? 'pointer' : 'not-allowed',
                    border: `1.5px solid ${checked ? 'var(--brand)' : 'var(--border)'}`,
                    borderRadius: 'var(--r-md)',
                    background: checked ? 'var(--brand-bg)' : 'var(--bg-elev)',
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 12,
                    opacity: writable ? 1 : 0.7,
                  }}
                >
                  <Radio checked={checked} />
                  <div style={{ flex: 1 }}>
                    <div
                      style={{
                        fontSize: 14,
                        fontWeight: 600,
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                      }}
                    >
                      {opt.t}
                      <span
                        style={{
                          fontSize: 11,
                          padding: '1px 6px',
                          borderRadius: 3,
                          background: 'var(--bg-muted)',
                          color: 'var(--text-secondary)',
                          fontWeight: 500,
                        }}
                      >
                        {opt.tag}
                      </span>
                    </div>
                    <div
                      style={{
                        fontSize: 12,
                        color: 'var(--text-tertiary)',
                        marginTop: 4,
                        lineHeight: 1.6,
                      }}
                    >
                      {opt.d}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {/* 自有 OSS 配置表单 */}
          {effMode === 'custom' && (
            <div style={{ marginTop: 18, paddingTop: 18, borderTop: '1px dashed var(--border)' }}>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'minmax(140px, 180px) 1fr',
                  rowGap: 16,
                  columnGap: 24,
                }}
              >
                <Lbl required>云厂商</Lbl>
                <div>
                  <Select
                    value={form.vendor === 'tencent_cos' ? 'tencent-cos' : 'aliyun-oss'}
                    disabled={!writable}
                    onChange={(v: string) =>
                      setVendor(v === 'tencent-cos' ? 'tencent_cos' : 'aliyun_oss')
                    }
                    options={[
                      { value: 'aliyun-oss', label: '阿里云 OSS' },
                      { value: 'tencent-cos', label: '腾讯云 COS' },
                    ]}
                    style={{ width: '100%', maxWidth: 320 }}
                  />
                </div>

                <Lbl required>Bucket 名称</Lbl>
                <Input
                  value={form.bucket}
                  placeholder="例：wen-mingyuan-prod"
                  disabled={!writable}
                  onChange={(e) => setF('bucket', e.target.value)}
                  style={{ maxWidth: 420 }}
                />

                <Lbl required>{copy.endpointLabel}</Lbl>
                <div>
                  <Input
                    value={form.endpoint}
                    placeholder={copy.endpointPlaceholder}
                    disabled={!writable}
                    onChange={(e) => setF('endpoint', e.target.value)}
                    style={{ maxWidth: 420 }}
                  />
                  <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>
                    {copy.endpointHint}
                  </div>
                </div>

                <Lbl required>{copy.keyIdLabel}</Lbl>
                <Input
                  value={form.accessKeyId}
                  placeholder={copy.keyIdPlaceholder}
                  disabled={!writable}
                  onChange={(e) => setF('accessKeyId', e.target.value)}
                  style={{ maxWidth: 420, fontFamily: 'var(--font-mono)' }}
                />

                <Lbl required>{copy.keySecretLabel}</Lbl>
                <div>
                  <Input.Password
                    value={form.accessKeySecret}
                    placeholder={usingCustom ? '********（留空则不变）' : '请输入'}
                    disabled={!writable}
                    onChange={(e) => setF('accessKeySecret', e.target.value)}
                    style={{ maxWidth: 420, fontFamily: 'var(--font-mono)' }}
                  />
                  <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>
                    <LockOutlined style={{ marginRight: 4 }} />
                    AES 加密落库 · 回显永远显示{' '}
                    <span style={{ fontFamily: 'var(--font-mono)' }}>********</span>，重填才更新
                  </div>
                </div>

                <Lbl>存储路径前缀</Lbl>
                <div>
                  <Input
                    value={form.pathPrefix}
                    placeholder="可选，例：mingyuan/"
                    disabled={!writable}
                    onChange={(e) => setF('pathPrefix', e.target.value)}
                    style={{ maxWidth: 420, fontFamily: 'var(--font-mono)' }}
                  />
                  <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>
                    多租户共用 Bucket 时区分用，所有该租户文件自动加此前缀
                  </div>
                </div>

                <Lbl>测试连接</Lbl>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                  <WaBtn
                    onClick={() => test.mutate()}
                    disabled={!writable || !requiredFilled || testState === 'testing'}
                    icon={testState === 'testing' ? <LoadingOutlined /> : <ReloadOutlined />}
                  >
                    {testState === 'testing' ? '测试中…' : '测试连接'}
                  </WaBtn>
                  <TestPill state={testState} msg={testMsg} />
                  {writable && !requiredFilled ? (
                    <span style={{ fontSize: 12, color: 'var(--danger)' }}>
                      请先填写必填项（Bucket / {copy.endpointLabel} / {copy.keyIdLabel} /{' '}
                      {copy.keySecretLabel}）后再测试
                    </span>
                  ) : (
                    testState === 'idle' && (
                      <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
                        保存前<b style={{ color: 'var(--danger)' }}>必须</b>通过测试 ·
                        系统将上传一个 1KB 测试文件并删除
                      </span>
                    )
                  )}
                </div>
              </div>
            </div>
          )}

          {canEdit && (
            <div
              style={{
                display: 'flex',
                justifyContent: 'flex-end',
                gap: 8,
                marginTop: 18,
                paddingTop: 16,
                borderTop: '1px solid var(--divider)',
              }}
            >
              {actions}
            </div>
          )}
        </WaCard>

        {/* 切换说明 */}
        <div
          style={{
            padding: 14,
            background: 'var(--warning-bg)',
            border: '1px solid #FED7AA',
            borderRadius: 'var(--r-md)',
            fontSize: 13,
            lineHeight: 1.75,
            color: 'var(--warning)',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              fontWeight: 600,
              marginBottom: 4,
            }}
          >
            <WarningOutlined /> 切换说明
          </div>
          <ul style={{ margin: 0, paddingLeft: 22 }}>
            <li>
              切换后<b>新上传的文件</b>存入新的存储位置
            </li>
            <li>
              <b>历史文件保留在原存储</b>且下载 / 预览仍正常 — 文件元数据记录所在后端，签名 URL
              按文件后端生成
            </li>
            <li>如需迁移历史文件请联系运营（当前版本不提供自助迁移）</li>
            <li>
              切换操作需<b>输入租户标识二次确认</b>，并记录到操作日志
            </li>
            <li>
              <b>大文件断点续传</b>：阿里云 OSS / 腾讯云 COS 直传模式下，客户端 SDK 原生支持分片上传
              + 断点续传，无需额外配置
            </li>
          </ul>
        </div>

        {!canEdit && (
          <Typography.Text type="secondary">
            存储设置为敏感配置，仅主管理员 / 授权管理员可修改。
          </Typography.Text>
        )}
      </>
    );
  }

  return (
    <WaPage>
      <WaPageHead
        title="存储设置"
        hint="系统管理 / 存储设置"
        badges={<StatusPill badge={toneBadge('warning', '敏感操作 · 仅主管理员可改')} />}
      />
      {body}

      <DangerConfirm
        open={confirm === 'custom'}
        title="切换存储后端"
        lead={`您即将把新上传文件存入您自己的${vendorLabel(form.vendor)}。`}
        consequences={[
          '历史文件保留在原存储，下载 / 预览仍正常',
          '如需迁移历史文件请联系运营',
          '此操作记录在操作日志',
        ]}
        confirmPhrase={tenantKey}
        confirmLabel={<>输入租户标识 {tenantKey} 以确认</>}
        confirming={switchCustom.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => switchCustom.mutate()}
      />
      <DangerConfirm
        open={confirm === 'platform'}
        title="切换回平台默认存储"
        lead="您即将把新上传文件存回平台默认存储。"
        consequences={['历史文件保留在原私有 OSS', '此操作记录在操作日志']}
        confirmPhrase={tenantKey}
        confirmLabel={<>输入租户标识 {tenantKey} 以确认</>}
        confirming={switchPlatform.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => switchPlatform.mutate()}
      />
    </WaPage>
  );
}

function vendorLabel(backendType: string | null | undefined): string {
  return backendType === 'tencent_cos' ? '腾讯云 COS' : '阿里云 OSS';
}

function Radio({ checked }: { checked: boolean }) {
  return (
    <span
      style={{
        width: 18,
        height: 18,
        borderRadius: '50%',
        flexShrink: 0,
        border: `1.5px solid ${checked ? 'var(--brand)' : 'var(--border-strong)'}`,
        background: '#fff',
        position: 'relative',
        marginTop: 1,
      }}
    >
      {checked && (
        <span
          style={{
            position: 'absolute',
            inset: 3,
            borderRadius: '50%',
            background: 'var(--brand)',
          }}
        />
      )}
    </span>
  );
}
