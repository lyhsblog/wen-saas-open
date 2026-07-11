import { App, Skeleton, Switch, Typography } from 'antd';
import { SafetyCertificateOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, describeApiError } from '@wen-saas/api-client';
import { loadCapabilities, useHasPermission } from '@wen-saas/permissions';
import { ServerError5xxPage, StatusPill, toneBadge } from '@wen-saas/shared-ui';
import { watermarkApi } from './securityApi';
import { http } from '../../lib/http';
import { WaCard, WaPage, WaPageHead } from '../../components/wa';

const securityKeys = { watermark: ['security', 'watermark'] as const };

/** 租户后台 · 系统管理 · 安全合规（防截屏水印开关）。 */
export function SecurityPage() {
  const { message } = App.useApp();
  const qc = useQueryClient();
  // 未在权限目录预置 → 主管理员恒可、其余角色默认不可改；与后端 @RequirePermission 同口径。
  const canEdit = useHasPermission('tenant.security.watermark.edit');

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: securityKeys.watermark,
    queryFn: () => watermarkApi.get(),
  });

  const update = useMutation({
    mutationFn: (enabled: boolean) => watermarkApi.update(enabled),
    onSuccess: async (res) => {
      message.success(res.enabled ? '已开启防截屏水印' : '已关闭防截屏水印');
      qc.setQueryData(securityKeys.watermark, res);
      // 重新拉能力包，让全页水印即时反映开关变化（screenshotWatermark 经 capabilities 下发）。
      await loadCapabilities(http).catch(() => undefined);
    },
    onError: (e) => message.error(describeApiError(e, '保存失败')),
  });

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
      <WaCard title="防截屏水印">
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16, padding: '4px 0' }}>
          <div
            style={{
              width: 52,
              height: 52,
              borderRadius: 'var(--r-md)',
              background: 'var(--brand-bg)',
              color: 'var(--brand)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 26,
              flexShrink: 0,
            }}
          >
            <SafetyCertificateOutlined />
          </div>
          <div style={{ flex: 1 }}>
            <div
              style={{
                fontSize: 16,
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                gap: 10,
              }}
            >
              全页可溯源水印
              <Switch
                checked={data.enabled}
                loading={update.isPending}
                disabled={!canEdit}
                onChange={(v) => update.mutate(v)}
              />
            </div>
            <div
              style={{
                fontSize: 13,
                color: 'var(--text-tertiary)',
                marginTop: 6,
                lineHeight: 1.75,
              }}
            >
              开启后，租户端与运营端全页平铺淡色水印（<b>真实姓名 + 用户名 + 实时时间</b>
              ），用于截屏 /
              拍照外泄时可溯源到具体责任人。水印为浅色底纹，不影响日常阅读；不展示手机 /
              邮箱等联系方式（已加密脱敏）。
            </div>
            {!canEdit && (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                水印为租户级安全策略，仅主管理员可修改。
              </Typography.Text>
            )}
          </div>
        </div>
      </WaCard>
    );
  }

  return (
    <WaPage>
      <WaPageHead
        title="安全合规"
        hint="系统管理 / 安全合规"
        badges={<StatusPill badge={toneBadge('warning', '仅主管理员可改')} />}
      />
      {body}
    </WaPage>
  );
}
