import { useState } from 'react';
import { App, Modal, Select, Typography } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { describeApiError } from '@wen-saas/api-client';
import { deptApi } from '../dept/deptApi';
import { deptKeys } from '../dept/deptQueries';
import { memberApi } from './memberApi';

const { Text } = Typography;

type Props = {
  open: boolean;
  deptId: string;
  deptName: string;
  onClose: () => void;
};

/** 设置部门负责人（0~N 人，须为本部门成员）。 */
export function DeptLeadersModal({ open, deptId, deptName, onClose }: Props) {
  const { message } = App.useApp();
  const qc = useQueryClient();
  const [override, setOverride] = useState<string[] | null>(null);
  const [saving, setSaving] = useState(false);

  const query = useQuery({
    queryKey: ['dept-leaders', deptId],
    enabled: open,
    queryFn: async () => {
      const [members, leaders] = await Promise.all([
        memberApi.list({ deptId, includeSubDepts: false, pageSize: 200 }),
        deptApi.listLeaders(deptId),
      ]);
      return {
        options: members.items.map((m) => ({
          value: m.id,
          label: `${m.realName ?? m.username}（${m.username}）`,
        })),
        leaderIds: leaders.map((l) => l.userId),
      };
    },
  });

  const selected = override ?? query.data?.leaderIds ?? [];

  const close = () => {
    setOverride(null);
    onClose();
  };

  const save = async () => {
    setSaving(true);
    try {
      await deptApi.setLeaders(deptId, selected);
      // 部门树「负责人待处置」标签 + 详情随负责人增减/清空刷新（T13-01）
      void qc.invalidateQueries({ queryKey: deptKeys.tree() });
      void qc.invalidateQueries({ queryKey: deptKeys.detail(deptId) });
      void qc.invalidateQueries({ queryKey: ['dept-leaders', deptId] });
      message.success('已更新部门负责人');
      close();
    } catch (err) {
      message.error(describeApiError(err, '保存失败'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={`设置部门负责人 — ${deptName}`}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      onOk={() => void save()}
      onCancel={close}
      destroyOnHidden
      width={480}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          负责人须为本部门成员（0~N
          人）。负责人无自动数据权限，如需「看下属数据」请建「部门负责人」角色并设「本部门+下属」数据范围。
        </Text>
        <Select
          mode="multiple"
          loading={query.isFetching}
          value={selected}
          onChange={setOverride}
          options={query.data?.options ?? []}
          placeholder="从本部门成员中选择"
          style={{ width: '100%' }}
          maxTagCount="responsive"
          notFoundContent={query.isFetching ? '加载中…' : '本部门暂无成员'}
        />
      </div>
    </Modal>
  );
}
