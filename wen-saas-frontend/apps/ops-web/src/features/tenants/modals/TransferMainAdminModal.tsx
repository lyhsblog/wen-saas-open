import { useState } from 'react';
import {
  Alert,
  App,
  Form,
  Input,
  Modal,
  QRCode,
  Radio,
  Select,
  Statistic,
  Typography,
} from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { DangerConfirm } from '@wen-saas/shared-ui';
import { opsTenantApi } from '@wen-saas/api-client';
import { http } from '../../../lib/http';
import { applyApiErrorToAntdForm } from '../utils/form';
import { useTransferMainAdmin } from '../hooks/useTenantMutations';
import type { TransferMainAdminView, OldAdminHandling } from '@wen-saas/api-client';
import { tenantWebUrl } from '../../../lib/tenantWebUrl';

type Props = {
  open: boolean;
  tenantId: string;
  tenantCode: string;
  tenantName: string;
  currentAdminUsername: string;
  onClose: () => void;
};

type FormData = {
  mode: 'existing' | 'new';
  oldAdminHandling: OldAdminHandling;
  userId: string;
  username: string;
  realName: string;
};

export function TransferMainAdminModal({
  open,
  tenantId,
  tenantCode,
  tenantName,
  currentAdminUsername,
  onClose,
}: Props) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormData>();
  const [confirmOpen, setConfirmOpen] = useState(false);
  // 主弹窗 destroyOnHidden + open={open && !confirmOpen}：进入二次确认时主 Form 已被销毁，
  // 届时 form.getFieldsValue() 会返回 {}。故在校验通过（销毁前）即快照表单值，确认时提交快照。
  const [pendingValues, setPendingValues] = useState<FormData | null>(null);
  const [result, setResult] = useState<TransferMainAdminView | null>(null);
  const [memberKeyword, setMemberKeyword] = useState('');
  const { mutateAsync, isPending } = useTransferMainAdmin(tenantId);

  // 「从已有成员选」搜索（OPS-T-012）。仅在弹窗打开时查询。
  const { data: memberPage, isFetching: membersLoading } = useQuery({
    queryKey: ['ops', 'tenant', tenantId, 'members', memberKeyword],
    queryFn: () =>
      opsTenantApi(http).listTenantMembers(tenantId, {
        ...(memberKeyword ? { keyword: memberKeyword } : {}),
        pageSize: 20,
      }),
    enabled: open,
  });

  const handlePreConfirm = async () => {
    try {
      const values = await form.validateFields();
      setPendingValues(values);
      setConfirmOpen(true);
    } catch {
      // validation errors shown inline
    }
  };

  const handleConfirm = async () => {
    const values = pendingValues;
    if (!values) return;
    try {
      const res = await mutateAsync({
        mode: values.mode,
        oldAdminHandling: values.oldAdminHandling,
        ...(values.mode === 'new'
          ? {
              username: values.username,
              realName: values.realName,
            }
          : { userId: values.userId }),
      });
      setConfirmOpen(false);
      setResult(res);
    } catch (err) {
      setConfirmOpen(false);
      applyApiErrorToAntdForm(err, form);
    }
  };

  const handleClose = () => {
    form.resetFields();
    setResult(null);
    setConfirmOpen(false);
    setPendingValues(null);
    setMemberKeyword('');
    onClose();
  };

  const copyToClipboard = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      void message.success(`${label}已复制`);
    } catch {
      void message.error('复制失败，请手动复制');
    }
  };

  const mode = Form.useWatch('mode', form) as 'existing' | 'new' | undefined;

  if (result) {
    const resetUrl = tenantWebUrl(tenantCode, `/reset-password/${result.resetToken}`);
    return (
      <Modal
        open={open}
        title="主管理员已更换"
        onCancel={handleClose}
        footer={<></>}
        mask={{ closable: false }}
        destroyOnHidden
        width={520}
      >
        <Alert
          type="success"
          showIcon
          message={`新主管理员「${result.newAdminUsername}」已指定，请将密码设置链接发送给对方`}
          style={{ marginBottom: 16 }}
        />
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
              <Typography.Text style={{ flex: 1, fontFamily: 'var(--font-mono)', fontSize: 12 }}>
                {resetUrl}
              </Typography.Text>
              <CopyOutlined
                style={{ cursor: 'pointer', color: 'var(--brand)' }}
                onClick={() => void copyToClipboard(resetUrl, '链接')}
              />
            </div>
            <div style={{ marginTop: 12 }}>
              <Statistic.Countdown
                title="链接有效期剩余"
                value={new Date(result.resetExpiresAt).getTime()}
                format="D 天 H:mm:ss"
                valueStyle={{ fontSize: 16 }}
              />
            </div>
          </div>
          <div style={{ flexShrink: 0, textAlign: 'center' }}>
            <QRCode value={resetUrl} size={120} />
            <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 4 }}>
              扫码打开
            </div>
          </div>
        </div>
      </Modal>
    );
  }

  return (
    <>
      <Modal
        open={open && !confirmOpen}
        title="重新指定主管理员"
        onCancel={handleClose}
        onOk={() => void handlePreConfirm()}
        confirmLoading={isPending}
        mask={{ closable: false }}
        destroyOnHidden
        okText="下一步"
        width={520}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ mode: 'new', oldAdminHandling: 'demote_admin' }}
          style={{ marginTop: 16 }}
        >
          <Form.Item name="mode" label="指定方式">
            <Radio.Group>
              <Radio value="new">新建账号</Radio>
              <Radio value="existing">从已有成员选</Radio>
            </Radio.Group>
          </Form.Item>

          {mode === 'existing' && (
            <Form.Item
              name="userId"
              label="选择成员"
              rules={[{ required: true, message: '请选择一名成员' }]}
            >
              <Select
                showSearch
                placeholder="搜索姓名 / 账号"
                loading={membersLoading}
                filterOption={false}
                onSearch={setMemberKeyword}
                notFoundContent={membersLoading ? '搜索中…' : '无匹配成员'}
                options={(memberPage?.items ?? [])
                  .filter((m) => !m.mainAdmin)
                  .map((m) => ({
                    value: m.userId,
                    label: `${m.realName ?? m.username}（${m.username}）`,
                  }))}
              />
            </Form.Item>
          )}

          {mode === 'new' && (
            <>
              <Form.Item
                name="username"
                label="账号"
                rules={[
                  { required: true, message: '请输入账号' },
                  { min: 3, message: '至少 3 个字符' },
                  { max: 32, message: '不超过 32 个字符' },
                  {
                    pattern: /^[a-zA-Z0-9_\-.]+$/,
                    message: '只允许字母、数字、下划线、连字符、点',
                  },
                ]}
              >
                <Input placeholder="新主管理员账号" />
              </Form.Item>
              <Form.Item
                name="realName"
                label="姓名"
                rules={[
                  { required: true, message: '请输入姓名' },
                  { max: 64, message: '不超过 64 个字符' },
                ]}
              >
                <Input placeholder="真实姓名" />
              </Form.Item>
            </>
          )}

          <Form.Item
            name="oldAdminHandling"
            label={`原主管理员「${currentAdminUsername}」处理方式`}
            rules={[{ required: true }]}
          >
            <Select>
              <Select.Option value="demote_admin">降级为普通管理员</Select.Option>
              <Select.Option value="demote_member">降级为普通成员</Select.Option>
              <Select.Option value="disable">立即停用账号</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      <DangerConfirm
        open={confirmOpen}
        title={`确认更换主管理员：${tenantName}`}
        lead="主管理员更换后将立即生效，请确认以下影响"
        consequences={[
          `原主管理员「${currentAdminUsername}」将按您选择的方式处理`,
          '新主管理员账号处于未激活状态，需通过密码设置链接首次设密',
          '此操作将记录在审计日志中',
        ]}
        confirmPhrase={tenantCode}
        okText="确认更换"
        confirming={isPending}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => void handleConfirm()}
      />
    </>
  );
}
