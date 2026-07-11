import { useEffect, useState } from 'react';
import { App, Input, Modal, Select, Skeleton, Tooltip, Upload } from 'antd';
import {
  EditOutlined,
  InfoCircleOutlined,
  WarningOutlined,
  CheckOutlined,
  CloseOutlined,
  UploadOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  CountChip,
  formatDate,
  ServerError5xxPage,
  StatusPill,
  toneBadge,
  useTabStatus,
} from '@wen-saas/shared-ui';
import {
  ApiError,
  describeApiError,
  type CompanyProfile,
  type DictOption,
} from '@wen-saas/api-client';
import { PermissionGate, useIsReadonly } from '@wen-saas/permissions';
import { companyApi, INDUSTRY_OPTIONS, SCALE_OPTIONS } from './companyApi';
import { KV, Lbl, WaBtn, WaCard, WaPage, WaPageHead } from '../../components/wa';

const LOGO_MAX = 1024 * 1024;
const LOGO_TYPES = ['image/png', 'image/jpeg', 'image/jpg', 'image/svg+xml'];

const companyKeys = {
  profile: ['company', 'profile'] as const,
  options: ['company', 'options'] as const,
};

/** 常量字典回退：后端 options 端点不可用 / 为空时兜底（正常走字典 INDUSTRY / COMPANY_SIZE）。 */
const FALLBACK_INDUSTRY: DictOption[] = INDUSTRY_OPTIONS.map((v) => ({ code: v, name: v }));
const FALLBACK_SCALE: DictOption[] = SCALE_OPTIONS.map((v) => ({ code: v, name: v }));

/**
 * 合并下拉项：以字典项为准，若当前已存值不在列表里则补一条占位（避免历史值被清空显示）。
 */
function withCurrent(options: DictOption[], current: string | null): DictOption[] {
  if (!current || options.some((o) => o.code === current)) return options;
  return [...options, { code: current, name: current }];
}

const GRID: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'minmax(140px, 200px) 1fr',
  rowGap: 18,
  columnGap: 24,
};

/** 子域名冷却态（封装 Date.now() 避免 render 内直接调用触发 purity 规则）。 */
function computeCooldown(changeableAtIso: string | null): {
  cooldown: boolean;
  until: Date | null;
} {
  if (!changeableAtIso) return { cooldown: false, until: null };
  const until = new Date(changeableAtIso);
  return { cooldown: until.getTime() > Date.now(), until };
}

function fmtDate(iso: string | null): string {
  return formatDate(iso);
}

function useCompanyProfile() {
  return useQuery({ queryKey: companyKeys.profile, queryFn: () => companyApi.get() });
}

function useCompanyOptions() {
  return useQuery({
    queryKey: companyKeys.options,
    queryFn: () => companyApi.getOptions(),
    staleTime: 5 * 60 * 1000,
  });
}

/** 租户后台 · 系统管理 · 公司信息。 */
export function CompanyPage() {
  const { data, isLoading, isError, error, refetch } = useCompanyProfile();

  if (isError) {
    return (
      <WaPage>
        <ServerError5xxPage
          {...(error instanceof ApiError && error.requestId ? { requestId: error.requestId } : {})}
          onRetry={() => void refetch()}
        />
      </WaPage>
    );
  }
  if (isLoading || !data) {
    return (
      <WaPage>
        <WaCard>
          <Skeleton active />
        </WaCard>
      </WaPage>
    );
  }
  // key=tenantId|codeChangedAt 让服务端数据变化时整段重挂，规避 set-state-in-effect。
  return <CompanyForm key={`${data.tenantId}-${data.codeChangedAt ?? ''}`} profile={data} />;
}

function CompanyForm({ profile }: { profile: CompanyProfile }) {
  const { message } = App.useApp();
  const qc = useQueryClient();
  const readonly = useIsReadonly();
  const invalidate = () => qc.invalidateQueries({ queryKey: companyKeys.profile });

  const optionsQuery = useCompanyOptions();
  const rawIndustries = optionsQuery.data?.industries?.length
    ? optionsQuery.data.industries
    : FALLBACK_INDUSTRY;
  const rawScales = optionsQuery.data?.scales?.length ? optionsQuery.data.scales : FALLBACK_SCALE;

  const [editingBasic, setEditingBasic] = useState(false);
  const [industry, setIndustry] = useState(profile.industry ?? '');
  const [scale, setScale] = useState(profile.scale ?? '');
  const [description, setDescription] = useState(profile.description ?? '');
  const [slot, setSlot] = useState(profile.routeSlot);
  const [renameOpen, setRenameOpen] = useState(false);

  const saveBasic = useMutation({
    mutationFn: () =>
      companyApi.updateBasic({
        industry: industry || null,
        scale: scale || null,
        description: description || null,
      }),
    onSuccess: () => {
      message.success('已保存');
      setEditingBasic(false);
      void invalidate();
    },
    onError: (e) => message.error(describeApiError(e, '保存失败')),
  });
  const uploadLogo = useMutation({
    mutationFn: (file: File) => companyApi.uploadLogo(file),
    onSuccess: () => {
      message.success('Logo 已更新');
      void invalidate();
    },
    onError: (e) => message.error(describeApiError(e, '上传失败')),
  });
  const removeLogo = useMutation({
    mutationFn: () => companyApi.removeLogo(),
    onSuccess: () => {
      message.success('已恢复默认 Logo');
      void invalidate();
    },
    onError: (e) => message.error(describeApiError(e, '删除失败')),
  });
  const saveSlot = useMutation({
    mutationFn: (v: string) => companyApi.changeRouteSlot(v),
    onSuccess: () => {
      message.success('子域名已更新');
      void invalidate();
    },
    onError: (e) => message.error(describeApiError(e, '子域名修改失败')),
  });

  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    if (!LOGO_TYPES.includes(file.type)) {
      message.error('仅支持 PNG / JPG / SVG');
      return Upload.LIST_IGNORE;
    }
    if (file.size > LOGO_MAX) {
      message.error('Logo 不能超过 1MB');
      return Upload.LIST_IGNORE;
    }
    uploadLogo.mutate(file);
    return Upload.LIST_IGNORE;
  };

  // 子域名基础域（本地 localhost / 线上 saas.xxx）；H5 端域为 m.{基础域}。
  const baseDomain = (import.meta.env.VITE_TENANT_BASE_DOMAIN as string) || 'example.com';
  const { cooldown, until } = computeCooldown(profile.routeSlotChangeableAt);
  const slotDirty = slot.trim() !== '' && slot !== profile.routeSlot;
  // 子域名规则：合法 DNS label——小写字母或数字开头，小写字母 / 数字 / 连字符，不以连字符结尾，5-16 位。
  const slotInvalid = slotDirty && !/^[a-z0-9][a-z0-9-]{3,14}[a-z0-9]$/.test(slot.trim());
  // 预览仅回显合法子域名，非法/空输入回退占位符，避免展示明显非法地址（T7-03）。
  const previewSlot = slot.trim() && !slotInvalid ? slot.trim() : '{子域名}';

  const basicDirty =
    industry !== (profile.industry ?? '') ||
    scale !== (profile.scale ?? '') ||
    description !== (profile.description ?? '');

  // 脏标签对接示范：编辑中有未保存改动（基本信息）或子域名待保存时，标记本页签为「未保存」，
  // 误关 / 刷新会被二次确认拦截，避免丢失填写内容。保存 / 取消后派生量归零、自动清脏。
  const { setDirty } = useTabStatus();
  const tabDirty = (editingBasic && basicDirty) || slotDirty;
  useEffect(() => {
    setDirty(tabDirty);
  }, [tabDirty, setDirty]);

  const cancelBasic = () => {
    setIndustry(profile.industry ?? '');
    setScale(profile.scale ?? '');
    setDescription(profile.description ?? '');
    setEditingBasic(false);
  };

  return (
    <WaPage>
      <WaPageHead title="公司信息" hint="系统管理 / 公司信息" />

      {/* 基本信息 */}
      <WaCard
        title="基本信息"
        extra={
          editingBasic ? (
            <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
              公司名称需走「独立编辑流程」
            </span>
          ) : (
            <PermissionGate permission="company.edit">
              <WaBtn
                size="sm"
                icon={<EditOutlined />}
                disabled={readonly}
                onClick={() => setEditingBasic(true)}
              >
                编辑
              </WaBtn>
            </PermissionGate>
          )
        }
      >
        <div style={GRID}>
          <Lbl required>公司名称</Lbl>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            <Input
              value={profile.name}
              readOnly
              style={{ maxWidth: 420, background: 'var(--bg-muted)' }}
            />
            <PermissionGate permission="company.edit">
              <WaBtn size="sm" disabled={readonly} onClick={() => setRenameOpen(true)}>
                申请改名
              </WaBtn>
            </PermissionGate>
            <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
              <InfoCircleOutlined style={{ marginRight: 4 }} />
              影响合同 / 对外材料，需填写变更原因
            </span>
          </div>

          <Lbl>行业</Lbl>
          <div>
            <Select
              value={industry || undefined}
              disabled={!editingBasic}
              placeholder="请选择行业"
              allowClear
              onChange={(v?: string) => setIndustry(v ?? '')}
              options={withCurrent(rawIndustries, industry || null).map((o) => ({
                value: o.code,
                label: o.name,
              }))}
              style={{ width: '100%', maxWidth: 320 }}
            />
            <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>
              来自字典 <span style={{ fontFamily: 'var(--font-mono)' }}>INDUSTRY</span> ·
              可在字典管理中追加自定义项
            </div>
          </div>

          <Lbl>公司规模</Lbl>
          <div>
            <Select
              value={scale || undefined}
              disabled={!editingBasic}
              placeholder="请选择规模"
              allowClear
              onChange={(v?: string) => setScale(v ?? '')}
              options={withCurrent(rawScales, scale || null).map((o) => ({
                value: o.code,
                label: o.name,
              }))}
              style={{ width: '100%', maxWidth: 320 }}
            />
          </div>

          <Lbl>备注</Lbl>
          <Input.TextArea
            rows={3}
            value={description}
            maxLength={500}
            disabled={!editingBasic}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="自由文本，内部参考用"
            style={{ resize: 'vertical', minHeight: 70, maxWidth: 720 }}
          />
        </div>

        {editingBasic && (
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
            <WaBtn icon={<CloseOutlined />} onClick={cancelBasic}>
              取消
            </WaBtn>
            <WaBtn
              kind="primary"
              icon={<CheckOutlined />}
              disabled={!basicDirty || saveBasic.isPending || readonly}
              onClick={() => saveBasic.mutate()}
            >
              保存
            </WaBtn>
          </div>
        )}
      </WaCard>

      {/* 品牌定制 */}
      <WaCard title="品牌定制" extra={<CountChip>白标范围：Logo + 子域名</CountChip>}>
        <div style={{ ...GRID, rowGap: 24 }}>
          <Lbl>公司 Logo</Lbl>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16 }}>
            <div
              style={{
                width: 96,
                height: 96,
                border: '1px dashed var(--border-strong)',
                borderRadius: 'var(--r-md)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: 'var(--bg-muted)',
                fontWeight: 700,
                fontSize: 24,
                color: 'var(--brand)',
                overflow: 'hidden',
              }}
            >
              {profile.logoUrl ? (
                <img
                  src={profile.logoUrl}
                  alt="logo"
                  style={{ maxWidth: '90%', maxHeight: '90%' }}
                />
              ) : (
                (profile.name || 'LOGO').slice(0, 2)
              )}
            </div>
            <div>
              <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                <PermissionGate permission="company.edit">
                  <Upload
                    showUploadList={false}
                    beforeUpload={beforeUpload}
                    accept=".png,.jpg,.jpeg,.svg"
                    disabled={readonly}
                  >
                    <WaBtn icon={<UploadOutlined />} disabled={uploadLogo.isPending || readonly}>
                      上传
                    </WaBtn>
                  </Upload>
                  {profile.logoFileId ? (
                    <WaBtn
                      icon={<DeleteOutlined />}
                      disabled={removeLogo.isPending || readonly}
                      onClick={() => removeLogo.mutate()}
                    >
                      恢复默认
                    </WaBtn>
                  ) : null}
                </PermissionGate>
              </div>
              <ul
                style={{
                  margin: 0,
                  paddingLeft: 18,
                  fontSize: 12,
                  color: 'var(--text-tertiary)',
                  lineHeight: 1.8,
                }}
              >
                <li>建议尺寸 200 × 200，PNG / JPG / SVG（推荐 SVG 适配高清）</li>
                <li>大小不超过 1 MB</li>
                <li>显示位置：登录页、工作台 header、邮件落款、H5 端 header</li>
                <li>上传后立即生效，全租户用户刷新可见</li>
              </ul>
            </div>
          </div>

          <Lbl>白标子域名</Lbl>
          <div>
            <div
              style={{
                display: 'flex',
                alignItems: 'stretch',
                border: `1px solid ${slotInvalid ? 'var(--danger, #DC2626)' : 'var(--border)'}`,
                borderRadius: 'var(--r-md)',
                overflow: 'hidden',
                maxWidth: 560,
              }}
            >
              <input
                style={{
                  flex: 1,
                  border: 'none',
                  padding: '7px 10px',
                  fontFamily: 'var(--font-mono)',
                  minWidth: 0,
                  background: cooldown ? 'var(--bg-muted)' : '#fff',
                }}
                value={slot}
                disabled={cooldown}
                onChange={(e) => setSlot(e.target.value)}
                placeholder="子域名"
              />
              <span
                style={{
                  padding: '7px 12px',
                  background: 'var(--bg-muted)',
                  fontSize: 12,
                  color: 'var(--text-tertiary)',
                  fontFamily: 'var(--font-mono)',
                  whiteSpace: 'nowrap',
                  display: 'flex',
                  alignItems: 'center',
                }}
              >
                .{baseDomain}
              </span>
              {cooldown && (
                <span style={{ alignSelf: 'center', marginRight: 8 }}>
                  <StatusPill badge={toneBadge('warning', '30 天内已改 1 次')} />
                </span>
              )}
            </div>
            <div
              style={{
                marginTop: 8,
                display: 'flex',
                gap: 8,
                alignItems: 'center',
                flexWrap: 'wrap',
              }}
            >
              <PermissionGate permission="company.edit">
                <WaBtn
                  size="sm"
                  kind="primary"
                  disabled={cooldown || !slotDirty || slotInvalid || saveSlot.isPending || readonly}
                  onClick={() => saveSlot.mutate(slot.trim())}
                >
                  {saveSlot.isPending ? '保存中…' : '保存子域名'}
                </WaBtn>
              </PermissionGate>
              <span
                style={{
                  fontSize: 12,
                  color: slotInvalid ? 'var(--danger, #DC2626)' : 'var(--text-tertiary)',
                }}
              >
                {slotInvalid
                  ? '子域名需小写字母或数字开头，仅含小写字母 / 数字 / 连字符，不以连字符结尾，5-16 位'
                  : '小写字母或数字开头，小写字母数字及连字符，5-16 位'}
              </span>
            </div>
            <div
              style={{
                marginTop: 12,
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: 12,
                maxWidth: 720,
              }}
            >
              <KV k="Web 端" v={`${previewSlot}.${baseDomain}`} mono />
              <KV k="H5 端" v={`${previewSlot}.m.${baseDomain}`} mono />
              <KV k="上次修改" v={fmtDate(profile.codeChangedAt)} />
              <KV
                k="下次可改"
                v={cooldown ? fmtDate(until?.toISOString() ?? null) : '立即可改'}
                highlight
              />
            </div>
            <div
              style={{
                marginTop: 10,
                padding: 10,
                background: 'var(--warning-bg)',
                border: '1px solid #FED7AA',
                borderRadius: 'var(--r-md)',
                fontSize: 12,
                color: 'var(--warning)',
              }}
            >
              <WarningOutlined style={{ marginRight: 6 }} />
              子域名 <b>全平台唯一</b> · 30 天内最多修改 1 次 · 修改后旧子域名 <b>90 天</b>{' '}
              内自动重定向到新地址。
            </div>
          </div>
        </div>
      </WaCard>

      <Modal
        title="修改公司名称"
        open={renameOpen}
        onCancel={() => setRenameOpen(false)}
        width={520}
        footer={null}
        destroyOnHidden
      >
        <div
          style={{
            padding: 12,
            background: 'var(--danger-bg)',
            border: '1px solid #FECACA',
            borderRadius: 'var(--r-md)',
            fontSize: 12,
            color: 'var(--danger)',
            margin: '8px 0 16px',
            lineHeight: 1.7,
          }}
        >
          <WarningOutlined style={{ marginRight: 6 }} />
          公司名称变更将影响合同 / 对外材料 / 发票抬头。该操作需联系您的客户经理（运营侧警示流程），
          本页不直接提交。
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Tooltip title="改名走运营警示流程，请联系客户经理">
            <span>
              <WaBtn onClick={() => setRenameOpen(false)}>我知道了</WaBtn>
            </span>
          </Tooltip>
        </div>
      </Modal>
    </WaPage>
  );
}
