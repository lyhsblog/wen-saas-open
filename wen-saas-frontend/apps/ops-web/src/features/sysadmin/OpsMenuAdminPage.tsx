import { useMemo, useState } from 'react';
import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Segmented,
  Select,
  Space,
  Switch,
  Table,
  Tooltip,
} from 'antd';
import { CountChip, StatusPill, toneBadge } from '@wen-saas/shared-ui';
import { MenuOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  opsMenuApi,
  type MenuAdminNode,
  type MenuSaveRequest,
  type PermissionCodeView,
} from '@wen-saas/api-client';
import { describeApiError } from '@wen-saas/api-client';
import type { ColumnsType } from 'antd/es/table';
import { http } from '../../lib/http';

const api = opsMenuApi(http);

const NODE_GROUP = 1;
const NODE_PAGE = 2;
const NODE_ACTION = 3;

const TYPE_LABEL: Record<number, string> = { 1: '分组', 2: '页面', 3: '动作' };

type Scope = 1 | 2;
const SCOPE_OPTIONS = [
  { label: '运营端', value: 1 },
  { label: '租户端', value: 2 },
];

type EditorState =
  | { mode: 'create'; parentKey: string | null; allowedTypes: number[] }
  | { mode: 'edit'; node: MenuAdminNode };

/** 去掉空 children，antd Table 才不会给叶子渲染展开图标。 */
function normalize(nodes: MenuAdminNode[]): MenuAdminNode[] {
  return nodes.map((n) => {
    const kids = n.children && n.children.length > 0 ? normalize(n.children) : undefined;
    return { ...n, children: kids } as MenuAdminNode;
  });
}

/**
 * 运营后台「菜单管理」页。运营自助维护 sys_menu 树：增改 / 启停 / 删除 / 绑权限码。
 * 无 UI 原型（新页），按 ops 既有管理页风格 + tokens 实现。
 */
export function OpsMenuAdminPage() {
  const { message, modal } = App.useApp();
  const qc = useQueryClient();
  const [scope, setScope] = useState<Scope>(1);
  const [editor, setEditor] = useState<EditorState | null>(null);

  const treeQuery = useQuery({
    queryKey: ['ops', 'menus', scope],
    queryFn: () => api.tree(scope),
  });
  const codesQuery = useQuery({
    queryKey: ['ops', 'permission-codes', scope],
    queryFn: () => api.permissionCodes(scope),
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['ops', 'menus', scope] });

  const statusMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => api.setStatus(id, enabled),
    onSuccess: () => {
      void invalidate();
      message.success('已更新状态');
    },
    onError: (e) => message.error(describeApiError(e, '操作失败')),
  });
  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.remove(id),
    onSuccess: () => {
      void invalidate();
      message.success('已删除');
    },
    onError: (e) => message.error(describeApiError(e, '删除失败')),
  });

  const data = useMemo(() => normalize(treeQuery.data ?? []), [treeQuery.data]);

  const askDelete = (node: MenuAdminNode) => {
    modal.confirm({
      title: `删除「${node.label}」？`,
      content: '仅自建节点可删，且必须无子节点。删除后不可恢复。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => deleteMutation.mutateAsync(node.id),
    });
  };

  const columns: ColumnsType<MenuAdminNode> = [
    {
      title: '名称',
      dataIndex: 'label',
      key: 'label',
      render: (label: string, node) => (
        <Space size={6}>
          <CountChip>{TYPE_LABEL[node.nodeType]}</CountChip>
          <span style={{ fontWeight: node.nodeType === NODE_GROUP ? 600 : 400 }}>{label}</span>
          {node.dangling && (
            <Tooltip title="绑定的权限码不在后端目录中（悬空失效），请修绑">
              <span>
                <StatusPill badge={toneBadge('danger', '失效')} />
              </span>
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: '路由',
      dataIndex: 'path',
      key: 'path',
      render: (path: string | null) =>
        path ? <code style={{ fontSize: 12 }}>{path}</code> : <Muted />,
    },
    {
      title: '权限码',
      dataIndex: 'permissionCode',
      key: 'permissionCode',
      render: (code: string | null, node) =>
        code ? (
          <code
            style={{
              fontSize: 12,
              color: node.dangling ? 'var(--danger)' : 'var(--text-secondary)',
            }}
          >
            {code}
          </code>
        ) : (
          <Muted />
        ),
    },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      width: 90,
      render: (source: number) =>
        source === 1 ? <CountChip tone="brand">种子</CountChip> : <CountChip>自建</CountChip>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (status: number, node) => (
        <Switch
          size="small"
          checked={status === 1}
          loading={statusMutation.isPending && statusMutation.variables?.id === node.id}
          onChange={(checked) => statusMutation.mutate({ id: node.id, enabled: checked })}
        />
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      render: (_, node) => (
        <Space size={4}>
          {node.nodeType !== NODE_ACTION && (
            <Button
              type="link"
              size="small"
              onClick={() =>
                setEditor({
                  mode: 'create',
                  parentKey: node.nodeKey,
                  allowedTypes: node.nodeType === NODE_GROUP ? [NODE_PAGE] : [NODE_ACTION],
                })
              }
            >
              加子节点
            </Button>
          )}
          <Button type="link" size="small" onClick={() => setEditor({ mode: 'edit', node })}>
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            danger
            disabled={node.source === 1}
            onClick={() => askDelete(node)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, display: 'flex', flexDirection: 'column', height: '100%', gap: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <MenuOutlined style={{ color: 'var(--brand)', fontSize: 18 }} />
        <span style={{ fontSize: 18, fontWeight: 600 }}>菜单管理</span>
        <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
          维护导航菜单树：分组 / 页面 / 动作，绑路由与权限码（加菜单不必后端发版）
        </span>
        <div style={{ flex: 1 }} />
        <Segmented options={SCOPE_OPTIONS} value={scope} onChange={(v) => setScope(v as Scope)} />
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() =>
            setEditor({ mode: 'create', parentKey: null, allowedTypes: [NODE_GROUP, NODE_PAGE] })
          }
        >
          新建顶层节点
        </Button>
      </div>

      <Table
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={data}
        loading={treeQuery.isPending}
        pagination={false}
        expandable={{ defaultExpandAllRows: true }}
        scroll={{ y: 'calc(100vh - 230px)' }}
      />

      {editor && (
        <MenuEditorModal
          editor={editor}
          scope={scope}
          codes={codesQuery.data ?? []}
          onClose={() => setEditor(null)}
          onSaved={() => {
            setEditor(null);
            void invalidate();
          }}
        />
      )}
    </div>
  );
}

function Muted() {
  return <span style={{ color: 'var(--text-tertiary)' }}>—</span>;
}

type FormValues = {
  nodeType: number;
  label: string;
  icon?: string | undefined;
  path?: string | undefined;
  permissionCode?: string | undefined;
  description?: string | undefined;
  sort?: number | undefined;
};

function MenuEditorModal({
  editor,
  scope,
  codes,
  onClose,
  onSaved,
}: {
  editor: EditorState;
  scope: Scope;
  codes: PermissionCodeView[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const isEdit = editor.mode === 'edit';
  const initialType = isEdit ? editor.node.nodeType : editor.allowedTypes[0]!;

  const initial: FormValues = isEdit
    ? {
        nodeType: editor.node.nodeType,
        label: editor.node.label,
        icon: editor.node.icon ?? undefined,
        path: editor.node.path ?? undefined,
        permissionCode: editor.node.permissionCode ?? undefined,
        description: editor.node.description ?? undefined,
        sort: editor.node.sort,
      }
    : { nodeType: initialType, label: '', sort: 0 };

  const nodeType = Form.useWatch('nodeType', form) ?? initialType;
  const isGroup = nodeType === NODE_GROUP;
  const isAction = nodeType === NODE_ACTION;

  const saveMutation = useMutation({
    mutationFn: async (v: FormValues) => {
      if (isEdit) {
        return api.update(editor.node.id, {
          label: v.label,
          icon: v.icon ?? null,
          path: isGroup ? null : (v.path ?? null),
          permissionCode: isGroup ? null : (v.permissionCode ?? null),
          description: v.description ?? null,
          sort: v.sort ?? null,
        });
      }
      const body: MenuSaveRequest = {
        scope,
        parentKey: editor.parentKey,
        nodeType: v.nodeType,
        label: v.label,
        icon: v.icon ?? null,
        path: v.nodeType === NODE_GROUP ? null : (v.path ?? null),
        permissionCode: v.nodeType === NODE_GROUP ? null : (v.permissionCode ?? null),
        description: v.description ?? null,
        sort: v.sort ?? null,
      };
      return api.create(body);
    },
    onSuccess: () => {
      message.success(isEdit ? '已保存' : '已新建');
      onSaved();
    },
    onError: (e) => message.error(describeApiError(e, '保存失败')),
  });

  const codeOptions = codes.map((c) => ({
    value: c.code,
    label: (
      <span>
        {c.name}
        <span style={{ color: 'var(--text-tertiary)', fontSize: 12, marginLeft: 6 }}>{c.code}</span>
        {c.dataScoped && (
          <span style={{ marginLeft: 6 }}>
            <CountChip tone="brand">数据权限</CountChip>
          </span>
        )}
      </span>
    ),
  }));

  return (
    <Modal
      open
      title={isEdit ? `编辑节点 · ${editor.node.label}` : '新建节点'}
      okText="保存"
      confirmLoading={saveMutation.isPending}
      onCancel={onClose}
      onOk={() => {
        form
          .validateFields()
          .then((v) => saveMutation.mutateAsync(v))
          .catch(() => undefined);
      }}
      destroyOnHidden
      width={560}
    >
      <Form form={form} layout="vertical" initialValues={initial} style={{ marginTop: 12 }}>
        <Form.Item label="节点类型" name="nodeType" rules={[{ required: true }]}>
          <Segmented
            disabled={isEdit}
            options={(isEdit ? [NODE_GROUP, NODE_PAGE, NODE_ACTION] : editor.allowedTypes).map(
              (t) => ({ label: TYPE_LABEL[t], value: t }),
            )}
          />
        </Form.Item>
        <Form.Item label="名称" name="label" rules={[{ required: true, message: '请输入名称' }]}>
          <Input placeholder="中文显示名" maxLength={64} />
        </Form.Item>
        {!isAction && (
          <Form.Item label="图标名" name="icon" tooltip="antd 风格短名，如 setting / team / book">
            <Input placeholder="可空（分组 / 页面侧栏图标）" maxLength={64} />
          </Form.Item>
        )}
        {!isGroup && !isAction && (
          <Form.Item
            label="路由"
            name="path"
            rules={[{ required: true, message: '页面节点必须有路由' }]}
          >
            <Input placeholder="如 /sys/members" maxLength={256} />
          </Form.Item>
        )}
        {!isGroup && (
          <Form.Item
            label="权限码"
            name="permissionCode"
            rules={isAction ? [{ required: true, message: '动作节点必须绑定权限码' }] : []}
            tooltip="勾它=授予该码；从后端权限码目录选，不在目录的码会被拒绝"
          >
            <Select
              showSearch
              allowClear={!isAction}
              placeholder="选择权限码"
              optionFilterProp="value"
              options={codeOptions}
            />
          </Form.Item>
        )}
        <Form.Item label="说明" name="description">
          <Input.TextArea rows={2} placeholder="可空（动作节点角色页 tooltip）" maxLength={256} />
        </Form.Item>
        <Form.Item label="同级排序" name="sort">
          <InputNumber min={0} style={{ width: 160 }} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
