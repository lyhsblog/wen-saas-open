import { useEffect } from 'react';
import { Alert, Button, Checkbox, Empty, Form, Input, Modal, Radio, Space, Spin } from 'antd';
import { CountChip } from '@wen-saas/shared-ui';
import { useQuery } from '@tanstack/react-query';
import { opsRecommendedRoleAdminApi } from '@wen-saas/api-client';
import type { CatalogTreeNode, RecRoleDataScope, RecRoleDetail } from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { applyApiErrorToAntdForm } from '../tenants/utils/form';
import { useCreateRecRole, useUpdateRecRole } from './hooks/useRecRoleMutations';
import { DATA_SCOPE_OPTIONS } from './utils/recRoleMeta';
import { PermissionCatalogTree } from '../permission/PermissionCatalogTree';

type Props = {
  open: boolean;
  /** 传入 detail 为编辑模式；不传为新建模式。 */
  editing: RecRoleDetail | null;
  onClose: () => void;
  /** 新建成功后回调（用于选中新模板）。 */
  onCreated?: (id: string) => void;
};

type FormData = {
  name: string;
  description: string;
  dataScope: RecRoleDataScope;
  packageCodes: string[];
  permissionCodes: string[];
};

export function OpsRecRoleFormModal({ open, editing, onClose, onCreated }: Props) {
  const [form] = Form.useForm<FormData>();
  const isEdit = editing != null;
  // 已发布态：禁止移除已含套餐包 / 权限点（服务端兜底，前端禁用取消勾选）。
  const lockRemoval = editing != null && editing.status === 'PUBLISHED';
  const lockedPackages = lockRemoval ? new Set(editing.packageCodes) : new Set<string>();
  const lockedPermissions = lockRemoval ? new Set(editing.permissionCodes) : new Set<string>();

  const { data: packages, isLoading: pkgsLoading } = useQuery({
    queryKey: ['ops', 'rec-role-packages'],
    queryFn: () => opsRecommendedRoleAdminApi(http).listAvailablePackages(),
    enabled: open,
  });
  const { data: catalog, isLoading: treeLoading } = useQuery({
    queryKey: ['ops', 'rec-role-permission-catalog'],
    queryFn: () => opsRecommendedRoleAdminApi(http).listPermissionCatalog(),
    enabled: open,
  });
  const tree = catalog?.tree ?? [];

  const create = useCreateRecRole();
  const update = useUpdateRecRole(editing?.id ?? '');
  const isPending = create.isPending || update.isPending;

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue({
        name: editing.name,
        description: editing.description ?? '',
        dataScope: editing.dataScope,
        packageCodes: editing.packageCodes,
        permissionCodes: editing.permissionCodes,
      });
    } else {
      form.setFieldsValue({
        name: '',
        description: '',
        dataScope: 1,
        packageCodes: [],
        permissionCodes: [],
      });
    }
  }, [open, editing, form]);

  const submit = async (publishImmediately: boolean) => {
    const values = await form.validateFields();
    try {
      if (isEdit) {
        await update.mutateAsync({
          name: values.name,
          description: values.description ? values.description : null,
          dataScope: values.dataScope,
          packageCodes: values.packageCodes,
          permissionCodes: values.permissionCodes,
        });
      } else {
        const detail = await create.mutateAsync({
          name: values.name,
          description: values.description ? values.description : null,
          dataScope: values.dataScope,
          packageCodes: values.packageCodes,
          permissionCodes: values.permissionCodes,
          publishImmediately,
        });
        onCreated?.(detail.id);
      }
      form.resetFields();
      onClose();
    } catch (err) {
      applyApiErrorToAntdForm(err, form);
      // 非字段级错误已由 mutation onError toast
    }
  };

  const footer = isEdit
    ? [
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button key="save" type="primary" loading={isPending} onClick={() => void submit(false)}>
          保存
        </Button>,
      ]
    : [
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button key="draft" loading={isPending} onClick={() => void submit(false)}>
          保存为草稿
        </Button>,
        <Button key="publish" type="primary" loading={isPending} onClick={() => void submit(true)}>
          保存并发布
        </Button>,
      ];

  return (
    <Modal
      open={open}
      title={isEdit ? `编辑推荐角色 · ${editing.name}` : '新建推荐角色模板'}
      onCancel={onClose}
      footer={footer}
      mask={{ closable: false }}
      destroyOnHidden
      width={720}
    >
      {lockRemoval && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="已发布编辑限制：可改名 / 改描述 / 改数据范围 / 加套餐包 / 加权限点，但禁止移除已含套餐包与权限点。如需减权限，请新建模板替代旧模板。"
        />
      )}
      <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
        <Form.Item
          name="name"
          label="模板名称"
          extra="运营内部唯一"
          rules={[{ required: true, message: '请输入模板名称' }, { max: 128 }]}
        >
          <Input placeholder="例：客户经理" disabled={isPending} />
        </Form.Item>
        <Form.Item
          name="description"
          label="描述"
          extra="给租户看的角色描述"
          rules={[{ max: 1024 }]}
        >
          <Input.TextArea
            rows={2}
            maxLength={1024}
            showCount
            placeholder="例：客户全只读 + 工单受理（选填）"
            disabled={isPending}
          />
        </Form.Item>
        <Form.Item
          name="dataScope"
          label="默认数据范围"
          extra="仅影响后续复制，已复制角色按快照不受影响"
          rules={[{ required: true, message: '请选择默认数据范围' }]}
        >
          <Radio.Group disabled={isPending} options={DATA_SCOPE_OPTIONS} />
        </Form.Item>
        <Form.Item
          name="packageCodes"
          label="关联套餐包"
          extra="多选 · 至少 1 个 · 关联基础包 = 所有租户可见"
          rules={[{ required: true, message: '请至少关联一个套餐包' }]}
        >
          {pkgsLoading ? (
            <div style={{ padding: 24, textAlign: 'center' }}>
              <Spin />
            </div>
          ) : (packages ?? []).length === 0 ? (
            <Empty
              description="暂无可关联的套餐包，请先在「套餐包管理」创建并发布"
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              style={{ padding: '16px 0' }}
            />
          ) : (
            <Checkbox.Group disabled={isPending} style={{ width: '100%' }}>
              <Space direction="vertical" size={6} style={{ width: '100%' }}>
                {(packages ?? []).map((p) => (
                  <Checkbox
                    key={p.code}
                    value={p.code}
                    disabled={lockedPackages.has(p.code) || isPending}
                  >
                    {p.isSystem && (
                      <span style={{ marginRight: 4 }}>
                        <CountChip tone="brand">基础</CountChip>
                      </span>
                    )}
                    <span style={{ fontWeight: 500 }}>{p.name}</span>
                    <span style={{ color: 'var(--text-tertiary)', fontSize: 12, marginLeft: 6 }}>
                      {p.code}
                    </span>
                  </Checkbox>
                ))}
              </Space>
            </Checkbox.Group>
          )}
        </Form.Item>
        <Form.Item
          name="permissionCodes"
          label="包含的权限点"
          extra="跨业务模块勾选 · 可包含尚未在任何套餐包中的功能"
        >
          {treeLoading ? (
            <div style={{ padding: 24, textAlign: 'center' }}>
              <Spin />
            </div>
          ) : tree.length === 0 ? (
            <Alert
              type="info"
              showIcon
              message="当前尚无可选业务权限点。业务模块接入并注册权限点后，此处会自动出现可勾选的权限树。"
            />
          ) : (
            <PermissionCodesField tree={tree} lockedCodes={lockedPermissions} />
          )}
        </Form.Item>
      </Form>
    </Modal>
  );
}

/**
 * antd Form 字段适配器：把 catalogTree 三态树桥接成受控的 `permissionCodes: string[]`。
 * 已锁定码（已发布模板已含）禁止取消：分组「取消全选」时过滤掉 locked，不从已选集移除。
 */
function PermissionCodesField({
  tree,
  lockedCodes,
  value,
  onChange,
}: {
  tree: CatalogTreeNode[];
  lockedCodes: Set<string>;
  value?: string[];
  onChange?: (codes: string[]) => void;
}) {
  const checked = new Set(value ?? []);
  const setCodes = (codes: string[], on: boolean) => {
    const next = new Set(checked);
    if (on) codes.forEach((c) => next.add(c));
    else codes.forEach((c) => !lockedCodes.has(c) && next.delete(c));
    onChange?.([...next]);
  };
  return (
    <PermissionCatalogTree
      tree={tree}
      checked={checked}
      onSet={setCodes}
      lockedCodes={lockedCodes}
    />
  );
}
