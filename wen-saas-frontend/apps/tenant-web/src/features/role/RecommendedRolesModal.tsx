import { useState } from 'react';
import { Button, Empty, List, Modal, Spin } from 'antd';
import { CountChip } from '@wen-saas/shared-ui';
import { useQuery } from '@tanstack/react-query';
import { RECOMMENDED_DATA_SCOPE_LABEL, type RecommendedDataScope } from '@wen-saas/api-client';
import { recommendedRoleApi } from './permissionCatalogApi';
import { RoleActivatePreviewModal } from './RoleActivatePreviewModal';

// 文案单源自 @wen-saas/api-client（数字版）。
const SCOPE_LABEL = RECOMMENDED_DATA_SCOPE_LABEL;

type Props = {
  open: boolean;
  onClose: () => void;
  onActivated: (roleId: string) => void;
  onViewRole: (roleId: string) => void;
};

/** 推荐角色复制：来自运营已发布且命中本租户套餐的模板，复制后生成独立副本。 */
export function RecommendedRolesModal({ open, onClose, onActivated, onViewRole }: Props) {
  const listQuery = useQuery({
    queryKey: ['recommended-roles'],
    queryFn: () => recommendedRoleApi.listAvailable(),
    enabled: open,
  });

  // 复制前先进入「预览调权」弹窗（R8）：未购模块权限点标灰过滤，可调名称/数据范围/勾权。
  const [previewId, setPreviewId] = useState<string | null>(null);

  const handleActivated = (roleId: string) => {
    setPreviewId(null);
    void listQuery.refetch();
    onActivated(roleId);
  };

  return (
    <>
      <Modal
        open={open}
        title="推荐角色"
        footer={<Button onClick={onClose}>关闭</Button>}
        onCancel={onClose}
        destroyOnHidden
        width={560}
      >
        {listQuery.isPending ? (
          <div style={{ padding: 32, textAlign: 'center' }}>
            <Spin />
          </div>
        ) : (listQuery.data ?? []).length === 0 ? (
          <Empty description="暂无可用推荐角色（来自运营后台已发布且匹配本租户套餐的模板）" />
        ) : (
          <List
            dataSource={listQuery.data ?? []}
            renderItem={(item) => (
              <List.Item
                actions={[
                  // 不拦截重复复制（X3-02）：已复制过仍可再次复制为新的独立副本，同时提供查看入口。
                  ...(item.activated && item.activatedRoleId
                    ? [
                        <Button
                          key="view"
                          type="link"
                          size="small"
                          onClick={() => onViewRole(item.activatedRoleId!)}
                        >
                          已复制 →
                        </Button>,
                      ]
                    : []),
                  <Button
                    key="copy"
                    type={item.activated ? 'default' : 'primary'}
                    size="small"
                    onClick={() => setPreviewId(item.id)}
                  >
                    {item.activated ? '再次复制' : '复制'}
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={
                    <span>
                      {item.name}
                      <span style={{ marginLeft: 8, display: 'inline-flex', gap: 4 }}>
                        <CountChip>
                          {SCOPE_LABEL[item.dataScope as RecommendedDataScope] ?? '—'}
                        </CountChip>
                        <CountChip>{item.permissionCount} 权限</CountChip>
                      </span>
                    </span>
                  }
                  description={item.description || '—'}
                />
              </List.Item>
            )}
          />
        )}
      </Modal>
      {previewId != null && (
        <RoleActivatePreviewModal
          key={previewId}
          open
          templateId={previewId}
          onCancel={() => setPreviewId(null)}
          onActivated={handleActivated}
        />
      )}
    </>
  );
}
