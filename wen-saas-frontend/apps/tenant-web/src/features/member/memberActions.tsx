import { useMemo, useState, type ReactNode } from 'react';
import { App } from 'antd';
import { useQuery } from '@tanstack/react-query';
import type { DeptView } from '@wen-saas/domain';
import type { MemberDetail } from '@wen-saas/api-client';
import { describeApiError } from '@wen-saas/api-client';
import { roleApi } from '../role/roleApi';
import { memberApi } from './memberApi';
import {
  useCreateMember,
  useManageMemberDepts,
  useReissueActivation,
  useSetMemberStatus,
  useUnlockMemberLogin,
  useUpdateMember,
} from './memberQueries';
import { MemberFormModal, type MemberFormValues } from './MemberFormModal';
import { ActivationLinkModal, type ActivationInfo } from './ActivationLinkModal';

/** 停用 / 启用确认所需的成员归一化视图（列表项与详情都能映射出）。 */
export type MemberActionTarget = {
  id: string;
  status: number;
  username: string;
  realName: string | null;
  owner: boolean;
  leaderDeptCount: number;
};

/**
 * 成员操作复用 hook：编辑弹窗 / 激活链接 / 停用启用 / 重置链接 / 解除锁定。
 *
 * <p>成员列表（{@link MemberSection}）与成员详情（{@link MemberDetailPane}）共用，避免重复维护
 * 「停用」二次确认（含部门负责人待处置高亮）等带业务语义的交互。
 */
export function useMemberActions(treeData: DeptView[]) {
  const { message, modal } = App.useApp();

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<MemberDetail | null>(null);
  const [defaultDeptId, setDefaultDeptId] = useState('');
  const [activation, setActivation] = useState<ActivationInfo | null>(null);

  const rolesQuery = useQuery({
    queryKey: ['role', 'options'],
    queryFn: () => roleApi.listRoles(),
  });
  const roleOptions = useMemo(
    () => (rolesQuery.data ?? []).map((r) => ({ id: String(r.id), name: r.name ?? '' })),
    [rolesQuery.data],
  );
  const positionsQuery = useQuery({
    queryKey: ['member', 'positions'],
    queryFn: () => memberApi.positions(),
    staleTime: 5 * 60 * 1000,
  });
  const positionOptions = positionsQuery.data ?? [];

  const createMember = useCreateMember();
  const updateMember = useUpdateMember(editing?.id ?? '');
  const manageDepts = useManageMemberDepts(editing?.id ?? '');
  const setStatus = useSetMemberStatus();
  const reissue = useReissueActivation();
  const unlockMutation = useUnlockMemberLogin();

  const openCreate = (deptId: string) => {
    setDefaultDeptId(deptId);
    setEditing(null);
    setFormOpen(true);
  };

  const openEdit = async (id: string) => {
    try {
      const detail = await memberApi.detail(id);
      setEditing(detail);
      setFormOpen(true);
    } catch (err) {
      message.error(describeApiError(err, '加载成员详情失败'));
    }
  };

  const handleSubmit = async (values: MemberFormValues) => {
    if (editing) {
      await updateMember.mutateAsync({
        realName: values.realName,
        ...(values.mobile != null ? { mobile: values.mobile } : {}),
        ...(values.email != null ? { email: values.email } : {}),
        // 岗位非必填：始终下发（空串=清空）
        positionCode: values.positionCode ?? '',
        roleIds: values.roleIds,
      });
      // 主部门留空 = 编辑未归属部门的成员且保持不归属（后端 mainDeptId @NotNull，跳过部门重写）
      if (values.mainDeptId) {
        await manageDepts.mutateAsync({
          mainDeptId: values.mainDeptId,
          secondaryDeptIds: values.secondaryDeptIds,
        });
      }
      message.success('已更新成员');
    } else {
      const created = await createMember.mutateAsync({
        username: values.username,
        realName: values.realName,
        mainDeptId: values.mainDeptId,
        secondaryDeptIds: values.secondaryDeptIds,
        roleIds: values.roleIds,
        ...(values.mobile ? { mobile: values.mobile } : {}),
        ...(values.email ? { email: values.email } : {}),
        ...(values.positionCode ? { positionCode: values.positionCode } : {}),
      });
      setActivation({
        username: created.username,
        activationToken: created.activationToken,
        activationExpiresAt: created.activationExpiresAt,
      });
    }
    setFormOpen(false);
    setEditing(null);
  };

  const doToggle = async (t: MemberActionTarget) => {
    try {
      await setStatus.mutateAsync({ userId: t.id, enabled: t.status !== 1 });
      message.success(t.status === 1 ? '已停用' : '已启用');
    } catch (err) {
      message.error(describeApiError(err, '状态切换失败'));
    }
  };

  // 停用是「状态信号」非连锁清理：弹窗说明影响 + 高亮保留的负责人身份
  const confirmToggle = (t: MemberActionTarget) => {
    const who = t.realName || t.username;
    if (t.status !== 1) {
      modal.confirm({
        title: `确认启用成员「${who}」？`,
        content: '启用后该成员可正常登录，原有角色与部门负责人身份立即恢复，无需重新配置。',
        okText: '确认启用',
        cancelText: '取消',
        onOk: () => doToggle(t),
      });
      return;
    }
    modal.confirm({
      title: `确认停用成员「${who}」？`,
      width: 480,
      okText: '确认停用',
      okButtonProps: { danger: true },
      cancelText: '取消',
      content: (
        <div style={{ fontSize: 13, lineHeight: 1.7 }}>
          <p style={{ marginTop: 0 }}>
            停用后该成员将<b>立即被强制下线</b>、无法登录，不再接收消息与待办通知。
          </p>
          <p style={{ color: 'var(--text-secondary)', marginBottom: 0 }}>
            其角色、所属部门、第三方绑定、创建的数据均<b>保留</b>
            ；恢复启用后立即复权，无需重新配置。
          </p>
          <p style={{ color: 'var(--text-secondary)', marginBottom: 0, marginTop: 8 }}>
            若该成员承担部门负责人、审批等职责，停用后相关职责<b>不会</b>
            自动转移，请另行安排交接处置。
          </p>
          {t.leaderDeptCount > 0 && (
            <div
              style={{
                marginTop: 10,
                padding: '8px 12px',
                background: 'var(--warning-bg)',
                border: '1px solid var(--warning)',
                borderRadius: 6,
              }}
            >
              ⚠️ 该成员仍是 <b>{t.leaderDeptCount}</b> 个部门的负责人。停用<b>不会</b>自动卸任，
              如需可在「设置部门负责人」中另行指派。
            </div>
          )}
        </div>
      ),
      onOk: () => doToggle(t),
    });
  };

  const resetLink = async (id: string) => {
    try {
      const issued = await reissue.mutateAsync(id);
      setActivation({
        username: issued.username,
        activationToken: issued.activationToken,
        activationExpiresAt: issued.activationExpiresAt,
      });
    } catch (err) {
      message.error(describeApiError(err, '生成链接失败'));
    }
  };

  const unlock = async (id: string) => {
    try {
      await unlockMutation.mutateAsync(id);
      message.success('已解除登录锁定');
    } catch (err) {
      message.error(describeApiError(err, '解除锁定失败'));
    }
  };

  const modals: ReactNode = (
    <>
      <MemberFormModal
        open={formOpen}
        editing={editing}
        defaultDeptId={defaultDeptId}
        treeData={treeData}
        roleOptions={roleOptions}
        positionOptions={positionOptions}
        onCancel={() => {
          setFormOpen(false);
          setEditing(null);
        }}
        onSubmit={handleSubmit}
      />
      <ActivationLinkModal
        open={activation != null}
        info={activation}
        onClose={() => setActivation(null)}
      />
    </>
  );

  return { openCreate, openEdit, confirmToggle, resetLink, unlock, roleOptions, modals };
}
