import { useState } from 'react';
import {
  Alert,
  App,
  Button,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  QRCode,
  Select,
  Spin,
  Statistic,
  Tooltip,
  Typography,
} from 'antd';
import type { FormInstance } from 'antd';
import { CheckCircleOutlined, CopyOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, opsTenantApi, opsUserApi, type CreatedTenantView } from '@wen-saas/api-client';
import { CountChip, WizardModal, type WizardStep } from '@wen-saas/shared-ui';
import type { Dayjs } from 'dayjs';
import { http } from '../../lib/http';
import { dashboardKeys } from '../dashboard/dashboardQueries';
import { useOpsPackages } from './hooks/useOpsPackages';
import { tenantWebHost, tenantWebUrl } from '../../lib/tenantWebUrl';

const GB = 1024 ** 3;

const INDUSTRY_OPTIONS = ['SaaS / 数据服务', '制造业', '零售', '金融', '教育', '医疗', '其他'];
const SCALE_OPTIONS = ['1-20', '20-50', '50-200', '200-500', '500+'];

type FormData = {
  tenantName: string;
  tenantCode: string;
  tenantRemark: string;
  csmUserId?: string;
  fullName: string;
  uscc?: string;
  industry?: string;
  scale?: string;
  region?: string;
  tags?: string[];
  packageCodes: string[];
  memberCap?: number;
  storageCapGb?: number;
  expireAt?: Dayjs;
  timezone?: string;
  adminUsername: string;
  adminRealName: string;
  adminMobile: string;
  adminEmail: string;
};

const STEP_FIELDS: Record<number, (keyof FormData)[]> = {
  0: ['tenantName', 'tenantCode', 'fullName', 'uscc'],
  1: ['packageCodes'],
  2: [],
  3: ['adminUsername', 'adminRealName', 'adminMobile', 'adminEmail'],
  4: [],
};

type Props = {
  open: boolean;
  onClose: () => void;
  onCreated: (view: CreatedTenantView) => void;
};

export function OpenTenantWizardModal({ open, onClose, onCreated }: Props) {
  const { message } = App.useApp();
  const [currentStep, setCurrentStep] = useState(0);
  const [result, setResult] = useState<CreatedTenantView | null>(null);
  const [form] = Form.useForm<FormData>();
  const qc = useQueryClient();

  // 客户经理候选人（OPS-T-002，选填）
  const { data: csmUsers } = useQuery({
    queryKey: ['ops', 'users'],
    queryFn: () => opsUserApi(http).listOpsUsers(),
    staleTime: 5 * 60 * 1000,
  });

  const { mutateAsync, isPending } = useMutation({
    mutationFn: (data: FormData) =>
      opsTenantApi(http).createTenant({
        tenantName: data.tenantName,
        tenantCode: data.tenantCode,
        tenantRemark: data.tenantRemark || null,
        adminUsername: data.adminUsername,
        adminRealName: data.adminRealName,
        adminMobile: data.adminMobile,
        adminEmail: data.adminEmail,
        csmUserId: data.csmUserId || null,
        fullName: data.fullName || null,
        uscc: data.uscc || null,
        industry: data.industry || null,
        scale: data.scale || null,
        region: data.region || null,
        tags: data.tags && data.tags.length > 0 ? data.tags : null,
        memberCap: data.memberCap ?? null,
        storageCap: data.storageCapGb != null ? data.storageCapGb * GB : null,
        expireAt: data.expireAt ? data.expireAt.toISOString() : null,
        timezone: data.timezone || null,
        packageCodes: data.packageCodes ?? ['base'],
      }),
  });

  const validateStep = async () => {
    const fields = STEP_FIELDS[currentStep] ?? [];
    if (fields.length === 0) return true;
    return form
      .validateFields(fields)
      .then(() => true)
      .catch(() => false);
  };

  const resetAll = () => {
    form.resetFields();
    setResult(null);
    setCurrentStep(0);
  };

  const handleClose = () => {
    // 完成态关闭：刷新列表
    if (result) void qc.invalidateQueries({ queryKey: ['ops', 'tenants'] });
    resetAll();
    onClose();
  };

  const handleViewDetail = () => {
    const view = result;
    resetAll();
    if (view) onCreated(view);
  };

  const handleFinish = async () => {
    // getFieldsValue(true)：确认步时前几步 Form.Item 已卸载，需取 preserve 的全量值
    const values = form.getFieldsValue(true) as FormData;
    try {
      const view = await mutateAsync(values);
      setResult(view);
      void qc.invalidateQueries({ queryKey: ['ops', 'tenants'] });
      // 工作台「在租户数」「最新动态」随开通刷新（X1-01）
      void qc.invalidateQueries({ queryKey: dashboardKeys.all });
    } catch (err) {
      if (err instanceof ApiError) {
        const fe = err.fieldErrors;
        const firstFieldMsg = Object.values(fe)[0];
        if (Object.keys(fe).length > 0) {
          form.setFields(
            Object.entries(fe).map(([name, msg]) => ({
              name: name as keyof FormData,
              errors: [msg ?? ''],
            })),
          );
          // jump to step 0 if there are field errors for step-0 fields
          const step0Fields = STEP_FIELDS[0] ?? [];
          if (Object.keys(fe).some((k) => step0Fields.includes(k as keyof FormData))) {
            setCurrentStep(0);
          }
        }
        // 优先展示后端 errors[] 首条具体原因：确认步提交时字段已卸载，内联挂错跳回后易丢，
        // detail 又是通用「请求字段校验失败」，故 toast 直接兜住真因。
        void message.error(
          firstFieldMsg ?? err.problem.detail ?? err.problem.title ?? '开通失败，请检查填写内容',
        );
      } else {
        void message.error('网络错误，请稍后重试');
      }
    }
  };

  // 确认步回显：读取 preserve 的全量值（含已卸载步骤字段）。currentStep 变化即触发重渲染。
  const values = form.getFieldsValue(true) as Partial<FormData>;

  const copyToClipboard = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      void message.success(`${label}已复制`);
    } catch {
      void message.error('复制失败，请手动复制');
    }
  };

  // 完成态：开通成功后单独渲染结果页（不再作为向导的一步）
  if (result) {
    return (
      <Modal
        open={open}
        title="开通新租户"
        width={720}
        mask={{ closable: false }}
        onCancel={handleClose}
        footer={
          <div
            style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'flex-end' }}
          >
            <Button onClick={handleClose}>关闭</Button>
            <Button type="primary" onClick={handleViewDetail}>
              查看租户详情
            </Button>
          </div>
        }
      >
        <CompleteStep result={result} onCopy={copyToClipboard} />
      </Modal>
    );
  }

  const steps: WizardStep[] = [
    {
      key: 'basic',
      title: '基础信息',
      onBeforeNext: validateStep,
      content: (
        <div>
          <Form.Item
            name="tenantName"
            label="租户名称"
            rules={[
              { required: true, message: '请输入租户名称' },
              { max: 64, message: '不超过 64 个字符' },
            ]}
          >
            <Input placeholder="例：Acme Corporation" autoFocus maxLength={64} showCount />
          </Form.Item>
          <Form.Item
            name="tenantCode"
            label={
              <span>
                租户标识&nbsp;
                <Tooltip title="全局唯一，由小写字母、数字、连字符组成，创建后不可修改">
                  <InfoCircleOutlined style={{ color: 'var(--text-tertiary)' }} />
                </Tooltip>
              </span>
            }
            rules={[
              { required: true, message: '请输入租户标识' },
              {
                // 与后端 CreateTenantRequest / TenantCodePolicy 同口径：合法 DNS label，5–16 位、首尾不能是连字符
                pattern: /^[a-z0-9][a-z0-9-]{3,14}[a-z0-9]$/,
                message: '小写字母/数字/连字符，5–16 位，首尾须为字母或数字',
              },
            ]}
          >
            <Input placeholder="例：acme-corp" style={{ fontFamily: 'var(--font-mono)' }} />
          </Form.Item>
          <TenantCodePreview form={form} />
          <Form.Item name="csmUserId" label="客户经理（选填）">
            <Select
              allowClear
              showSearch
              placeholder="选择客户经理负责人"
              optionFilterProp="label"
              options={(csmUsers ?? []).map((u) => ({
                value: u.userId,
                label: u.realName ? `${u.realName}（${u.username}）` : u.username,
              }))}
            />
          </Form.Item>

          <Typography.Text
            type="secondary"
            style={{ fontSize: 12, display: 'block', margin: '4px 0 12px' }}
          >
            公司信息（选填，用于工单/合同与对外资料归档）
          </Typography.Text>
          <Form.Item
            name="fullName"
            label="公司全称"
            rules={[{ max: 128, message: '不超过 128 个字符' }]}
          >
            <Input placeholder="例：北京蓝鲸数据科技有限公司" maxLength={128} />
          </Form.Item>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Form.Item
              name="uscc"
              label="统一社会信用代码"
              rules={[
                {
                  // 选填；填了必须是 18 位（数字 / 大写字母），拦截位数/格式错误
                  validator: (_, value: unknown) =>
                    !value || (typeof value === 'string' && /^[0-9A-Z]{18}$/.test(value))
                      ? Promise.resolve()
                      : Promise.reject(new Error('统一社会信用代码为 18 位（数字 / 大写字母）')),
                },
              ]}
            >
              <Input
                placeholder="18 位"
                style={{ fontFamily: 'var(--font-mono)' }}
                maxLength={18}
              />
            </Form.Item>
            <Form.Item name="region" label="所在地区">
              <Input placeholder="例：北京 · 海淀" />
            </Form.Item>
            <Form.Item name="industry" label="行业">
              <Select
                allowClear
                placeholder="选择行业"
                options={INDUSTRY_OPTIONS.map((v) => ({ value: v, label: v }))}
              />
            </Form.Item>
            <Form.Item name="scale" label="规模">
              <Select
                allowClear
                placeholder="选择规模"
                options={SCALE_OPTIONS.map((v) => ({ value: v, label: `${v} 人` }))}
              />
            </Form.Item>
          </div>
          <Form.Item name="tags" label="标签">
            <Select
              mode="tags"
              allowClear
              placeholder="回车添加，如：重点客户、试点"
              tokenSeparators={[',', '，']}
            />
          </Form.Item>
        </div>
      ),
    },
    {
      key: 'package',
      title: '套餐选择',
      onBeforeNext: validateStep,
      content: (
        <Form.Item
          name="packageCodes"
          initialValue={['base']}
          rules={[{ required: true, message: '请至少选择一个套餐' }]}
        >
          <PackageSelector />
        </Form.Item>
      ),
    },
    {
      key: 'quota',
      title: '配额与到期',
      content: (
        <div>
          <Typography.Paragraph type="secondary" style={{ fontSize: 13, marginBottom: 16 }}>
            配额与到期均为选填，留空则按套餐默认值开通。价格不在系统体现，由运营线下管理。
          </Typography.Paragraph>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Form.Item name="memberCap" label="成员上限">
              <InputNumber
                min={1}
                max={100000}
                style={{ width: '100%' }}
                addonAfter="人"
                placeholder="留空=不限制"
              />
            </Form.Item>
            <Form.Item name="storageCapGb" label="存储上限">
              <InputNumber
                min={1}
                max={1048576}
                style={{ width: '100%' }}
                addonAfter="GB"
                placeholder="留空=不限制"
              />
            </Form.Item>
            <Form.Item name="expireAt" label="到期时间">
              <DatePicker style={{ width: '100%' }} placeholder="留空=永久" showTime={false} />
            </Form.Item>
            <Form.Item name="timezone" label="时区" initialValue="Asia/Shanghai">
              <Select options={[{ value: 'Asia/Shanghai', label: 'Asia/Shanghai (+08:00)' }]} />
            </Form.Item>
          </div>
          <Alert
            type="info"
            showIcon
            message="到期前 30 / 7 / 3 / 1 天预警，到期后租户转只读。开通后可在租户详情「套餐包」Tab 续费管理。"
            style={{ marginTop: 4 }}
          />
        </div>
      ),
    },
    {
      key: 'admin',
      title: '主管理员',
      onBeforeNext: validateStep,
      content: (
        <div>
          <Form.Item
            name="adminUsername"
            label={
              <span>
                管理员账号&nbsp;
                <Tooltip title="3–32 位，字母/数字/下划线/连字符/点，全局唯一">
                  <InfoCircleOutlined style={{ color: 'var(--text-tertiary)' }} />
                </Tooltip>
              </span>
            }
            rules={[
              { required: true, message: '请输入管理员账号' },
              { min: 3, message: '至少 3 个字符' },
              { max: 32, message: '不超过 32 个字符' },
              {
                pattern: /^[a-zA-Z0-9_\-.]+$/,
                message: '只允许字母、数字、下划线、连字符、点',
              },
            ]}
          >
            <Input placeholder="例：admin" style={{ fontFamily: 'var(--font-mono)' }} />
          </Form.Item>
          <Form.Item
            name="adminRealName"
            label="管理员姓名"
            rules={[
              { required: true, message: '请输入管理员姓名' },
              { max: 64, message: '不超过 64 个字符' },
            ]}
          >
            <Input placeholder="例：张三" />
          </Form.Item>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Form.Item
              name="adminMobile"
              label={
                <span>
                  手机号&nbsp;
                  <Tooltip title="用于自助找回密码与登录提醒">
                    <InfoCircleOutlined style={{ color: 'var(--text-tertiary)' }} />
                  </Tooltip>
                </span>
              }
              rules={[
                { required: true, message: '请输入主管理员手机号' },
                { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的 11 位手机号' },
              ]}
            >
              <Input placeholder="例：13800000000" style={{ fontFamily: 'var(--font-mono)' }} />
            </Form.Item>
            <Form.Item
              name="adminEmail"
              label="邮箱"
              rules={[
                { required: true, message: '请输入主管理员邮箱' },
                { type: 'email', message: '请输入有效的邮箱' },
              ]}
            >
              <Input placeholder="例：admin@example.com" />
            </Form.Item>
          </div>
          <Alert
            type="info"
            showIcon
            message="开通完成后将生成 48h 有效的密码设置链接，并自动发送到主管理员邮箱；如未收到可在完成页复制链接线下转发。"
            style={{ marginTop: 8 }}
          />
        </div>
      ),
    },
    {
      key: 'confirm',
      title: '确认开通',
      content: (
        <div>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 16, fontSize: 13 }}>
            请确认以下信息无误后点击「开通」。
          </Typography.Paragraph>
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="租户名称">{values.tenantName || '—'}</Descriptions.Item>
            <Descriptions.Item label="租户标识">
              <code style={{ fontFamily: 'var(--font-mono)' }}>{values.tenantCode || '—'}</code>
            </Descriptions.Item>
            <Descriptions.Item label="公司全称">{values.fullName || '—'}</Descriptions.Item>
            <Descriptions.Item label="行业 / 规模">
              {[values.industry, values.scale ? `${values.scale} 人` : null]
                .filter(Boolean)
                .join(' · ') || '—'}
            </Descriptions.Item>
            <Descriptions.Item label="客户经理">
              {csmUsers?.find((u) => u.userId === values.csmUserId)?.realName ??
                csmUsers?.find((u) => u.userId === values.csmUserId)?.username ??
                '—'}
            </Descriptions.Item>
            <Descriptions.Item label="运营备注">{values.tenantRemark || '—'}</Descriptions.Item>
            <Descriptions.Item label="套餐">
              {(values.packageCodes ?? ['base']).join('、') || '基础包'}
            </Descriptions.Item>
            <Descriptions.Item label="配额 / 到期">
              {[
                values.memberCap != null ? `${values.memberCap} 人` : null,
                values.storageCapGb != null ? `${values.storageCapGb} GB` : null,
                values.expireAt ? values.expireAt.format('YYYY-MM-DD') : '永久',
              ]
                .filter(Boolean)
                .join(' · ')}
            </Descriptions.Item>
            <Descriptions.Item label="管理员账号">{values.adminUsername || '—'}</Descriptions.Item>
            <Descriptions.Item label="管理员姓名">{values.adminRealName || '—'}</Descriptions.Item>
            <Descriptions.Item label="管理员手机 / 邮箱">
              {[values.adminMobile, values.adminEmail].filter(Boolean).join(' · ') || '—'}
            </Descriptions.Item>
          </Descriptions>
          <Alert
            type="warning"
            showIcon
            message="密码设置链接 48 小时内有效，开通后将自动发送到主管理员邮箱。"
            style={{ marginTop: 16 }}
          />
        </div>
      ),
    },
  ];

  return (
    <Form form={form} layout="vertical" requiredMark={false}>
      <WizardModal
        open={open}
        title="开通新租户"
        steps={steps}
        current={currentStep}
        onChange={setCurrentStep}
        onCancel={handleClose}
        onFinish={handleFinish}
        finishText="开通"
        finishing={isPending}
        width={720}
      />
    </Form>
  );
}

// 租户标识子域名实时预览 —— 让运营明确标识即租户访问子域名（{code}.基础域）
function TenantCodePreview({ form }: { form: FormInstance<FormData> }) {
  const code = Form.useWatch('tenantCode', form);
  const trimmed = (code ?? '').trim();
  return (
    <Typography.Text
      type="secondary"
      style={{ fontSize: 12, display: 'block', margin: '-8px 0 12px' }}
    >
      {trimmed ? (
        <>
          将作为访问地址：
          <code style={{ fontFamily: 'var(--font-mono)' }}>{tenantWebHost(trimmed)}</code>
        </>
      ) : (
        `将作为租户访问子域名，如 ${tenantWebHost('acme-corp')}`
      )}
    </Typography.Text>
  );
}

// Package selector sub-component —— 套餐目录来自后端 `GET /ops/packages`（OPS-T-011）
function PackageSelector({
  value,
  onChange,
}: {
  value?: string[];
  onChange?: (v: string[]) => void;
}) {
  const { catalog, isLoading } = useOpsPackages();
  const selected = value ?? [];
  const toggle = (code: string, enabled: boolean) => {
    if (!enabled) return;
    const next = selected.includes(code) ? selected.filter((c) => c !== code) : [...selected, code];
    onChange?.(next);
  };

  if (isLoading) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(2, 1fr)',
        gap: 12,
      }}
    >
      {catalog.map((pkg) => {
        const enabled = pkg.status === 'ACTIVE';
        const isSelected = selected.includes(pkg.code);
        return (
          <div
            key={pkg.code}
            onClick={() => toggle(pkg.code, enabled)}
            style={{
              padding: '12px 16px',
              borderRadius: 'var(--r-md)',
              border: `1.5px solid ${isSelected ? 'var(--brand)' : 'var(--border)'}`,
              background: isSelected ? 'var(--brand-bg)' : 'var(--bg-surface)',
              cursor: enabled ? 'pointer' : 'not-allowed',
              opacity: enabled ? 1 : 0.5,
              position: 'relative',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
              <span style={{ fontWeight: 600, fontSize: 14 }}>{pkg.name}</span>
              {!enabled && <CountChip>已下架</CountChip>}
              {isSelected && enabled && (
                <CheckCircleOutlined style={{ color: 'var(--brand)', marginLeft: 'auto' }} />
              )}
            </div>
            {pkg.description && (
              <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>{pkg.description}</div>
            )}
          </div>
        );
      })}
    </div>
  );
}

function CompleteStep({
  result,
  onCopy,
}: {
  result: CreatedTenantView;
  onCopy: (text: string, label: string) => Promise<void>;
}) {
  const resetUrl = tenantWebUrl(result.tenantCode, `/reset-password/${result.resetToken}`);
  const expiresAt = new Date(result.resetExpiresAt).getTime();

  return (
    <div>
      <div style={{ textAlign: 'center', marginBottom: 20 }}>
        <CheckCircleOutlined
          style={{ fontSize: 40, color: 'var(--success)', marginBottom: 8, display: 'block' }}
        />
        <Typography.Title level={4} style={{ margin: '8px 0 4px' }}>
          租户已创建
        </Typography.Title>
        <Typography.Text type="secondary" style={{ fontSize: 13 }}>
          设密码链接已自动发送至主管理员邮箱；也可复制下方链接线下转发
        </Typography.Text>
      </div>

      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 6 }}>
            密码设置链接（48 小时内有效）
          </Typography.Paragraph>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 10px',
              background: 'var(--bg-muted)',
              borderRadius: 'var(--r-md)',
              border: '1px solid var(--border)',
              wordBreak: 'break-all',
            }}
          >
            <Typography.Text style={{ flex: 1, fontFamily: 'var(--font-mono)', fontSize: 11 }}>
              {resetUrl}
            </Typography.Text>
            <Button
              icon={<CopyOutlined />}
              size="small"
              onClick={() => void onCopy(resetUrl, '密码设置链接')}
            >
              复制
            </Button>
          </div>
          <div style={{ marginTop: 12 }}>
            <Statistic.Countdown
              title="链接有效期剩余"
              value={expiresAt}
              format="D 天 H:mm:ss"
              valueStyle={{ fontSize: 16 }}
            />
          </div>
          <Descriptions column={1} size="small" style={{ marginTop: 16 }}>
            <Descriptions.Item label="主管理员账号">{result.adminUsername}</Descriptions.Item>
            <Descriptions.Item label="租户标识">
              <code style={{ fontFamily: 'var(--font-mono)' }}>{result.tenantCode}</code>
            </Descriptions.Item>
            <Descriptions.Item label="内部编号">{result.tenantNo}</Descriptions.Item>
          </Descriptions>
        </div>
        <div style={{ flexShrink: 0, textAlign: 'center' }}>
          <QRCode value={resetUrl} size={140} />
          <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 4 }}>扫码打开</div>
        </div>
      </div>

      <Alert
        type="warning"
        showIcon
        style={{ marginTop: 16 }}
        message="操作须知"
        description={
          <ul style={{ margin: 0, paddingLeft: 20, fontSize: 12 }}>
            <li>开通时已自动向主管理员邮箱发送设密码链接邮件</li>
            <li>关闭后仍可在租户详情「主管理员」Tab 找回链接，过期可重新生成</li>
            <li>主管理员点开链接需先输入账号校验才能设密码，防止链接泄露</li>
            <li>激活前该租户在列表显示「待激活」状态</li>
          </ul>
        }
      />
    </div>
  );
}
