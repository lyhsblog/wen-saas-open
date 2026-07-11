import { App } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  opsTenantApi,
  describeApiError,
  type AddSeatAddonRequest,
  type AssignPlanRequest,
  type RenewPlanRequest,
  type RevokePlanRequest,
  type TransferMainAdminRequest,
  type ImpersonateRequest,
  type ImpersonateNonceView,
  type TenantSuspendRequest,
  type UpdateTenantRemarkRequest,
  type UpdateTenantNameRequest,
  type TransferMainAdminView,
} from '@wen-saas/api-client';
import { http } from '../../../lib/http';

function useInvalidate(tenantId: string) {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: ['ops', 'tenant', tenantId] });
    void qc.invalidateQueries({ queryKey: ['ops', 'tenants'] });
  };
}

export function useAddSeatAddon(tenantId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate(tenantId);
  return useMutation({
    mutationFn: (body: AddSeatAddonRequest) => opsTenantApi(http).addSeatAddon(tenantId, body),
    onSuccess: () => {
      void message.success('加购席位成功');
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '加购失败'));
    },
  });
}

export function useAssignPlan(tenantId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate(tenantId);
  return useMutation({
    mutationFn: (body: AssignPlanRequest) => opsTenantApi(http).assignPlan(tenantId, body),
    onSuccess: () => {
      void message.success('套餐勾选成功');
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '勾选失败'));
    },
  });
}

export function useRenewPlan(tenantId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate(tenantId);
  return useMutation({
    mutationFn: ({ packageCode, body }: { packageCode: string; body: RenewPlanRequest }) =>
      opsTenantApi(http).renewPlan(tenantId, packageCode, body),
    onSuccess: () => {
      void message.success('续费成功');
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '续费失败'));
    },
  });
}

export function useRevokePlan(tenantId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate(tenantId);
  return useMutation({
    mutationFn: ({ packageCode, body }: { packageCode: string; body: RevokePlanRequest }) =>
      opsTenantApi(http).revokePlan(tenantId, packageCode, body),
    onSuccess: () => {
      void message.success('套餐已取消');
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '取消失败'));
    },
  });
}

export function useTransferMainAdmin(tenantId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate(tenantId);
  return useMutation<TransferMainAdminView, Error, TransferMainAdminRequest>({
    mutationFn: (body) => opsTenantApi(http).transferMainAdmin(tenantId, body),
    onSuccess: () => {
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '指定失败'));
    },
  });
}

export function useImpersonate(tenantId: string) {
  const { message } = App.useApp();
  return useMutation<ImpersonateNonceView, Error, ImpersonateRequest>({
    mutationFn: (body) => opsTenantApi(http).impersonate(tenantId, body),
    onError: (err) => {
      void message.error(describeApiError(err, '代登录失败'));
    },
  });
}

export function useSuspendTenant(tenantId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate(tenantId);
  return useMutation({
    mutationFn: (body?: TenantSuspendRequest) => opsTenantApi(http).suspend(tenantId, body),
    onSuccess: () => {
      void message.success('租户已暂停');
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '操作失败'));
    },
  });
}

export function useResumeTenant(tenantId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate(tenantId);
  return useMutation({
    mutationFn: () => opsTenantApi(http).resume(tenantId),
    onSuccess: () => {
      void message.success('租户已恢复');
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '操作失败'));
    },
  });
}

export function useUpdateRemark(tenantId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate(tenantId);
  return useMutation({
    mutationFn: (body: UpdateTenantRemarkRequest) =>
      opsTenantApi(http).updateRemark(tenantId, body),
    onSuccess: () => {
      void message.success('备注已更新');
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '更新失败'));
    },
  });
}

export function useUpdateName(tenantId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate(tenantId);
  return useMutation({
    mutationFn: (body: UpdateTenantNameRequest) => opsTenantApi(http).updateName(tenantId, body),
    onSuccess: () => {
      void message.success('租户名称已更新');
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '更新失败'));
    },
  });
}

export function useReissueResetLink(tenantId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate(tenantId);
  return useMutation({
    mutationFn: () => opsTenantApi(http).reissueResetLink(tenantId),
    onSuccess: () => {
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '重发失败'));
    },
  });
}

export function useUnlockLoginAttempt(tenantId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate(tenantId);
  return useMutation({
    mutationFn: (userId: string) => opsTenantApi(http).unlockLoginAttempt(tenantId, userId),
    onSuccess: () => {
      void message.success('登录锁定已解除');
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '解锁失败'));
    },
  });
}
