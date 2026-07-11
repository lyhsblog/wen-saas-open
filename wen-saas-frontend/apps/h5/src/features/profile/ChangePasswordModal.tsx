import { useEffect, useState } from 'react';
import { Button, Dialog, Form, Input, NavBar, Popup, Toast } from 'antd-mobile';
import { ApiError } from '@wen-saas/api-client';
import { useChangePassword } from './profileQueries';
import { StepUpGate } from './StepUpGate';

type FormValues = { newPassword: string; confirm: string };

export function ChangePasswordModal({ onClose }: { onClose: () => void }) {
  const [form] = Form.useForm<FormValues>();
  const change = useChangePassword();
  const [stepUpToken, setStepUpToken] = useState<string | null>(null);
  // 挂载后下一帧置 true 触发入场动画；关闭走 visible=false → afterClose 通知父级卸载
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    const raf = requestAnimationFrame(() => setVisible(true));
    return () => cancelAnimationFrame(raf);
  }, []);

  // 已有输入内容时，关闭前确认放弃
  const requestClose = async () => {
    const v = form.getFieldsValue();
    const dirty = !!(v.newPassword || v.confirm);
    if (dirty) {
      const ok = await Dialog.confirm({ content: '放弃编辑？' });
      if (!ok) return;
    }
    setVisible(false);
  };

  const handleFinish = async (v: FormValues) => {
    if (!stepUpToken) return;
    if (v.newPassword !== v.confirm) {
      Toast.show({ content: '两次输入的新密码不一致' });
      return;
    }
    try {
      await change.mutateAsync({ stepUpToken, newPassword: v.newPassword });
      Toast.show({ icon: 'success', content: '密码已修改' });
      setVisible(false);
    } catch (err) {
      setStepUpToken(null); // 票据可能已过期，退回验证步
      const msg = err instanceof ApiError ? (err.problem.detail ?? '修改失败') : '修改失败';
      Toast.show({ content: msg });
    }
  };

  return (
    <Popup
      visible={visible}
      position="right"
      afterClose={onClose}
      bodyStyle={{
        width: '100vw',
        background: 'var(--bg)',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <NavBar onBack={() => void requestClose()} style={{ background: 'var(--bg-elev)' }}>
        修改密码
      </NavBar>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {!stepUpToken ? (
          <StepUpGate title="修改密码前请先验证身份" onVerified={setStepUpToken} />
        ) : (
          <>
            <Form
              form={form}
              layout="vertical"
              onFinish={(v: FormValues) => void handleFinish(v)}
              footer={
                <Button block type="submit" color="primary" size="large" loading={change.isPending}>
                  确认修改
                </Button>
              }
            >
              <Form.Item
                name="newPassword"
                label="新密码"
                rules={[
                  { required: true, message: '请输入新密码' },
                  { min: 8, message: '至少 8 位' },
                ]}
              >
                <Input type="password" placeholder="至少 8 位，含字母与数字" clearable />
              </Form.Item>
              <Form.Item
                name="confirm"
                label="确认新密码"
                rules={[{ required: true, message: '请再次输入新密码' }]}
              >
                <Input type="password" placeholder="再次输入新密码" clearable />
              </Form.Item>
            </Form>
            <div
              style={{
                padding: '0 16px',
                fontSize: 12,
                color: 'var(--text-tertiary)',
                lineHeight: 1.6,
              }}
            >
              修改成功后，其他设备的登录会话将被注销，需要重新登录。
            </div>
          </>
        )}
      </div>
    </Popup>
  );
}
